package art.pilipala.api.admin.application;

import java.time.Instant;
import java.util.UUID;

import art.pilipala.api.admin.domain.AdminUser;

/**
 * 运维后台的用户行：账号信息 + 钱包余额快照。
 *
 * <p>UUID 字段由 Jackson 直接序列化成字符串（{@code user_id}）。
 */
public record AdminUserView(
        UUID userId,
        String username,
        String locale,
        long available,
        long frozen,
        Instant createdAt,
        Instant updatedAt) {

    /** 领域对象 → 视图。 */
    public static AdminUserView of(AdminUser user) {
        return new AdminUserView(user.userId(), user.username(), user.locale(),
                user.available(), user.frozen(), user.createdAt(), user.updatedAt());
    }
}
