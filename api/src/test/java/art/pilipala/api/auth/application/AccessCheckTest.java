package art.pilipala.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.auth.infra.TenantMapper;
import org.junit.jupiter.api.Test;

/**
 * 权限判定的两个范围不会串：{@code job.read} 在平台和租户各有一行，
 * 平台角色给的 {@code job.read} 不能当租户权限用，反之亦然。
 */
class AccessCheckTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final TenantMapper tenants = mock(TenantMapper.class);
    private final AccessCheck access = new AccessCheck(tenants);

    @Test
    void aPlatformJobReadIsNotATenantJobRead() {
        when(tenants.findPlatformPermissions(USER)).thenReturn(List.of("job.read", "user.read"));
        when(tenants.findTenantPermissions(USER, TENANT)).thenReturn(List.of("job.submit"));

        assertThat(access.hasPlatform(USER, "job.read")).isTrue();
        assertThat(access.hasTenant(USER, TENANT, "job.read")).isFalse();
        assertThat(access.hasTenant(USER, TENANT, "job.submit")).isTrue();
        assertThat(access.hasPlatform(USER, "job.submit")).isFalse();
    }

    @Test
    void aTenantJobReadIsNotAPlatformJobRead() {
        when(tenants.findPlatformPermissions(USER)).thenReturn(List.of());
        when(tenants.findTenantPermissions(USER, TENANT)).thenReturn(List.of("job.read"));

        assertThat(access.hasPlatform(USER, "job.read")).isFalse();
        assertThat(access.hasTenant(USER, TENANT, "job.read")).isTrue();
    }

    @Test
    void noTenantMeansNoTenantPermission() {
        assertThat(access.hasTenant(USER, null, "job.read")).isFalse();
        verifyNoInteractions(tenants);
    }
}
