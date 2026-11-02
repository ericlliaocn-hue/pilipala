package art.pilipala.api.auth.application;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.TenantMapper;
import org.springframework.stereotype.Component;

/**
 * 用户的个人租户。
 *
 * <p>当前个人租户只认「{@code active} 成员 + {@code type = personal} + 租户 {@code active}」
 * 里最早的一条，与管理端用户列表（{@code AdminUserMapper}）的取法一致。
 *
 * <p>登录时找不到个人租户就保持为空，不在登录路径里补建；
 * 账本与批次必须挂租户，{@link #require} 找不到就失败，也不在加额路径里补建。
 */
@Component
public class PersonalTenants {

    private final TenantMapper tenants;

    public PersonalTenants(TenantMapper tenants) {
        this.tenants = tenants;
    }

    public Optional<UUID> find(UUID userId) {
        return Optional.ofNullable(tenants.findPersonalTenantId(userId));
    }

    /** 该用户的个人租户；没有就失败（不补建）。 */
    public UUID require(UUID userId) {
        return find(userId).orElseThrow(AuthException::validation);
    }
}
