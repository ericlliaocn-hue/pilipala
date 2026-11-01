package art.pilipala.api.auth.application;

import java.util.UUID;

/**
 * 注册 / 登录 / 重置密码成功后返回的会话信息（WP17：脱敏 phone/email，不含 username）。
 *
 * <p>{@code role} 由平台角色分配推导：{@code users.role} 列已删除。
 */
public record AuthSession(
        UUID userId,
        String phone,
        String email,
        String locale,
        String token,
        String role) {
}
