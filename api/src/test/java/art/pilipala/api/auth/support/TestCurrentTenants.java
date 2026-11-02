package art.pilipala.api.auth.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.application.PersonalTenants;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.TenantMapper;

/**
 * 单测用的当前租户：会话里不带 {@code tenant_id}，一律回落到用户的个人租户。
 *
 * <p>映射表里没有的用户就是「没有当前租户」，{@code require} 直接 {@code FORBIDDEN}，
 * 与线上规则一致；映射到的租户都算 {@code active}。
 */
public final class TestCurrentTenants {

    private TestCurrentTenants() {
    }

    public static CurrentTenant forTenants(Map<UUID, UUID> tenantByUser) {
        TenantMapper mapper = mock(TenantMapper.class);
        when(mapper.findPersonalTenantId(any())).thenAnswer(call -> tenantByUser.get(call.<UUID>getArgument(0)));
        when(mapper.countActiveTenant(any())).thenReturn(1);
        AuthSessionService sessions = mock(AuthSessionService.class);
        when(sessions.sessionTenantId()).thenReturn(Optional.empty());
        return new CurrentTenant(sessions, new PersonalTenants(mapper), mapper);
    }

    public static CurrentTenant forUser(UUID userId, UUID tenantId) {
        return forTenants(Map.of(userId, tenantId));
    }
}
