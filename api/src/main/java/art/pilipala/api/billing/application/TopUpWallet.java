package art.pilipala.api.billing.application;

import java.util.UUID;

import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.infra.BillingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 充值用例：加可用余额，并同时记一笔 {@code topup} 流水。
 *
 * <p>这是「往租户账上加钱」的唯一入口：注册赠送、运维脚本、Admin 的受控加额都走这里，
 * 保证任何一笔进账都能在 {@code wallet_ledger} 里查到。
 *
 * <p>钱进的是 {@code tenantId} 那只钱包；{@code userId} 只表示操作者，写进流水便于对账。
 * C 端渠道充值在入账时也应经 {@link #topUpForRecharge}，保证流水带 {@code recharge_order_id}。
 */
@Service
public class TopUpWallet {

    private final WalletService wallets;
    private final BillingProperties properties;
    private final CreditGrantService grantService;

    public TopUpWallet(WalletService wallets, BillingProperties properties, CreditGrantService grantService) {
        this.wallets = wallets;
        this.properties = properties;
        this.grantService = grantService;
    }

    /**
     * 充值。金额必须为正整数。
     *
     * @param tenantId 收款租户
     * @param userId   操作者
     * @param amount   充值的 credit 数
     */
    @Transactional
    public Wallet topUp(UUID tenantId, UUID userId, long amount) {
        Wallet wallet = wallets.ensureWallet(tenantId);
        grantService.grantForAdmin(tenantId, userId, wallet.id(), amount);
        wallets.credit(tenantId, userId, amount);
        return wallets.ensureWallet(tenantId);
    }

    @Transactional
    public Wallet topUpForRecharge(UUID tenantId, UUID userId, long amount, UUID rechargeOrderId) {
        wallets.credit(tenantId, userId, amount, rechargeOrderId);
        return wallets.ensureWallet(tenantId);
    }

    /**
     * 注册时给刚建好的个人租户开钱包。
     *
     * <p>先把钱包插成 0 余额；{@code pilipala.billing.initial-credits > 0} 时再按加额语义
     * 记一笔 {@code topup} 流水和一条同额批次（{@code source_type = admin}、730 天），
     * 余额只经 {@code wallet.id} 增加一次——不在 INSERT 钱包时直接写成赠送金额，
     * 否则同一个数字会记两次。
     *
     * @param tenantId 刚建的个人租户
     * @return 新钱包 id
     */
    @Transactional
    public UUID openPersonalWallet(UUID userId, UUID tenantId) {
        UUID walletId = wallets.openTenantWallet(tenantId);
        long credits = properties.initialCredits();
        if (credits <= 0) {
            return walletId;
        }
        wallets.creditWallet(walletId, tenantId, userId, credits, null);
        grantService.grantForAdmin(tenantId, userId, walletId, credits);
        return walletId;
    }
}
