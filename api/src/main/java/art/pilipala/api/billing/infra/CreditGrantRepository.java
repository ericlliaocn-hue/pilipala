package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditGrant;

public interface CreditGrantRepository {

    /** 批次归属租户（{@code tenant_id} 非空），{@code grant.userId()} 只表示操作者。 */
    CreditGrant insert(UUID tenantId, CreditGrant grant);

    List<CreditGrant> listConsumableForUpdate(UUID walletId);

    void updateRemaining(long id, long remaining);

    List<CreditGrant> listByUser(UUID userId, int limit);

    List<CreditGrant> findExpiredForUpdate(Instant before, int limit);

    /** C 端钱包快照里的批次，按该租户钱包的 {@code wallet_id} 查。 */
    List<CreditGrant> listActiveSummary(UUID walletId, int limit);

    List<CreditGrant> listSubscriptionRemainingForUpdate(UUID tenantId);
}
