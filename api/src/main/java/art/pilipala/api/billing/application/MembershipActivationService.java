package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import art.pilipala.api.billing.domain.MembershipPlan;
import art.pilipala.api.billing.domain.MembershipPlanPrice;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.UserSubscription;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.infra.MembershipPlanMapper;
import art.pilipala.api.billing.infra.MembershipPlanPriceMapper;
import art.pilipala.api.billing.infra.UserSubscriptionMapper;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipActivationService {

    private final MembershipPlanPriceMapper prices;
    private final MembershipPlanMapper plans;
    private final UserSubscriptionMapper subscriptions;
    private final WalletService wallets;
    private final CreditGrantService grantService;

    public MembershipActivationService(
            MembershipPlanPriceMapper prices,
            MembershipPlanMapper plans,
            UserSubscriptionMapper subscriptions,
            WalletService wallets,
            CreditGrantService grantService) {
        this.prices = prices;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.wallets = wallets;
        this.grantService = grantService;
    }

    @Transactional
    public WalletLedgerEntry activateFromPaidOrder(RechargeOrder order) {
        if (order.planPriceId() == null) {
            throw JobException.validation();
        }
        MembershipPlanPrice price = prices.findById(order.planPriceId());
        if (price == null || !price.enabled()) {
            throw JobException.validation();
        }
        MembershipPlan plan = plans.findById(price.planId());
        if (plan == null || !plan.enabled()) {
            throw JobException.validation();
        }
        if (subscriptions.findActiveByTenant(order.tenantId()) != null) {
            throw JobException.validation();
        }
        Instant now = Instant.now();
        Instant periodEnd = MembershipCycle.subscriptionEnd(now, price.cycle());
        subscriptions.expireActiveForTenant(order.tenantId());
        UserSubscription sub = subscriptions.insert(
                order.userId(),
                order.tenantId(),
                plan.id(),
                plan.code(),
                price.id(),
                price.cycle(),
                price.variantCode(),
                "active",
                now,
                now,
                periodEnd,
                1,
                price.creditsPerMonth(),
                now,
                order.id());
        Wallet wallet = wallets.ensureWallet(order.tenantId());
        grantService.clearSubscriptionGrantRemaining(order.tenantId(), wallet.id());
        long granted = issueMonthlyGrant(
                order.tenantId(), order.userId(), wallet, sub.id(), price.creditsPerMonth(), now);
        return wallets.credit(order.tenantId(), order.userId(), granted, order.id());
    }

    @Transactional
    public long issueMonthlyGrant(
            UUID tenantId, UUID userId, Wallet wallet, long subscriptionId, long credits, Instant from) {
        Instant expires = MembershipCycle.grantExpires(from);
        return grantService.grantForSubscription(
                tenantId, userId, wallet, String.valueOf(subscriptionId), credits, expires);
    }

    @Transactional
    public int processDueGrants(int batchSize) {
        int processed = 0;
        Instant now = Instant.now();
        for (UserSubscription sub : subscriptions.listActiveForGrant(batchSize)) {
            if (!sub.currentPeriodEnd().isAfter(now)) {
                subscriptions.expireIfEnded(sub.id(), now);
                continue;
            }
            Instant last = sub.lastGrantAt() != null ? sub.lastGrantAt() : sub.startedAt();
            if (last.plus(MembershipCycle.GRANT_PERIOD_DAYS, ChronoUnit.DAYS).isAfter(now)) {
                continue;
            }
            Wallet wallet = wallets.ensureWallet(sub.tenantId());
            grantService.clearSubscriptionGrantRemaining(sub.tenantId(), wallet.id());
            long granted = issueMonthlyGrant(
                    sub.tenantId(), sub.userId(), wallet, sub.id(), sub.creditsPerMonth(), now);
            wallets.credit(sub.tenantId(), sub.userId(), granted);
            subscriptions.updateLastGrantAt(sub.id(), now);
            processed++;
        }
        return processed;
    }
}
