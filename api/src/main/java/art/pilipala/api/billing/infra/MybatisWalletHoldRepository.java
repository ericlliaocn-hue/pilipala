package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.billing.domain.PendingSettlement;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;

@Repository
public class MybatisWalletHoldRepository implements WalletHoldRepository {

    private final WalletHoldMapper mapper;

    public MybatisWalletHoldRepository(WalletHoldMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public WalletHold insert(UUID tenantId, UUID userId, UUID jobId, long amount) {
        return mapper.insert(tenantId, userId, jobId, amount);
    }

    @Override
    public Optional<WalletHold> findHold(UUID holdId) {
        return Optional.ofNullable(mapper.findHold(holdId));
    }

    @Override
    public Optional<WalletHold> finalizeIfFrozen(UUID holdId, WalletHoldStatus target) {
        return Optional.ofNullable(mapper.finalizeIfFrozen(holdId, target.value()));
    }

    @Override
    public boolean bindJob(UUID holdId, UUID jobId) {
        return mapper.bindJob(holdId, jobId) > 0;
    }

    @Override
    public List<PendingSettlement> findPendingSettlement(UUID tenantId, int limit) {
        List<PendingSettlement> rows = tenantId == null
                ? mapper.findPendingSettlementAll(limit)
                : mapper.findPendingSettlementByTenant(tenantId, limit);
        return rows.stream().filter(pending -> pending.jobState() != null).toList();
    }
}
