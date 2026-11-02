package art.pilipala.api.billing.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.billing.domain.Wallet;

@Repository
public class MybatisWalletRepository implements WalletRepository {

    private final WalletMapper mapper;

    public MybatisWalletRepository(WalletMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Wallet> findWallet(UUID walletId) {
        return Optional.ofNullable(mapper.findWallet(walletId));
    }

    @Override
    public Optional<Wallet> findByTenantId(UUID tenantId) {
        return Optional.ofNullable(mapper.findByTenantId(tenantId));
    }

    @Override
    public UUID openForTenant(UUID tenantId) {
        return mapper.openForTenant(tenantId);
    }

    @Override
    public boolean reserve(UUID walletId, long amount) {
        return mapper.reserve(walletId, amount) > 0;
    }

    @Override
    public boolean addAvailable(UUID walletId, long amount) {
        return mapper.addAvailable(walletId, amount) > 0;
    }

    @Override
    public boolean deductAvailable(UUID walletId, long amount) {
        return mapper.deductAvailable(walletId, amount) > 0;
    }

    @Override
    public boolean consumeFrozen(UUID walletId, long amount) {
        return mapper.consumeFrozen(walletId, amount) > 0;
    }

    @Override
    public boolean releaseFrozen(UUID walletId, long amount) {
        return mapper.releaseFrozen(walletId, amount) > 0;
    }
}
