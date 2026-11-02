package art.pilipala.api.auth.infra;

import java.util.Optional;
import java.util.UUID;

import cn.dev33.satoken.stp.StpUtil;
import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.application.AuthSession;
import art.pilipala.api.auth.application.PersonalTenants;
import art.pilipala.api.auth.domain.IdentityMasker;
import art.pilipala.api.auth.domain.User;
import org.springframework.stereotype.Component;

/**
 * 登录态：发令牌、记当前个人租户、读当前用户。
 *
 * <p>会话里只放当前个人租户 id，键名 {@value #TENANT_ID_KEY}。
 * 权限码不进会话：每次请求从库里读（{@code AccessCheck}）。
 * 会话存 Redis（{@code sa-token-redis-jackson}），所以存字符串而不是对象。
 *
 * <p>找不到个人租户时不在登录路径里补建，会话里就不写这个键。
 */
@Component
public class AuthSessionService {

    static final String TENANT_ID_KEY = "tenant_id";

    private final AdminBootstrap adminBootstrap;
    private final PersonalTenants personalTenants;

    public AuthSessionService(AdminBootstrap adminBootstrap, PersonalTenants personalTenants) {
        this.adminBootstrap = adminBootstrap;
        this.personalTenants = personalTenants;
    }

    public AuthSession start(User user) {
        StpUtil.login(user.id().toString());
        personalTenants.find(user.id())
                .ifPresent(tenantId -> StpUtil.getSession().set(TENANT_ID_KEY, tenantId.toString()));
        return toSession(user, StpUtil.getTokenValue());
    }

    /** 会话里的当前租户；已发出的旧令牌没有这个键时为空。 */
    public Optional<UUID> sessionTenantId() {
        String raw = StpUtil.getSession().getString(TENANT_ID_KEY);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public AuthSession toSession(User user, String token) {
        return new AuthSession(
                user.id(),
                IdentityMasker.maskPhone(user.phone()),
                IdentityMasker.maskEmail(user.email()),
                user.locale(),
                token,
                adminBootstrap.isPlatformStaff(user.id()) ? "admin" : "user");
    }

    public String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    public void end() {
        StpUtil.logout();
    }
}
