package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.billing.domain.CreditGrant;

@Repository
public class MybatisCreditGrantRepository implements CreditGrantRepository {

    private final CreditGrantMapper mapper;

    public MybatisCreditGrantRepository(CreditGrantMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CreditGrant insert(UUID tenantId, CreditGrant draft) {
        return mapper.insert(
                draft.userId(),
                draft.walletId(),
                tenantId,
                draft.sourceType(),
                draft.sourceId(),
                draft.grantType(),
                draft.creditsTotal(),
                draft.creditsRemaining(),
                draft.expiresAt(),
                draft.productId());
    }

    @Override
    public List<CreditGrant> listConsumableForUpdate(UUID walletId) {
        return mapper.listConsumableForUpdate(walletId);
    }

    @Override
    public void updateRemaining(long id, long remaining) {
        mapper.updateRemaining(id, remaining);
    }

    @Override
    public List<CreditGrant> listByUser(UUID userId, int limit) {
        return mapper.listByUser(userId, limit);
    }

    @Override
    public List<CreditGrant> findExpiredForUpdate(Instant before, int limit) {
        return mapper.findExpiredForUpdate(before, limit);
    }

    @Override
    public List<CreditGrant> listActiveSummary(UUID walletId, int limit) {
        return mapper.listActiveSummary(walletId, limit);
    }

    @Override
    public List<CreditGrant> listSubscriptionRemainingForUpdate(UUID tenantId) {
        return mapper.listSubscriptionRemainingForUpdate(tenantId);
    }
}
