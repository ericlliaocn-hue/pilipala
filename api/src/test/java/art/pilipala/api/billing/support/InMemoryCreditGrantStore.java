package art.pilipala.api.billing.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import art.pilipala.api.auth.support.TestPersonalTenants;
import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.infra.CreditGrantRepository;

public class InMemoryCreditGrantStore implements CreditGrantRepository {

    private final AtomicLong ids = new AtomicLong(1);
    private final List<CreditGrant> grants = new ArrayList<>();

    @Override
    public CreditGrant insert(UUID tenantId, CreditGrant draft) {
        CreditGrant saved = new CreditGrant(
                ids.getAndIncrement(),
                draft.userId(),
                draft.walletId(),
                tenantId,
                draft.sourceType(),
                draft.sourceId(),
                draft.grantType(),
                draft.creditsTotal(),
                draft.creditsRemaining(),
                draft.expiresAt(),
                draft.productId(),
                Instant.now());
        grants.add(saved);
        return saved;
    }

    /** 测试便捷：直接写一条批次，租户取自草稿。 */
    public CreditGrant insert(CreditGrant draft) {
        return insert(draft.tenantId() == null ? TestPersonalTenants.TENANT : draft.tenantId(), draft);
    }

    @Override
    public List<CreditGrant> listConsumableForUpdate(UUID walletId) {
        return grants.stream()
                .filter(g -> g.walletId().equals(walletId) && g.creditsRemaining() > 0)
                .sorted(Comparator.comparing(CreditGrant::expiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CreditGrant::createdAt))
                .toList();
    }

    @Override
    public void updateRemaining(long id, long remaining) {
        for (int i = 0; i < grants.size(); i++) {
            CreditGrant g = grants.get(i);
            if (g.id() == id) {
                grants.set(i, new CreditGrant(
                        g.id(),
                        g.userId(),
                        g.walletId(),
                        g.tenantId(),
                        g.sourceType(),
                        g.sourceId(),
                        g.grantType(),
                        g.creditsTotal(),
                        remaining,
                        g.expiresAt(),
                        g.productId(),
                        g.createdAt()));
                return;
            }
        }
    }

    @Override
    public List<CreditGrant> listByUser(UUID userId, int limit) {
        return grants.stream().filter(g -> g.userId().equals(userId)).limit(limit).toList();
    }

    @Override
    public List<CreditGrant> findExpiredForUpdate(Instant before, int limit) {
        return grants.stream()
                .filter(g -> g.creditsRemaining() > 0 && g.expiresAt() != null && g.expiresAt().isBefore(before))
                .limit(limit)
                .toList();
    }

    /** C 端钱包快照按该租户钱包的 wallet_id 看批次。 */
    @Override
    public List<CreditGrant> listActiveSummary(UUID walletId, int limit) {
        return grants.stream().filter(g -> g.walletId().equals(walletId)).limit(limit).toList();
    }

    @Override
    public List<CreditGrant> listSubscriptionRemainingForUpdate(UUID tenantId) {
        return grants.stream()
                .filter(g -> tenantId.equals(g.tenantId()) && "subscription".equals(g.grantType())
                        && g.creditsRemaining() > 0)
                .toList();
    }

    public List<CreditGrant> all() {
        return List.copyOf(grants);
    }
}
