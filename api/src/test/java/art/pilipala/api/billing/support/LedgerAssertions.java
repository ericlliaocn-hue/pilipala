package art.pilipala.api.billing.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;

/**
 * 账本不变量的断言。
 *
 * <p>不变量来自 docs/engineering/data-model/billing.md：
 * available &gt;= 0、frozen &gt;= 0；余额能被流水完全解释；
 * frozen 等于还没结算的冻结单之和；每张冻结单最多 1 条 freeze 流水、
 * 最多 1 条 confirm 或 release 流水。
 */
public final class LedgerAssertions {

    private LedgerAssertions() {
    }

    /** 余额与流水都按租户的钱包核对；流水按 {@code wallet_id} 归集，不看操作者。 */
    public static void assertInvariants(InMemoryWalletStore store, UUID tenantId) {
        Wallet wallet = store.walletOf(tenantId);
        List<WalletLedgerEntry> entries = store.allLedger().stream()
                .filter(entry -> entry.walletId().equals(wallet.id()))
                .toList();

        long topup = sum(entries, WalletLedgerType.TOPUP);
        long freeze = sum(entries, WalletLedgerType.FREEZE);
        long confirm = sum(entries, WalletLedgerType.CONFIRM);
        long release = sum(entries, WalletLedgerType.RELEASE);
        long frozenHolds = store.allHolds().stream()
                .filter(hold -> hold.tenantId().equals(tenantId) && hold.status() == WalletHoldStatus.FROZEN)
                .mapToLong(WalletHold::amount)
                .sum();

        assertThat(wallet.available()).isEqualTo(topup - freeze + release);
        assertThat(wallet.frozen()).isEqualTo(freeze - confirm - release);
        assertThat(wallet.frozen()).isEqualTo(frozenHolds);
        assertThat(wallet.available()).isGreaterThanOrEqualTo(0);
        assertThat(wallet.frozen()).isGreaterThanOrEqualTo(0);

        for (WalletHold hold : store.allHolds()) {
            List<WalletLedgerEntry> forHold = entries.stream()
                    .filter(entry -> hold.id().equals(entry.holdId()))
                    .toList();
            long freezeCount = forHold.stream().filter(entry -> entry.type() == WalletLedgerType.FREEZE).count();
            long settleCount = forHold.stream()
                    .filter(entry -> entry.type() == WalletLedgerType.CONFIRM || entry.type() == WalletLedgerType.RELEASE)
                    .count();
            assertThat(freezeCount).isLessThanOrEqualTo(1);
            assertThat(settleCount).isLessThanOrEqualTo(1);
        }
    }

    private static long sum(List<WalletLedgerEntry> entries, WalletLedgerType type) {
        return entries.stream().filter(entry -> entry.type() == type).mapToLong(WalletLedgerEntry::amount).sum();
    }
}
