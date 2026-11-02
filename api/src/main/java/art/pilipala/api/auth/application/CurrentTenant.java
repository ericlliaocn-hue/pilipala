package art.pilipala.api.auth.application;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.TenantMapper;
import org.springframework.stereotype.Component;

/**
 * C 端的当前租户，唯一取法。
 *
 * <p>会话里有 {@code tenant_id} 就用它，没有就按 {@link PersonalTenants} 的规则从库里取
 * （active 成员、{@code type = personal}、租户 active，最早一条）。
 * 不在请求里补建租户，也不要求重新登录。
 *
 * <p>请求的 query 或 JSON 里另带 {@code tenant_id} 时必须等于当前租户，否则
 * {@code FORBIDDEN}；没带是正常情况。没有当前租户同样 {@code FORBIDDEN}，不新造错误码。
 */
@Component
public class CurrentTenant {

    private final AuthSessionService sessions;
    private final PersonalTenants personalTenants;
    private final TenantMapper tenants;

    public CurrentTenant(AuthSessionService sessions, PersonalTenants personalTenants, TenantMapper tenants) {
        this.sessions = sessions;
        this.personalTenants = personalTenants;
        this.tenants = tenants;
    }

    /** 当前租户；解析不出来时为空。 */
    public Optional<UUID> find(UUID userId) {
        return sessions.sessionTenantId().or(() -> personalTenants.find(userId));
    }

    /** 当前租户，没有则 {@code FORBIDDEN}。 */
    public UUID require(UUID userId) {
        return require(userId, null);
    }

    /**
     * 当前租户，并校验请求里另带的 {@code requestedTenantId}。
     *
     * @param requestedTenantId 请求 query 或 JSON 里的 {@code tenant_id}；为空表示没带
     * @throws AuthException 没有当前租户，或带的值不等于当前租户（均为 {@code FORBIDDEN}）
     */
    public UUID require(UUID userId, String requestedTenantId) {
        UUID tenantId = find(userId).orElseThrow(AuthException::forbidden);
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.trim().equals(tenantId.toString())) {
            throw AuthException.forbidden();
        }
        return tenantId;
    }

    /**
     * 当前租户，且该租户必须 {@code active}，否则 {@code FORBIDDEN}。
     *
     * <p>写钱、写任务的路径用这个：会话里的 {@code tenant_id} 可能指向后来被停用的租户。
     */
    public UUID requireActive(UUID userId, String requestedTenantId) {
        UUID tenantId = require(userId, requestedTenantId);
        if (tenants.countActiveTenant(tenantId) == 0) {
            throw AuthException.forbidden();
        }
        return tenantId;
    }
}
