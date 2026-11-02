package art.pilipala.api.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户聚合。列与 docs/engineering/data-model/README.md 的 users（WP17）一致；
 * {@code status} 由 M4 的 {@code V24} 加入。
 */
public record User(
        UUID id,
        String username,
        String phone,
        String email,
        String passwordHash,
        String locale,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DISABLED = "disabled";

    /** 停用账号不能登录，也不能重置密码。 */
    public boolean disabled() {
        return STATUS_DISABLED.equals(status);
    }
}
