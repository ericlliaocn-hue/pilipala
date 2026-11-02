package art.pilipala.api.auth.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.auth.infra.TenantMapper;
import org.springframework.stereotype.Component;

/**
 * 权限判定。
 *
 * <p>平台与租户是两个范围，{@code job.read} 在两边各有一行：平台角色上的 {@code job.read}
 * 不能让 {@link #hasTenant} 返回真，租户角色上的也不能让 {@link #hasPlatform} 返回真。
 * 两个范围由 SQL 的 {@code scope} 条件分开，不靠调用方自觉。
 *
 * <p>每次都查库，不读会话缓存：降权后下一次请求就失效。
 */
@Component
public class AccessCheck {

    private final TenantMapper tenants;

    public AccessCheck(TenantMapper tenants) {
        this.tenants = tenants;
    }

    /** 是否持有平台范围的该权限码。 */
    public boolean hasPlatform(UUID userId, String code) {
        return code != null && platformPermissions(userId).contains(code);
    }

    /** 是否在该租户上持有租户范围的该权限码（成员与租户都要是 active）。 */
    public boolean hasTenant(UUID userId, UUID tenantId, String code) {
        return code != null && tenantId != null && tenantPermissions(userId, tenantId).contains(code);
    }

    public List<String> platformPermissions(UUID userId) {
        return tenants.findPlatformPermissions(userId);
    }

    public List<String> tenantPermissions(UUID userId, UUID tenantId) {
        return tenants.findTenantPermissions(userId, tenantId);
    }

    /** 该租户上的角色码。 */
    public List<String> tenantRoles(UUID userId, UUID tenantId) {
        return tenants.findTenantRoleCodes(userId, tenantId);
    }
}
