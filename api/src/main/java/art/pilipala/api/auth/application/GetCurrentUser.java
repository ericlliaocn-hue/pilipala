package art.pilipala.api.auth.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.IdentityMasker;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

/**
 * {@code GET /api/v1/auth/me}：当前用户 + 当前这一个租户的角色与两边的权限码。
 *
 * <p>当前租户由 {@link CurrentTenant} 统一解析（会话优先，其次个人租户规则），
 * 解析不出来时 {@code tenantId} 与 {@code tenantType} 为 null，三个数组是空数组。
 * 权限每次现查，不读会话缓存。
 */
@Service
public class GetCurrentUser {

    private static final String PERSONAL = "personal";

    private final UserRepository users;
    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;
    private final CurrentTenant currentTenant;
    private final AccessCheck access;

    public GetCurrentUser(UserRepository users, AuthSessionService sessions, AdminBootstrap adminBootstrap,
            CurrentTenant currentTenant, AccessCheck access) {
        this.users = users;
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
        this.currentTenant = currentTenant;
        this.access = access;
    }

    public CurrentUser current() {
        UUID userId;
        try {
            userId = UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
        User user = users.findById(userId).orElseThrow(AuthException::unauthorized);
        UUID tenantId = currentTenant.find(user.id()).orElse(null);
        return new CurrentUser(
                user.id(),
                IdentityMasker.maskPhone(user.phone()),
                IdentityMasker.maskEmail(user.email()),
                user.locale(),
                adminBootstrap.isPlatformStaff(user.id()) ? "admin" : "user",
                tenantId,
                tenantId == null ? null : PERSONAL,
                tenantId == null ? List.of() : access.tenantRoles(user.id(), tenantId),
                tenantId == null ? List.of() : access.tenantPermissions(user.id(), tenantId),
                access.platformPermissions(user.id()));
    }
}
