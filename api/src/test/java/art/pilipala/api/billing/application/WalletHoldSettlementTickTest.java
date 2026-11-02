package art.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import art.pilipala.api.auth.support.TestPersonalTenants;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.billing.support.LedgerAssertions;
import org.junit.jupiter.api.Test;

/**
 * 冻结单的后台对账结算：任务终态与冻结单终态之间的窗口由它补上。
 *
 * <p>worker 只改 job.status，不碰钱包（ADR 0004），所以这条链路必须成立。
 * 结算按租户的钱包找，冻结单上的 {@code tenant_id} 是唯一入口。
 */
class WalletHoldSettlementTickTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = TestPersonalTenants.TENANT;
    private static final long COST = 10;

    private final Map<UUID, String> jobStates = new HashMap<>();
    private final InMemoryWalletStore store = new InMemoryWalletStore(jobStates::get);
    private final InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
    private final CreditGrantService grantService = new CreditGrantService(grantStore, store, store);
    private final WalletService wallets = new WalletService(store, store, store, grantService);
    private final WalletHoldSettlementTick tick =
            new WalletHoldSettlementTick(store, wallets, new BillingProperties(10, 0, 200, 50));

    @Test
    void tickConfirmsTheHoldOfASucceededJob() {
        WalletHold hold = frozenHold();
        jobStates.put(jobIdOf(hold), "succeeded");

        assertThat(tick.tick()).isEqualTo(1);

        assertThat(store.findHold(hold.id()).orElseThrow().status()).isEqualTo(WalletHoldStatus.CONFIRMED);
        assertThat(store.walletOf(TENANT).frozen()).isZero();
        assertThat(store.walletOf(TENANT).available()).isEqualTo(90);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void tickReleasesTheHoldOfADeadJob() {
        WalletHold hold = frozenHold();
        jobStates.put(jobIdOf(hold), "dead");

        assertThat(tick.tick()).isEqualTo(1);

        assertThat(store.findHold(hold.id()).orElseThrow().status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(store.walletOf(TENANT).frozen()).isZero();
        assertThat(store.walletOf(TENANT).available()).isEqualTo(100);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void tickLeavesTheHoldOfARunningJobAlone() {
        WalletHold hold = frozenHold();
        jobStates.put(jobIdOf(hold), "running");

        assertThat(tick.tick()).isZero();

        assertThat(store.findHold(hold.id()).orElseThrow().status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(store.walletOf(TENANT).frozen()).isEqualTo(COST);
    }

    @Test
    void settlingTheSameHoldTwiceIsIdempotent() {
        WalletHold hold = frozenHold();
        jobStates.put(jobIdOf(hold), "succeeded");

        assertThat(tick.tick()).isEqualTo(1);
        assertThat(tick.tick()).isZero();

        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        assertThat(store.walletOf(TENANT).available()).isEqualTo(90);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void settlingForOneTenantDoesNotTouchAnother() {
        WalletHold hold = frozenHold();
        jobStates.put(jobIdOf(hold), "succeeded");

        assertThat(tick.settleForTenant(UUID.randomUUID())).isZero();
        assertThat(store.findHold(hold.id()).orElseThrow().status()).isEqualTo(WalletHoldStatus.FROZEN);
    }

    private WalletHold frozenHold() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);
        UUID jobId = UUID.randomUUID();
        wallets.bindJob(hold.id(), jobId);
        jobStates.put(jobId, "queued");
        return hold;
    }

    private UUID jobIdOf(WalletHold hold) {
        return store.findHold(hold.id()).orElseThrow().jobId();
    }
}
