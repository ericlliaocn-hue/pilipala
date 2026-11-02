package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 冻结单：一次 job 对应唯一一张 {@code wallet_hold}，也是 {@code job.hold_id}。
 *
 * <p>{@code jobId} 在冻结时先为空，job 行落库后再回填（避免与 job 表互指外键的死结）。
 * 终态只进一次：frozen → confirmed（实扣）或 frozen → released（解冻）。
 *
 * @param id         主键
 * @param tenantId   被冻结的钱包所属租户
 * @param userId     操作者（当前登录用户）
 * @param jobId      关联任务；未绑定时为 null
 * @param amount     冻结额，恒为正整数
 * @param status     冻结单状态
 * @param createdAt  创建时间
 * @param finalizedAt 终态时间；未终结时为 null
 */
public record WalletHold(
        UUID id,
        UUID tenantId,
        UUID userId,
        UUID jobId,
        long amount,
        WalletHoldStatus status,
        Instant createdAt,
        Instant finalizedAt) {
}
