package art.pilipala.api.billing.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import art.pilipala.api.billing.domain.JobTerminalState;
import art.pilipala.api.billing.domain.PendingSettlement;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.WalletHoldRepository;
import art.pilipala.api.billing.infra.WalletLedgerRepository;
import art.pilipala.api.billing.infra.WalletRepository;

/**
 * 内存版钱包仓储：账本测试不需要 Postgres。
 *
 * <p>行为对齐 {@code Jdbc*}Repository 的语义（一租户一钱包、余额守卫、
 * 冻结单终态只进一次），只是把数据放在内存里。三个仓储接口一起实现，
 * 方便用例直接拿到同一份数据。
 *
 * <p>钱包要知道任务终态才能算出「待结算」的冻结单，所以构造时可以传一个
 * 任务状态查询函数（测试里接 {@code InMemoryJobStore}）。
 */
public class InMemoryWalletStore implements WalletRepository, WalletHoldRepository, WalletLedgerRepository {

    private final Map<UUID, Wallet> wallets = new LinkedHashMap<>();
    private final Map<UUID, WalletHold> holds = new LinkedHashMap<>();
    private final List<WalletLedgerEntry> ledger = new ArrayList<>();
    private final Function<UUID, String> jobStatusLookup;

    public InMemoryWalletStore() {
        this(null);
    }

    public InMemoryWalletStore(Function<UUID, String> jobStatusLookup) {
        this.jobStatusLookup = jobStatusLookup;
    }

    // ----- wallet -----

    @Override
    public Optional<Wallet> findWallet(UUID walletId) {
        return Optional.ofNullable(wallets.get(walletId));
    }

    @Override
    public Optional<Wallet> findByTenantId(UUID tenantId) {
        return wallets.values().stream().filter(wallet -> wallet.tenantId().equals(tenantId)).findFirst();
    }

    /** 钱包属于租户：新租户的钱包 {@code available = frozen = 0}。 */
    @Override
    public UUID openForTenant(UUID tenantId) {
        Wallet wallet = new Wallet(UUID.randomUUID(), tenantId, 0, 0, Instant.now());
        wallets.put(wallet.id(), wallet);
        return wallet.id();
    }

