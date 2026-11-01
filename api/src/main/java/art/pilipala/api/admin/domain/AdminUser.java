package art.pilipala.api.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 运维后台看到的用户行：账号信息 + 钱包余额快照。
 *
 * @param available 可用 credit；用户还没有 wallet 行时为 0
 * @param frozen    已冻未确认 credit；用户还没有 wallet 行时为 0
 */
public record AdminUser(
        UUID userId,
        String username,
        String locale,
        long available,
        long frozen,
        Instant createdAt,
        Instant updatedAt) {
}
