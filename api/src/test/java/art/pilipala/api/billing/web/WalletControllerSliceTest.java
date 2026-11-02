package art.pilipala.api.billing.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.billing.application.GetWallet;
import art.pilipala.api.billing.application.WalletView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C 端钱包接口的切片测试：信封、snake_case 字段。
 *
 * <p>鉴权闸门（SaTokenConfig）在这个切片里被排除掉：会话由 mock 的
 * {@link AuthSessionService} 提供，只测控制器与信封这一层。
 *
 * <p>余额按当前租户查，响应里的 {@code user_id} 仍是登录用户。
 */
@WebMvcTest(controllers = WalletController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
class WalletControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private CurrentTenant currentTenant;

    @MockitoBean
    private GetWallet getWallet;

    @Test
    void walletReturnsTheSnapshot() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER, null)).thenReturn(TENANT);
        when(getWallet.get(USER, TENANT))
                .thenReturn(new WalletView(USER.toString(), 90, 10, Instant.parse("2026-10-24T04:30:00Z")));

        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.error_code").doesNotExist())
                .andExpect(jsonPath("$.data.user_id").value(USER.toString()))
                .andExpect(jsonPath("$.data.available").value(90))
                .andExpect(jsonPath("$.data.frozen").value(10))
                .andExpect(jsonPath("$.data.updated_at").value("2026-10-24T04:30:00Z"));
    }

    /** 查询参数带上别人的租户：FORBIDDEN，不返回任何余额。 */
    @Test
    void anotherTenantInTheQueryIsForbidden() throws Exception {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(currentTenant.require(USER, "44444444-4444-4444-4444-444444444444"))
                .thenThrow(AuthException.forbidden());

        mockMvc.perform(get("/api/v1/wallet?tenant_id=44444444-4444-4444-4444-444444444444"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }
}
