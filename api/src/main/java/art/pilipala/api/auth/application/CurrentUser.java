package art.pilipala.api.auth.application;

import java.util.List;
import java.util.UUID;

/**
 * 当前登录用户（WP17：脱敏身份，省略 username）。
 *
 * <p>{@code role} 由平台角色分配推导，不是 users 表的列；创作站暂时还在读它。
 * 租户侧的 {@code tenantId} / {@code tenantRoles} / {@code tenantPermissions} 只描述
 * 当前这一个租户，不返回其他租户；权限每次从库里查，不读会话缓存。
 * 没有当前租户时 {@code tenantId} 与 {@code tenantType} 为 null，三个数组是空数组。
 */
public record CurrentUser(
        UUID userId,
        String phone,
        String email,
        String locale,
        String role,
        UUID tenantId,
        String tenantType,
        List<String> tenantRoles,
        List<String> tenantPermissions,
        List<String> platformPermissions) {
}
