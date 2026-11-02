package art.pilipala.api.billing.application;

import java.time.Instant;

/**
 * 钱包快照。字段与 docs/engineering/api/gateway-contract.md 的 Wallet 小节一致。
 *
 * @param userId    当前登录用户（余额属于其租户）
 * @param available 可用 credit
 * @param frozen    已冻未确认 credit
 * @param updatedAt 最后一次余额变动时间
 */
public record WalletView(
        String userId,
        long available,
        long frozen,
        Instant updatedAt,
        java.util.List<WalletGrantSummaryView> grants) {

    public WalletView(String userId, long available, long frozen, Instant updatedAt) {
        this(userId, available, frozen, updatedAt, java.util.List.of());
    }
}
