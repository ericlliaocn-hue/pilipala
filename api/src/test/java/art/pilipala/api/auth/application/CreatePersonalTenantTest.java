package art.pilipala.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.TenantMapper;
import art.pilipala.api.billing.application.CreditGrantService;
import art.pilipala.api.billing.application.TopUpWallet;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.domain.CreditGrant;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 注册建租户：租户、成员、{@code tenant_owner}、钱包挂在同一个 {@code tenant_id} 上；
 * 初始额度为 0 时不写流水也不写批次。
 */
class CreatePersonalTenantTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final TenantMapper tenantMapper = mock(TenantMapper.class);
    private final InMemoryWalletStore wallets = new InMemoryWalletStore();
    private final InMemoryCreditGrantStore grants = new InMemoryCreditGrantStore();

    @BeforeEach
    void tenantIds() {
        when(tenantMapper.insertPersonalTenant(anyString())).thenReturn(TENANT_ID);
        when(tenantMapper.findPersonalTenantId(USER_ID)).thenReturn(TENANT_ID);
    }

    @Test
    void writesTenantMembershipOwnerAndWalletOnTheSameTenant() {
        UUID tenantId = provisioner(0).create(user("eric"));

        assertThat(tenantId).isEqualTo(TENANT_ID);
        verify(tenantMapper).insertMembership(TENANT_ID, USER_ID);
        verify(tenantMapper).insertTenantRole(USER_ID, TENANT_ID, "tenant_owner");

        Wallet wallet = wallets.walletOf(TENANT_ID);
        assertThat(wallet.available()).isZero();
        assertThat(wallet.frozen()).isZero();
        assertThat(wallets.allLedger()).isEmpty();
        assertThat(grants.all()).isEmpty();
    }

    @Test
    void booksInitialCreditsAsOneTopupEntryAndOneGrant() {
        provisioner(120).create(user("eric"));

        Wallet wallet = wallets.walletOf(TENANT_ID);
        assertThat(wallet.available()).isEqualTo(120);

        assertThat(wallets.allLedger()).hasSize(1);
        WalletLedgerEntry entry = wallets.allLedger().get(0);
        assertThat(entry.type()).isEqualTo(WalletLedgerType.TOPUP);
        assertThat(entry.amount()).isEqualTo(120);

        assertThat(grants.all()).hasSize(1);
        CreditGrant grant = grants.all().get(0);
        assertThat(grant.tenantId()).isEqualTo(TENANT_ID);
        assertThat(grant.walletId()).isEqualTo(wallet.id());
        assertThat(grant.sourceType()).isEqualTo("admin");
        assertThat(grant.sourceId()).isEqualTo("admin-topup");
        assertThat(grant.grantType()).isEqualTo("general");
        assertThat(grant.creditsTotal()).isEqualTo(120);
        assertThat(grant.creditsRemaining()).isEqualTo(120);
        assertThat(grant.expiresAt()).isNotNull();
    }

    @Test
    void tenantNameFallsBackFromUsernameToPhoneToEmailToUserId() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000abcd");
        Instant now = Instant.now();

        assertThat(CreatePersonalTenant.nameFor(
                new User(id, "eric", "+8617277889776", "eric@example.com", "hash", "zh", "active", now, now)))
                .isEqualTo("eric");
        assertThat(CreatePersonalTenant.nameFor(
                new User(id, "", "+8617277889776", "eric@example.com", "hash", "zh", "active", now, now)))
                .isEqualTo("9776");
        assertThat(CreatePersonalTenant.nameFor(
                new User(id, null, null, "eric@example.com", "hash", "zh", "active", now, now)))
                .isEqualTo("eric");
        assertThat(CreatePersonalTenant.nameFor(
                new User(id, null, null, null, "hash", "zh", "active", now, now)))
                .isEqualTo("user-0000abcd");
    }

    private CreatePersonalTenant provisioner(long initialCredits) {
        CreditGrantService grantService = new CreditGrantService(grants, wallets, wallets);
        WalletService walletService = new WalletService(wallets, wallets, wallets, grantService);
        TopUpWallet topUpWallet =
                new TopUpWallet(walletService, new BillingProperties(10, initialCredits, 200, 50), grantService);
        return new CreatePersonalTenant(tenantMapper, topUpWallet);
    }

    private static User user(String username) {
        Instant now = Instant.now();
        return new User(USER_ID, username, "+8617277889776", null, "hash", "zh", "active", now, now);
    }
}
