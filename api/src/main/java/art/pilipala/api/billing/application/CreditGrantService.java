package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.CreditGrantRepository;
import art.pilipala.api.billing.infra.WalletLedgerRepository;
import art.pilipala.api.billing.infra.WalletRepository;
import art.pilipala.api.job.infra.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 积分批次服务。批次与流水都挂在租户的钱包上，{@code userId} 只表示操作者；
 * 本服务不解析当前租户，{@code tenantId} 由调用方给出。
 */
@Service
public class CreditGrantService {

    private static final Logger log = LoggerFactory.getLogger(CreditGrantService.class);
    private static final int ADMIN_VALIDITY_DAYS = 730;

    private final CreditGrantRepository grants;
    private final WalletRepository wallets;
    private final WalletLedgerRepository ledger;

    public CreditGrantService(CreditGrantRepository grants, WalletRepository wallets, WalletLedgerRepository ledger) {
        this.grants = grants;
        this.wallets = wallets;
        this.ledger = ledger;
    }

    @Transactional
    public void grantForAdmin(UUID tenantId, UUID userId, UUID walletId, long amount) {
        if (amount <= 0) {
            throw JobException.validation();
        }
        Instant expires = Instant.now().plus(ADMIN_VALIDITY_DAYS, ChronoUnit.DAYS);
        grants.insert(tenantId, new CreditGrant(
                0L, userId, walletId, tenantId, "admin", "admin-topup", "general", amount, amount, expires, null,
                Instant.now()));
    }

    @Transactional
    public long grantForRecharge(
            UUID tenantId,
            UUID userId,
            Wallet wallet,
            UUID rechargeOrderId,
            CreditProduct product,
            long baseCredits,
            long fixedBonus,
            long firstPurchaseBonus) {
        String sourceId = rechargeOrderId.toString();
        Instant expires = expiresAt(product);
        long total = 0;
        if (baseCredits > 0) {
            grants.insert(tenantId, new CreditGrant(
                    0L, userId, wallet.id(), tenantId, "recharge", sourceId, "general", baseCredits, baseCredits,
                    expires, product == null ? null : product.id(), Instant.now()));
            total += baseCredits;
        }
        long bonus = fixedBonus + firstPurchaseBonus;
        if (bonus > 0) {
            grants.insert(tenantId, new CreditGrant(
                    0L, userId, wallet.id(), tenantId, "bonus", sourceId, "bonus", bonus, bonus, expires,
                    product == null ? null : product.id(), Instant.now()));
            total += bonus;
        }
        return total;
    }

    @Transactional
    public long grantLegacyRecharge(UUID tenantId, UUID userId, Wallet wallet, UUID rechargeOrderId, long credits) {
        Instant expires = Instant.now().plus(730, ChronoUnit.DAYS);
        grants.insert(tenantId, new CreditGrant(
                0L, userId, wallet.id(), tenantId, "recharge", rechargeOrderId.toString(), "general", credits, credits,
                expires, null, Instant.now()));
        return credits;
    }

    @Transactional
    public void consumeFromGrants(UUID walletId, UUID userId, long amount) {
        if (amount <= 0) {
            return;
        }
        long remaining = amount;
        for (CreditGrant grant : grants.listConsumableForUpdate(walletId)) {
            if (remaining <= 0) {
                break;
            }
            long take = Math.min(remaining, grant.creditsRemaining());
            long left = grant.creditsRemaining() - take;
            grants.updateRemaining(grant.id(), left);
            remaining -= take;
        }
        if (remaining > 0) {
            log.warn("grant 批次不足，wallet={} user={} shortfall={}", walletId, userId, remaining);
        }
    }

    @Transactional
    public WalletLedgerEntry expireGrant(CreditGrant grant) {
        long expired = grant.creditsRemaining();
        if (expired <= 0) {
            grants.updateRemaining(grant.id(), 0);
            return null;
        }
        if (!wallets.deductAvailable(grant.walletId(), expired)) {
            log.warn("过期扣减 available 失败 grant={} wallet={}", grant.id(), grant.walletId());
        }
        grants.updateRemaining(grant.id(), 0);
        return ledger.insert(
                grant.tenantId(),
                grant.walletId(),
                grant.userId(),
                WalletLedgerType.EXPIRE,
                -expired,
                null,
                null,
                null);
    }

    @Transactional
    public long grantForSubscription(
            UUID tenantId, UUID userId, Wallet wallet, String sourceId, long credits, Instant expiresAt) {
        if (credits <= 0) {
            return 0;
        }
        grants.insert(tenantId, new CreditGrant(
                0L,
                userId,
                wallet.id(),
                tenantId,
                "subscription",
                sourceId,
                "subscription",
                credits,
                credits,
                expiresAt,
                null,
                Instant.now()));
        return credits;
    }

    @Transactional
    public long clearSubscriptionGrantRemaining(UUID tenantId, UUID walletId) {
        long total = 0;
        for (CreditGrant grant : grants.listSubscriptionRemainingForUpdate(tenantId)) {
            long left = grant.creditsRemaining();
            if (left <= 0) {
                continue;
            }
            if (!wallets.deductAvailable(walletId, left)) {
                log.warn("订阅重置扣减 available 失败 grant={} wallet={}", grant.id(), walletId);
            }
            grants.updateRemaining(grant.id(), 0);
            ledger.insert(tenantId, grant.walletId(), grant.userId(), WalletLedgerType.EXPIRE, -left, null, null, null);
            total += left;
        }
        return total;
    }

    private static Instant expiresAt(CreditProduct product) {
        if (product == null || product.validityDays() == null) {
            return null;
        }
        return Instant.now().plus(product.validityDays(), ChronoUnit.DAYS);
    }
}
