package art.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import art.pilipala.api.auth.support.TestPersonalTenants;
import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.job.infra.JobException;
import org.junit.jupiter.api.Test;

class CreditSupermarketTest {

    @Test
    void customSliderPricingAndBounds() {
        assertThat(CreditProductMeta.priceFenForCredits(600, 15)).isEqualTo(4000);
        String meta = "{\"min_credits\":600,\"max_credits\":499995,\"credits_per_yuan\":15}";
        assertThatThrownBy(() -> validateCustom(599, meta)).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> validateCustom(500_000, meta)).isInstanceOf(JobException.class);
    }

    @Test
    void consumeUsesEarliestExpiryGrantFirst() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
        CreditGrantService grants = new CreditGrantService(grantStore, store, store);
        WalletService wallets = new WalletService(store, store, store, grants);
        UUID user = UUID.randomUUID();
        UUID tenant = TestPersonalTenants.TENANT;
        Wallet wallet = wallets.ensureWallet(tenant);
        Instant soon = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant later = Instant.now().plus(30, ChronoUnit.DAYS);
        grantStore.insert(new CreditGrant(0, user, wallet.id(), tenant, "recharge", "a", "general", 50, 50, later,
                null, Instant.now()));
        grantStore.insert(new CreditGrant(0, user, wallet.id(), tenant, "recharge", "b", "general", 50, 50, soon,
                null, Instant.now()));
        wallets.credit(tenant, user, 100);
        WalletHold hold = wallets.freeze(tenant, user, 60);
        wallets.confirm(hold.id());
        List<CreditGrant> remaining = grantStore.all();
        CreditGrant earliest = remaining.stream().filter(g -> g.expiresAt().equals(soon)).findFirst().orElseThrow();
        CreditGrant other = remaining.stream().filter(g -> g.expiresAt().equals(later)).findFirst().orElseThrow();
        assertThat(earliest.creditsRemaining()).isZero();
        assertThat(other.creditsRemaining()).isEqualTo(40);
    }

    @Test
    void expireJobDeductsAvailable() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
        CreditGrantService grantService = new CreditGrantService(grantStore, store, store);
        CreditGrantExpireJob job = new CreditGrantExpireJob(grantStore, grantService);
        UUID user = UUID.randomUUID();
        UUID tenant = TestPersonalTenants.TENANT;
        Wallet wallet = store.findWallet(store.openForTenant(tenant)).orElseThrow();
        store.addAvailable(wallet.id(), 80);
        grantStore.insert(new CreditGrant(
                0, user, wallet.id(), tenant, "recharge", "x", "general", 80, 80,
                Instant.now().minus(1, ChronoUnit.HOURS), null, Instant.now()));
        assertThat(job.expireDueGrants()).isEqualTo(1);
        assertThat(store.walletOf(tenant).available()).isZero();
        assertThat(grantStore.all().get(0).creditsRemaining()).isZero();
        assertThat(store.findLedgerByUserId(user).get(0).type()).isEqualTo(WalletLedgerType.EXPIRE);
    }

    @Test
    void seedMigrationHasNoCompetitorTrademarks() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V14__credit_supermarket.sql"));
        List<String> banned = List.of("LibTV", "Seedance", "Kling", "Wan", "Happy Horse");
        for (String word : banned) {
            assertThat(sql).doesNotContainIgnoringCase(word);
        }
    }

    private static void validateCustom(long credits, String metaJson) {
        CreditProductMeta.CustomSlider slider = CreditProductMeta.parseCustomSlider(metaJson);
        if (credits < slider.minCredits() || credits > slider.maxCredits()) {
            throw JobException.validation();
        }
    }
}
