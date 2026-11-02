package art.pilipala.api.billing.application;

import java.util.UUID;

import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.infra.CreditGrantRepository;
import org.springframework.stereotype.Service;

/**
 * 查当前租户的钱包快照。
 *
 * <p>读之前先把该租户「任务已终态但还没结算」的冻结单结算掉，
 * 这样客户端紧接着查 job 再查钱包时看到的余额已经是收敛后的（消除轮询窗口抖动）。
 *
 * <p>响应里的 {@code user_id} 是当前登录用户，余额来自该租户的钱包。
 */
@Service
public class GetWallet {

    private final WalletService wallets;
    private final WalletHoldSettlementTick settlement;
    private final CreditGrantRepository grants;

    public GetWallet(WalletService wallets, WalletHoldSettlementTick settlement, CreditGrantRepository grants) {
        this.wallets = wallets;
        this.settlement = settlement;
        this.grants = grants;
    }

    public WalletView get(UUID userId, UUID tenantId) {
        settlement.settleForTenant(tenantId);
        Wallet wallet = wallets.ensureWallet(tenantId);
        var summaries = grants.listActiveSummary(wallet.id(), 20).stream().map(WalletGrantSummaryView::of).toList();
        return new WalletView(userId.toString(), wallet.available(), wallet.frozen(), wallet.updatedAt(), summaries);
    }
}
