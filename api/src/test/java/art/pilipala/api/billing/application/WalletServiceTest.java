package art.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.JobTerminalState;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.billing.support.LedgerAssertions;
import art.pilipala.api.job.infra.JobException;
import org.junit.jupiter.api.Test;

/**
 * 预付费账本：充值、冻结、确认实扣、解冻退回，以及它们的不变量与幂等。
 *
 * <p>余额属于租户（{@code TENANT}），{@code USER} 只是操作者：每条路径都传租户，
 * 冻结单与流水也都带上租户。
 */
class WalletServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final long COST = 10;

    private final InMemoryWalletStore store = new InMemoryWalletStore();
    private final InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
    private final CreditGrantService grantService = new CreditGrantService(grantStore, store, store);
    private final WalletService wallets = new WalletService(store, store, store, grantService);

    @Test
    void topUpCreditsTheWalletAndWritesATopupEntry() {
        wallets.credit(TENANT, USER, 100);

        Wallet wallet = store.walletOf(TENANT);
        assertThat(wallet.available()).isEqualTo(100);
        assertThat(wallet.frozen()).isZero();

        List<WalletLedgerEntry> entries = store.findLedgerByUserId(USER);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).type()).isEqualTo(WalletLedgerType.TOPUP);
        assertThat(entries.get(0).amount()).isEqualTo(100);
        assertThat(entries.get(0).holdId()).isNull();
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void topUpRejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> wallets.credit(TENANT, USER, 0))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));
        assertThat(store.allLedger()).isEmpty();
    }

    @Test
    void freezeMovesAvailableToFrozenAndWritesAHold() {
        wallets.credit(TENANT, USER, 100);

        WalletHold hold = wallets.freeze(TENANT, USER, COST);

        Wallet wallet = store.walletOf(TENANT);
        assertThat(wallet.available()).isEqualTo(90);
        assertThat(wallet.frozen()).isEqualTo(COST);

        assertThat(store.allHolds()).hasSize(1);
        assertThat(hold.tenantId()).isEqualTo(TENANT);
        assertThat(hold.userId()).isEqualTo(USER);
        assertThat(hold.status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(hold.amount()).isEqualTo(COST);
        assertThat(hold.jobId()).isNull();
        assertThat(hold.finalizedAt()).isNull();

        List<WalletLedgerEntry> entries = store.findLedgerByUserId(USER);
        assertThat(entries).hasSize(2);
        WalletLedgerEntry freeze = entries.get(1);
        assertThat(freeze.type()).isEqualTo(WalletLedgerType.FREEZE);
        assertThat(freeze.amount()).isEqualTo(COST);
        assertThat(freeze.holdId()).isEqualTo(hold.id());
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void freezeFailsWhenTheBalanceIsNotEnough() {
        wallets.credit(TENANT, USER, 5);

        assertThatThrownBy(() -> wallets.freeze(TENANT, USER, COST))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("WALLET_INSUFFICIENT"));

        assertThat(store.allHolds()).isEmpty();
        assertThat(store.walletOf(TENANT).available()).isEqualTo(5);
        assertThat(store.walletOf(TENANT).frozen()).isZero();
    }

    @Test
    void anotherTenantsBalanceIsUntouched() {
        wallets.credit(TENANT, USER, 100);
        wallets.credit(UUID.randomUUID(), USER, 30);

        wallets.freeze(TENANT, USER, COST);

        assertThat(store.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(store.allHolds()).hasSize(1);
    }

    @Test
    void bindJobLinksTheHoldAndItsFreezeEntry() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);
        UUID jobId = UUID.randomUUID();

        wallets.bindJob(hold.id(), jobId);

        assertThat(store.findHold(hold.id()).orElseThrow().jobId()).isEqualTo(jobId);
        assertThat(store.findLedgerByUserId(USER).get(1).jobId()).isEqualTo(jobId);
    }

    @Test
    void confirmConsumesFrozenAndKeepsAvailable() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);

        assertThat(wallets.confirm(hold.id())).isTrue();

        Wallet wallet = store.walletOf(TENANT);
        assertThat(wallet.available()).isEqualTo(90);
        assertThat(wallet.frozen()).isZero();

        WalletHold confirmed = store.findHold(hold.id()).orElseThrow();
        assertThat(confirmed.status()).isEqualTo(WalletHoldStatus.CONFIRMED);
        assertThat(confirmed.finalizedAt()).isNotNull();

        WalletLedgerEntry entry = store.findLedgerByUserId(USER).get(2);
        assertThat(entry.type()).isEqualTo(WalletLedgerType.CONFIRM);
        assertThat(entry.amount()).isEqualTo(COST);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void confirmIsIdempotent() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);
        wallets.confirm(hold.id());

        assertThat(wallets.confirm(hold.id())).isFalse();

        assertThat(store.walletOf(TENANT).frozen()).isZero();
        assertThat(store.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void releaseReturnsTheFrozenAmountToAvailable() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);

        assertThat(wallets.release(hold.id())).isTrue();

        Wallet wallet = store.walletOf(TENANT);
        assertThat(wallet.available()).isEqualTo(100);
        assertThat(wallet.frozen()).isZero();
        assertThat(store.findHold(hold.id()).orElseThrow().status()).isEqualTo(WalletHoldStatus.RELEASED);

        WalletLedgerEntry entry = store.findLedgerByUserId(USER).get(2);
        assertThat(entry.type()).isEqualTo(WalletLedgerType.RELEASE);
        assertThat(entry.amount()).isEqualTo(COST);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void releaseIsIdempotent() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);
        wallets.release(hold.id());

        assertThat(wallets.release(hold.id())).isFalse();

        assertThat(store.walletOf(TENANT).available()).isEqualTo(100);
        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void switchingToTheOppositeTerminalStateIsRejected() {
        wallets.credit(TENANT, USER, 100);
        WalletHold hold = wallets.freeze(TENANT, USER, COST);
        wallets.confirm(hold.id());

        assertThatThrownBy(() -> wallets.release(hold.id()))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));

        assertThat(store.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(store.walletOf(TENANT).frozen()).isZero();
        assertThat(store.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(store, TENANT);
    }

    @Test
    void settlingByJobTerminalStateConfirmsOrReleases() {
        wallets.credit(TENANT, USER, 100);
        WalletHold succeeded = wallets.freeze(TENANT, USER, COST);
        WalletHold dead = wallets.freeze(TENANT, USER, COST);

        wallets.settleForJob(succeeded.id(), JobTerminalState.SUCCEEDED);
        wallets.settleForJob(dead.id(), JobTerminalState.DEAD);

        Wallet wallet = store.walletOf(TENANT);
        assertThat(wallet.frozen()).isZero();
        assertThat(wallet.available()).isEqualTo(90);
        LedgerAssertions.assertInvariants(store, TENANT);
    }
}
