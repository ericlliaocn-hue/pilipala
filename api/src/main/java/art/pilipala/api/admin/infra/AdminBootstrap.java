package art.pilipala.api.admin.infra;

import java.util.UUID;

import org.springframework.stereotype.Component;

import art.pilipala.api.auth.domain.User;

/**
 * 平台侧的角色引导。
 *
 * <p>{@code users.role} 已删除：运维身份改由 {@code role_assignment} 表达。
 *
 * <p>引导规则：系统里还没有任何 {@code platform_owner} 时，本次登录身份命中名单就授成
 * {@code platform_owner}；已经有所有者之后，名单不再自动升权。
 */
@Component
public class AdminBootstrap {

    private final AdminProperties properties;
    private final PlatformRoleMapper platformRoles;

    public AdminBootstrap(AdminProperties properties, PlatformRoleMapper platformRoles) {
        this.properties = properties;
        this.platformRoles = platformRoles;
    }

    /**
     * 必要时把当前身份引导成平台所有者。
     *
     * @return 原用户对象；角色不再挂在用户身上，所以没有需要回填的字段
     */
    public User ensureOwner(User user) {
        if (!shouldPromote(user)) {
            return user;
        }
        if (platformRoles.countPlatformOwners() > 0) {
            return user;
        }
        platformRoles.insertPlatformOwner(user.id());
        return user;
    }

    /** 是不是平台员工：持有平台范围的角色分配。 */
    public boolean isPlatformStaff(UUID userId) {
        return platformRoles.countPlatformRoles(userId) > 0;
    }

    private boolean shouldPromote(User user) {
        if (user.username() != null && properties.isBootstrapAdminUsername(user.username())) {
            return true;
        }
        if (user.phone() != null && properties.isBootstrapAdminPhone(user.phone())) {
            return true;
        }
        return user.email() != null && properties.isBootstrapAdminEmail(user.email());
    }
}
