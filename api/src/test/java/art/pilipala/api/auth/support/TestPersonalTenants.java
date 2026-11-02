package art.pilipala.api.auth.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import art.pilipala.api.auth.application.PersonalTenants;
import art.pilipala.api.auth.infra.TenantMapper;

/**
 * 账本 / 计费单测不需要真库：任何用户都给同一只个人租户。
 *
 * <p>步骤 3 之前，钱包按 {@code user_id} 查的路径本身还是断的（{@code wallet.user_id}
 * 已经删掉），所以这里只关心「流水与批次有没有带上租户」，不模拟钱包查找。
 */
public final class TestPersonalTenants {

    public static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private TestPersonalTenants() {
    }

    public static PersonalTenants anyUser() {
        return anyUser(TENANT);
    }

    public static PersonalTenants anyUser(UUID tenantId) {
        TenantMapper mapper = mock(TenantMapper.class);
        when(mapper.findPersonalTenantId(any())).thenReturn(tenantId);
        return new PersonalTenants(mapper);
    }
}
