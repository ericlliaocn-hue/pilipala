package art.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import art.pilipala.api.auth.support.TestPersonalTenants;
import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import org.junit.jupiter.api.Test;

class MembershipBillingTest {

    @Test
    void subscriptionResetClearsRemainingGrant() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
        CreditGrantService grants = new CreditGrantService(grantStore, store, store);
        WalletService wallets = new WalletService(store, store, store, grants);
        UUID user = UUID.randomUUID();
        UUID tenant = TestPersonalTenants.TENANT;
        Wallet wallet = wallets.ensureWallet(tenant);
        store.addAvailable(wallet.id(), 2000);
        grantStore.insert(new CreditGrant(
                0,
                user,
                wallet.id(),
                tenant,
                "subscription",
                "1",
                "subscription",
                2000,
                1500,
                Instant.now().plus(10, ChronoUnit.DAYS),
                null,
                Instant.now()));
        long cleared = grants.clearSubscriptionGrantRemaining(tenant, wallet.id());
        assertThat(cleared).isEqualTo(1500);
        assertThat(store.walletOf(tenant).available()).isEqualTo(500);
        assertThat(grantStore.all().get(0).creditsRemaining()).isZero();
        assertThat(store.findLedgerByUserId(user).stream().anyMatch(e -> e.type() == WalletLedgerType.EXPIRE)).isTrue();
    }

    @Test
    void planRankSoftGate() {
        assertThat(MembershipPlanRank.meetsMinPlan("deluxe", "pro")).isTrue();
        assertThat(MembershipPlanRank.meetsMinPlan("plus", "pro")).isFalse();
        assertThat(MembershipPlanRank.meetsMinPlan(null, "pro")).isFalse();
    }

    @Test
    void v15MigrationHasNoCompetitorTrademarks() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V15__membership.sql"));
        List<String> banned = List.of("LibTV", "Seedance", "Kling", "Wan", "Happy Horse");
        for (String word : banned) {
            assertThat(sql).doesNotContainIgnoringCase(word);
        }
    }
}
