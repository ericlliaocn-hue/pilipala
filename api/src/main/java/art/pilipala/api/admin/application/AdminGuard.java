package art.pilipala.api.admin.application;

import java.util.UUID;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import org.springframework.stereotype.Component;

/**
 * 运维后台的权限闸门。
 *
 * <p>登录校验由 WP3 的 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}：
 * 未登录时已经在闸门外被拦掉（UNAUTHORIZED）。这里只做第二道：
 * 已登录但不是平台员工的用户一律拒绝（FORBIDDEN）。
 *
 * <p>{@code users.role} 已删除，运维身份改看平台角色分配；每次都从库里读：
 * 降权后下一次请求就失效，不依赖会话里的旧值。
 */
@Component
public class AdminGuard {

    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;

    public AdminGuard(AuthSessionService sessions, AdminBootstrap adminBootstrap) {
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
    }

    /**
     * 要求当前登录者是平台员工，否则抛异常。
     *
     * @throws AuthException 未登录（UNAUTHORIZED）或不是平台员工（FORBIDDEN）
     */
    public void requireAdmin() {
        UUID userId = currentUserId();
        if (!adminBootstrap.isPlatformStaff(userId)) {
            throw AuthException.forbidden();
        }
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }
}
