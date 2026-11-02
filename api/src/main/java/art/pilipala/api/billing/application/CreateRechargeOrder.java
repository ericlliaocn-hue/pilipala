package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderKind;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.infra.ClientIpResolver;
import art.pilipala.api.billing.infra.OutTradeNoGenerator;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.billing.infra.RechargePackageProperties;
import art.pilipala.api.billing.infra.RechargeProperties;
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
public class CreateRechargeOrder {

    private final RechargeConfigValidator config;
    private final RechargeProductCatalog catalog;
    private final RechargeProperties rechargeProperties;
    private final RechargeOrderRepository orders;
    private final ZpayClient zpay;
    private final ZpayProperties zpayProperties;

    public CreateRechargeOrder(
            RechargeConfigValidator config,
            RechargeProductCatalog catalog,
            RechargeProperties rechargeProperties,
            RechargeOrderRepository orders,
            ZpayClient zpay,
            ZpayProperties zpayProperties) {
        this.config = config;
        this.catalog = catalog;
        this.rechargeProperties = rechargeProperties;
        this.orders = orders;
        this.zpay = zpay;
        this.zpayProperties = zpayProperties;
    }

    @Transactional
    public RechargeOrderView create(
            UUID userId,
            UUID tenantId,
            String packageId,
            String productCode,
            Long customCredits,
            String device,
            HttpServletRequest request) {
        config.requireCreateOrderConfig();
        ResolvedProduct resolved = resolveProduct(packageId, productCode, customCredits);
        boolean mobile = "mobile".equalsIgnoreCase(device);
        String clientIp = ClientIpResolver.resolve(request);
        String outTradeNo = OutTradeNoGenerator.generate();
        String packageKey = resolved.productCode() != null ? resolved.productCode() : resolved.legacyPackageId();
        RechargeOrder created = orders.insertCreated(new RechargeOrder(
                null,
                tenantId,
                userId,
                outTradeNo,
                "zpay",
                null,
                packageKey,
                resolved.moneyFen(),
                resolved.credits(),
                RechargeOrderStatus.CREATED,
                clientIp,
                mobile ? "mobile" : null,
                null,
                null,
                resolved.displayName(),
                resolved.productId(),
                resolved.productCode(),
                resolved.fixedBonusCredits(),
                null,
                null,
                null,
                null,
                RechargeOrderKind.RECHARGE,
                null,
                null,
                null));
        try {
            MapiResult result = zpay.createAlipayOrder(new MapiRequest(
                    outTradeNo,
                    config.notifyUrl(),
                    config.returnUrl(),
                    resolved.displayName(),
                    resolved.moneyFen(),
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

    private ResolvedProduct resolveProduct(String packageId, String productCode, Long customCredits) {
        if (catalog.isDbPrimary()) {
            String code = productCode != null && !productCode.isBlank() ? productCode : packageId;
            CreditProduct product = catalog.requireEnabled(code);
            if ("custom".equals(product.tab())) {
                if (customCredits == null) {
                    throw JobException.validation();
                }
                CreditProductMeta.CustomSlider slider = CreditProductMeta.parseCustomSlider(product.metaJson());
                if (customCredits < slider.minCredits() || customCredits > slider.maxCredits()) {
                    throw JobException.validation();
                }
                int moneyFen = CreditProductMeta.priceFenForCredits(customCredits, slider.creditsPerYuan());
                return new ResolvedProduct(
                        product.id(), product.code(), null, product.nameZh(), moneyFen, customCredits,
                        product.bonusCredits());
            }
            return new ResolvedProduct(
                    product.id(),
                    product.code(),
                    null,
                    product.nameZh(),
                    product.priceFen(),
                    product.credits(),
                    product.bonusCredits());
        }
        RechargePackageProperties pkg = catalog.requireLegacyPackage(packageId);
        return new ResolvedProduct(null, null, pkg.id(), pkg.name(), pkg.moneyFen(), pkg.credits(), 0);
    }

    private record ResolvedProduct(
            Long productId,
            String productCode,
            String legacyPackageId,
            String displayName,
            long moneyFen,
            long credits,
            long fixedBonusCredits) {
    }
}
