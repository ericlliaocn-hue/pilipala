package art.pilipala.api.auth.application;

import java.util.UUID;

/** 当前登录用户（WP17：脱敏身份，省略 username）。role 由平台角色分配推导，不是 users 表的列。 */
public record CurrentUser(UUID userId, String phone, String email, String locale, String role) {
}
