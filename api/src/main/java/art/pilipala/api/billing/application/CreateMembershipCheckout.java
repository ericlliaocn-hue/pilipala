package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import art.pilipala.api.billing.domain.MembershipPlan;
import art.pilipala.api.billing.domain.MembershipPlanPrice;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderKind;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.infra.ClientIpResolver;
import art.pilipala.api.billing.infra.MembershipPlanMapper;
import art.pilipala.api.billing.infra.MembershipPlanPriceMapper;
import art.pilipala.api.billing.infra.OutTradeNoGenerator;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.billing.infra.RechargeProperties;
import art.pilipala.api.billing.infra.UserSubscriptionMapper;
import art.pilipala.api.billing.infra.ZpayClient;
import art.pilipala.api.billing.infra.ZpayClient.MapiRequest;
import art.pilipala.api.billing.infra.ZpayClient.MapiResult;
import art.pilipala.api.billing.infra.ZpayProperties;
import art.pilipala.api.billing.infra.ZpayUrlNormalizer;
import art.pilipala.api.job.infra.JobException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateMembershipCheckout {

    private final RechargeConfigValidator config;
    private final MembershipPlanPriceMapper prices;
    private final MembershipPlanMapper plans;
    private final UserSubscriptionMapper subscriptions;
    private final RechargeOrderRepository orders;
    private final RechargeProperties rechargeProperties;
    private final ZpayClient zpay;
    private final ZpayProperties zpayProperties;

    public CreateMembershipCheckout(
            RechargeConfigValidator config,
            MembershipPlanPriceMapper prices,
            MembershipPlanMapper plans,
            UserSubscriptionMapper subscriptions,
            RechargeOrderRepository orders,
            RechargeProperties rechargeProperties,
            ZpayClient zpay,
            ZpayProperties zpayProperties) {
        this.config = config;
        this.prices = prices;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.orders = orders;
        this.rechargeProperties = rechargeProperties;
        this.zpay = zpay;
        this.zpayProperties = zpayProperties;
    }

    @Transactional
    public RechargeOrderView checkout(UUID userId, UUID tenantId, long priceId, String device,
            HttpServletRequest request) {
        config.requireCreateOrderConfig();
        if (subscriptions.findActiveByTenant(tenantId) != null) {
            throw JobException.validation();
        }
        MembershipPlanPrice price = prices.findById(priceId);
        if (price == null || !price.enabled()) {
            throw JobException.validation();
        }
        MembershipPlan plan = plans.findById(price.planId());
        if (plan == null || !plan.enabled() || !"personal".equals(plan.audience())) {
            throw JobException.validation();
        }
        boolean mobile = "mobile".equalsIgnoreCase(device);
        String clientIp = ClientIpResolver.resolve(request);
        String outTradeNo = OutTradeNoGenerator.generate();
        String displayName = plan.nameZh() + " · " + cycleLabel(price.cycle());
        RechargeOrder created = orders.insertCreated(new RechargeOrder(
                null,
                tenantId,
                userId,
                outTradeNo,
                "zpay",
                null,
                "membership:" + priceId,
                price.priceFen(),
                price.creditsPerMonth(),
                RechargeOrderStatus.CREATED,
                clientIp,
                mobile ? "mobile" : null,
                null,
                null,
                displayName,
                null,
                plan.code(),
                0L,
                null,
                null,
                null,
                null,
                RechargeOrderKind.MEMBERSHIP,
                priceId,
                null,
                null));
        try {
            MapiResult result = zpay.createAlipayOrder(new MapiRequest(
                    outTradeNo,
                    config.notifyUrl(),
                    config.returnUrl(),
                    displayName,
                    price.priceFen(),
                    clientIp,
                    mobile));
            String apiBase = zpayProperties.normalizedApiBase();
            String payUrl = ZpayUrlNormalizer.normalize(
                    mobile && result.payurl2() != null ? result.payurl2() : result.payurl(), apiBase);
            String qrcodeUrl = ZpayUrlNormalizer.normalize(result.qrcode(), apiBase);
            if (qrcodeUrl == null && payUrl != null) {
                qrcodeUrl = payUrl;
            }
            orders.markPendingPay(created.id(), result.tradeNo(), payUrl, qrcodeUrl);
            RechargeOrder pending = orders.findByOutTradeNo(outTradeNo).orElseThrow();
            Instant expireAt = pending.createdAt().plus(rechargeProperties.orderTtlMinutes(), ChronoUnit.MINUTES);
            return RechargeOrderView.of(pending, expireAt);
        } catch (JobException e) {
            orders.markFailed(created.id(), e.errorCode());
            throw e;
        } catch (RuntimeException e) {
            orders.markFailed(created.id(), "provider_error");
            throw JobException.rechargeProviderError();
        }
    }

    private static String cycleLabel(String cycle) {
        return switch (cycle) {
            case "year" -> "连续包年";
            case "quarter" -> "连续包季";
            default -> "连续包月";
        };
    }
}
