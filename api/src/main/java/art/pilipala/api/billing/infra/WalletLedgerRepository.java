package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;

/**
 * 账本流水的存取契约。
 *
 * <p>每次余额或冻结额变动都写一条流水；流水只追加，不改不删。
 * 流水归属租户（{@code tenant_id} 非空），{@code userId} 只表示操作者。
 */
public interface WalletLedgerRepository {

    WalletLedgerEntry insert(
            UUID tenantId, UUID walletId, UUID userId, WalletLedgerType type, long amount, UUID holdId, UUID jobId,
            UUID rechargeOrderId);

    /** 回填流水关联的 job；已回填时不覆盖。 */
    boolean bindJobId(UUID holdId, UUID jobId);

    /** 某个用户的全部流水，按记账时间排序（对账与测试用）。 */
    List<WalletLedgerEntry> findLedgerByUserId(UUID userId);

    /**
     * 某个用户最近的流水，按记账时间倒序（WP11 运维排查用）。
     *
     * @param limit 单页上限
     */
    List<WalletLedgerEntry> findRecentByUserId(UUID userId, int limit);
}
