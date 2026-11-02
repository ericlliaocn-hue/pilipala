package art.pilipala.api.auth.application;

import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.TenantMapper;
import art.pilipala.api.billing.application.TopUpWallet;
import org.springframework.stereotype.Component;

/**
 * 注册时给用户建个人租户。
 *
 * <p>四件事一起做：{@code personal} 租户、{@code active} 成员、该租户上的 {@code tenant_owner}、
 * 挂在该租户上的钱包（含初始积分的 topup 流水）。调用方在同一个事务里执行，
 * 任一步失败时用户、租户、成员、角色分配、钱包一起回滚。
 *
 * <p>形状与 {@code V25} 的存量回填相同，只是回填不写钱包（另有一步补空钱包）。
 */
@Component
public class CreatePersonalTenant {

    static final String OWNER_ROLE = "tenant_owner";

    private final TenantMapper tenants;
    private final TopUpWallet wallets;

    public CreatePersonalTenant(TenantMapper tenants, TopUpWallet wallets) {
        this.tenants = tenants;
        this.wallets = wallets;
    }

    /** @return 新建的个人租户 id */
    public UUID create(User user) {
        UUID tenantId = tenants.insertPersonalTenant(nameFor(user));
        tenants.insertMembership(tenantId, user.id());
        tenants.insertTenantRole(user.id(), tenantId, OWNER_ROLE);
        wallets.openPersonalWallet(user.id(), tenantId);
        return tenantId;
    }

    /**
     * 租户名：优先 {@code username}（空串当没有），其次手机号后四位，
     * 再次邮箱 {@code @} 前面的部分，都没有则 {@code user-} 加用户 id 末 8 位。
     */
    static String nameFor(User user) {
        if (user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        String phone = user.phone();
        if (phone != null && !phone.isBlank()) {
            return phone.substring(Math.max(0, phone.length() - 4));
        }
        String email = user.email();
        if (email != null && !email.isBlank()) {
            int at = email.indexOf('@');
            String local = at > 0 ? email.substring(0, at) : email;
            if (!local.isBlank()) {
                return local;
            }
        }
        String id = user.id().toString();
        return "user-" + id.substring(Math.max(0, id.length() - 8));
    }
}
