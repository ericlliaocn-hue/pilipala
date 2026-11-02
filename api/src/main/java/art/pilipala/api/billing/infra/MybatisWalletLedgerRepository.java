package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;

@Repository
public class MybatisWalletLedgerRepository implements WalletLedgerRepository {

    private final WalletLedgerMapper mapper;

    public MybatisWalletLedgerRepository(WalletLedgerMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public WalletLedgerEntry insert(
            UUID tenantId, UUID walletId, UUID userId, WalletLedgerType type, long amount, UUID holdId, UUID jobId,
            UUID rechargeOrderId) {
        return mapper.insert(tenantId, walletId, userId, type.value(), amount, holdId, jobId, rechargeOrderId);
    }

    @Override
    public boolean bindJobId(UUID holdId, UUID jobId) {
        return mapper.bindJobId(holdId, jobId) > 0;
    }

    @Override
    public List<WalletLedgerEntry> findLedgerByUserId(UUID userId) {
        return mapper.findLedgerByUserId(userId);
    }

    @Override
    public List<WalletLedgerEntry> findRecentByUserId(UUID userId, int limit) {
        return mapper.findRecentByUserId(userId, limit);
    }
}