    @Override
    public boolean reserve(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.available() < amount) {
            return false;
        }
        save(new Wallet(wallet.id(), wallet.tenantId(), wallet.available() - amount,
                wallet.frozen() + amount, Instant.now()));
        return true;
    }

    @Override
    public boolean addAvailable(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null) {
            return false;
        }
        save(new Wallet(wallet.id(), wallet.tenantId(), wallet.available() + amount,
                wallet.frozen(), Instant.now()));
        return true;
    }

    @Override
    public boolean deductAvailable(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.available() < amount) {
            return false;
        }
        save(new Wallet(wallet.id(), wallet.tenantId(), wallet.available() - amount,
                wallet.frozen(), Instant.now()));
        return true;
    }

    @Override
    public boolean consumeFrozen(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.frozen() < amount) {
            return false;
        }
        save(new Wallet(wallet.id(), wallet.tenantId(), wallet.available(),
                wallet.frozen() - amount, Instant.now()));
        return true;
    }

    @Override
    public boolean releaseFrozen(UUID walletId, long amount) {
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || wallet.frozen() < amount) {
            return false;
        }
        save(new Wallet(wallet.id(), wallet.tenantId(), wallet.available() + amount,
                wallet.frozen() - amount, Instant.now()));
        return true;
    }

    // ----- wallet_hold -----

    @Override
    public WalletHold insert(UUID tenantId, UUID userId, UUID jobId, long amount) {
        WalletHold hold = new WalletHold(UUID.randomUUID(), tenantId, userId, jobId, amount, WalletHoldStatus.FROZEN,
                Instant.now(), null);
        holds.put(hold.id(), hold);
        return hold;
    }

    @Override
    public Optional<WalletHold> findHold(UUID holdId) {
        return Optional.ofNullable(holds.get(holdId));
    }

    @Override
    public Optional<WalletHold> finalizeIfFrozen(UUID holdId, WalletHoldStatus target) {
        WalletHold hold = holds.get(holdId);
        if (hold == null || hold.status() != WalletHoldStatus.FROZEN) {
            return Optional.empty();
        }
        WalletHold finalized = new WalletHold(hold.id(), hold.tenantId(), hold.userId(), hold.jobId(), hold.amount(),
                target, hold.createdAt(), Instant.now());
        holds.put(holdId, finalized);
        return Optional.of(finalized);
    }

    @Override
    public boolean bindJob(UUID holdId, UUID jobId) {
        WalletHold hold = holds.get(holdId);
        if (hold == null || hold.jobId() != null) {
            return false;
        }
        holds.put(holdId, new WalletHold(hold.id(), hold.tenantId(), hold.userId(), jobId, hold.amount(),
                hold.status(), hold.createdAt(), hold.finalizedAt()));
        return true;
    }

    @Override
    public List<PendingSettlement> findPendingSettlement(UUID tenantId, int limit) {
        return holds.values().stream()
                .filter(hold -> hold.status() == WalletHoldStatus.FROZEN)
                .filter(hold -> tenantId == null || hold.tenantId().equals(tenantId))
                .filter(hold -> hold.jobId() != null)
                .filter(hold -> jobState(hold.jobId()) != null)
                .sorted(Comparator.comparing(WalletHold::createdAt))
                .limit(limit)
                .map(hold -> new PendingSettlement(hold.id(), hold.tenantId(), hold.jobId(), hold.amount(),
                        jobState(hold.jobId())))
                .toList();
    }

    // ----- wallet_ledger -----

    @Override
    public WalletLedgerEntry insert(UUID tenantId, UUID walletId, UUID userId, WalletLedgerType type, long amount,
            UUID holdId, UUID jobId, UUID rechargeOrderId) {
        WalletLedgerEntry entry = new WalletLedgerEntry(UUID.randomUUID(), walletId, userId, type, amount, holdId,
                jobId, rechargeOrderId, Instant.now());
        ledger.add(entry);
        return entry;
    }

    @Override
    public boolean bindJobId(UUID holdId, UUID jobId) {
        boolean updated = false;
        for (int i = 0; i < ledger.size(); i++) {
            WalletLedgerEntry entry = ledger.get(i);
            if (entry.holdId() != null && entry.holdId().equals(holdId) && entry.jobId() == null) {
                ledger.set(i, new WalletLedgerEntry(entry.id(), entry.walletId(), entry.userId(), entry.type(),
                        entry.amount(), entry.holdId(), jobId, entry.rechargeOrderId(), entry.createdAt()));
                updated = true;
            }
        }
        return updated;
    }

    @Override
    public List<WalletLedgerEntry> findLedgerByUserId(UUID userId) {
        return ledger.stream().filter(entry -> entry.userId().equals(userId)).toList();
    }

    @Override
    public List<WalletLedgerEntry> findRecentByUserId(UUID userId, int limit) {
        return ledger.stream()
                .filter(entry -> entry.userId().equals(userId))
                .sorted(Comparator.comparing(WalletLedgerEntry::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    // ----- 测试辅助 -----

    /** 所有流水，按记账顺序。 */
    public List<WalletLedgerEntry> allLedger() {
        return List.copyOf(ledger);
    }

    /** 所有冻结单。 */
    public List<WalletHold> allHolds() {
        return List.copyOf(holds.values());
    }

    /** 某个租户的钱包快照。 */
    public Wallet walletOf(UUID tenantId) {
        return findByTenantId(tenantId).orElseThrow();
    }

    /** 某个钱包 id 的快照。 */
    public Wallet walletById(UUID walletId) {
        return findWallet(walletId).orElseThrow();
    }

    private void save(Wallet wallet) {
        wallets.put(wallet.id(), wallet);
    }

    private JobTerminalState jobState(UUID jobId) {
        if (jobStatusLookup == null) {
            return null;
        }
        return JobTerminalState.of(jobStatusLookup.apply(jobId));
    }
}
