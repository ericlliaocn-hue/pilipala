package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 钱包余额行。列与 docs/engineering/data-model/billing.md 一致。
 *
 * <p>不变量：{@code available >= 0} 且 {@code frozen >= 0}。余额只经由
 * 充值 / 冻结 / 确认 / 释放四条路径变动，每条路径都写一条 wallet_ledger 流水。
 *
 * @param id        主键
 * @param tenantId  余额主人，一租户一钱包
 * @param available 可用 credit
 * @param frozen    已冻未确认 credit
 * @param updatedAt 最后一次变动时间
 */
public record Wallet(
        UUID id,
        UUID tenantId,
        long available,
        long frozen,
        Instant updatedAt) {
}
