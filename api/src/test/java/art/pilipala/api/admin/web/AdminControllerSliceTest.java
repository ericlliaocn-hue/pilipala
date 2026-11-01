package art.pilipala.api.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminJobView;
import art.pilipala.api.admin.application.AdminLedgerEntryView;
import art.pilipala.api.admin.application.AdminUserView;
import art.pilipala.api.admin.application.I18nMessageView;
import art.pilipala.api.admin.application.ListAdminJobs;
import art.pilipala.api.admin.application.ListAdminUsers;
import art.pilipala.api.admin.application.ListEnabledModels;
import art.pilipala.api.admin.application.ListI18nMessages;
import art.pilipala.api.admin.application.ListModelDisplayConfig;
import art.pilipala.api.admin.application.ListWalletLedger;
import art.pilipala.api.admin.application.ModelDisplayConfigView;
import art.pilipala.api.admin.application.RetryAdminJob;
import art.pilipala.api.admin.application.TopUpUserWallet;
import art.pilipala.api.admin.application.UpdateModelDisplayConfig;
import art.pilipala.api.admin.application.UpsertI18nMessage;
import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.billing.application.WalletView;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.ModelCatalogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 运维后台接口的切片测试：权限闸门、信封、snake_case 字段、上游原文只在 Admin 可见。
 *
 * <p>鉴权闸门（SaTokenConfig）在这个切片里被排除掉：会话由 mock 的
 * {@link AuthSessionService} 提供，只测控制器与信封这一层。
 */
@WebMvcTest(controllers = { AdminUserController.class, AdminWalletController.class, AdminJobController.class,
        AdminI18nController.class, AdminModelController.class, ModelController.class, AdminVideoController.class },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
@Import(AdminGuard.class)
class AdminControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private AdminBootstrap adminBootstrap;

    @MockitoBean
    private ListAdminUsers listAdminUsers;

    @MockitoBean
    private TopUpUserWallet topUpUserWallet;

    @MockitoBean
    private ListWalletLedger listWalletLedger;

    @MockitoBean
    private ListAdminJobs listAdminJobs;

    @MockitoBean
    private RetryAdminJob retryAdminJob;

    @MockitoBean
    private ListI18nMessages listI18nMessages;

    @MockitoBean
    private UpsertI18nMessage upsertI18nMessage;

    @MockitoBean
    private ListEnabledModels listEnabledModels;

    @MockitoBean
    private ListModelDisplayConfig listModelDisplayConfig;

    @MockitoBean
    private UpdateModelDisplayConfig updateModelDisplayConfig;

    @MockitoBean
    private ModelCatalogRepository modelCatalogRepository;

    // ----- 权限闸门 -----

    @Test
    void aNormalUserIsForbidden() throws Exception {
        loginAs(false);

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("无权访问管理后台。"));
    }

    @Test
    void aNormalUserIsForbiddenOnEveryAdminRoute() throws Exception {
        loginAs(false);

        mockMvc.perform(get("/api/v1/admin/jobs"))
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/admin/jobs/" + JOB_ID + "/retry"))
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(put("/api/v1/admin/i18n-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"K","locale":"zh","template":"t"}"""))
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/models"))
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void withoutASessionTheAnswerIsUnauthorized() throws Exception {
        when(sessions.currentUserId()).thenReturn("not-a-uuid");

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }

    // ----- 用户与钱包 -----

    @Test
    void anAdminGetsTheUserList() throws Exception {
        loginAs(true);
        when(listAdminUsers.list(any(), any(Integer.class)))
                .thenReturn(List.of(new AdminUserView(USER, "demo", "zh", 120L, 30L,
                        Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-24T01:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/users").param("q", "dem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].user_id").value(USER.toString()))
                .andExpect(jsonPath("$.data[0].username").value("demo"))
                .andExpect(jsonPath("$.data[0].available").value(120))
                .andExpect(jsonPath("$.data[0].frozen").value(30));
    }

    @Test
    void topUpReturnsTheWalletSnapshot() throws Exception {
        loginAs(true);
        when(topUpUserWallet.topUp(eq(USER), eq(100L)))
                .thenReturn(new WalletView(USER.toString(), 220L, 0L, Instant.parse("2026-10-24T00:00:00Z")));

        mockMvc.perform(post("/api/v1/admin/wallets/" + USER + "/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.user_id").value(USER.toString()))
                .andExpect(jsonPath("$.data.available").value(220));
    }

    @Test
    void topUpWithANonPositiveAmountUsesTheEnvelope() throws Exception {
        loginAs(true);
        when(topUpUserWallet.topUp(eq(USER), eq(0L))).thenThrow(JobException.validation());

        mockMvc.perform(post("/api/v1/admin/wallets/" + USER + "/topup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":0}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
    }

    @Test
    void theLedgerIsListedForOneUser() throws Exception {
        loginAs(true);
        when(listWalletLedger.list(eq(USER), any(Integer.class)))
                .thenReturn(List.of(new AdminLedgerEntryView(UUID.randomUUID(), USER, "topup", 100L, null, null,
                        Instant.parse("2026-10-24T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/wallets/" + USER + "/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].type").value("topup"))
                .andExpect(jsonPath("$.data[0].amount").value(100));
    }

    // ----- 任务排障 -----

    @Test
    void theUpstreamRawTextOnlyShowsUpInTheAdminResponse() throws Exception {
        loginAs(true);
        when(listAdminJobs.list(any(), any(), any(Integer.class)))
                .thenReturn(List.of(new AdminJobView(JOB_ID, USER, "demo", "dead", "image.generate", "gpt-image-2",
                        "a cat", 3, 3, null, null, "PROVIDER_UNAVAILABLE",
                        Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-24T01:00:00Z"),
                        List.of(new AdminJobView.Step(UUID.randomUUID(), 3, "failed", "PROVIDER_UNAVAILABLE", null,
                                Instant.parse("2026-10-24T00:10:00Z"), Instant.parse("2026-10-24T00:20:00Z"))),
                        List.of(new AdminJobView.ProviderTaskView(UUID.randomUUID(), null, "req-1", "500",
                                null, Instant.parse("2026-10-24T00:15:00Z"))))));

        mockMvc.perform(get("/api/v1/admin/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].prompt").value("a cat"))
                .andExpect(jsonPath("$.data[0].steps[0].attempt").value(3))
                .andExpect(jsonPath("$.data[0].provider_tasks[0].raw_status").value("500"))
                .andExpect(jsonPath("$.data[0].provider_tasks[0].upstream_request_id").value("req-1"));
    }

    @Test
    void retryReturnsTheQueuedJob() throws Exception {
        loginAs(true);
        when(retryAdminJob.retry(eq(JOB_ID)))
                .thenReturn(new AdminJobView(JOB_ID, USER, "demo", "queued", "image.generate", "gpt-image-2",
                        "a cat", 3, 4, null, null, null,
                        Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-24T02:00:00Z"),
                        List.of(), List.of()));

        mockMvc.perform(post("/api/v1/admin/jobs/" + JOB_ID + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("queued"))
                .andExpect(jsonPath("$.data.max_attempts").value(4));
    }

    // ----- 动态文案 -----

    @Test
    void anI18nMessageCanBeUpdated() throws Exception {
        loginAs(true);
        when(listI18nMessages.list(any()))
                .thenReturn(List.of(new I18nMessageView(UUID.randomUUID(), "WALLET_INSUFFICIENT", "zh",
                        "余额不足，请联系管理员充值。", Instant.parse("2026-10-24T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/admin/i18n-messages").param("q", "WALLET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("WALLET_INSUFFICIENT"))
                .andExpect(jsonPath("$.data[0].locale").value("zh"));
    }

    // ----- 模型配置 -----

    @Test
    void theModelConfigIsListedAndUpdated() throws Exception {
        loginAs(true);
        when(listModelDisplayConfig.list())
                .thenReturn(List.of(new ModelDisplayConfigView(UUID.randomUUID(), "gpt-image-2", "image.generate",
                        "GPT Image 2", true, 10, Instant.parse("2026-10-24T00:00:00Z"))));
        when(updateModelDisplayConfig.update(eq("gpt-image-2"), eq("GPT Image 2"), eq("image.generate"), eq(false),
                eq(10)))
                .thenReturn(new ModelDisplayConfigView(UUID.randomUUID(), "gpt-image-2", "image.generate", "GPT Image 2",
                        false, 10, Instant.parse("2026-10-24T01:00:00Z")));

        mockMvc.perform(get("/api/v1/admin/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].model").value("gpt-image-2"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));

        mockMvc.perform(put("/api/v1/admin/models/gpt-image-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"display_name":"GPT Image 2","capability":"image.generate","enabled":false,"sort_order":10}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    // ----- C 端模型列表 -----

    @Test
    void theClientOnlySeesTheEnabledModels() throws Exception {
        when(listEnabledModels.list()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    // ----- 测试辅助 -----

    // ----- 视频产品 / target 只读 -----

    @Test
    void anAdminCanReadVideoProductsAndGroups() throws Exception {
        loginAs(true);
        ModelProduct product = new ModelProduct(UUID.randomUUID(), "video-stable", "video.generate",
                "Video", "test", 30, "{}", true, 10);
        ModelProductGroup group = new ModelProductGroup(UUID.randomUUID(), product.id(), "default", "sd-2-c4",
                10, 900, new BigDecimal("2.0"), true);
        when(modelCatalogRepository.findAllProducts()).thenReturn(List.of(product));
        when(modelCatalogRepository.findAllGroups()).thenReturn(List.of(group));

        mockMvc.perform(get("/api/v1/admin/video/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].code").value("video-stable"))
                .andExpect(jsonPath("$.data[0].price_credits").value(30));
        mockMvc.perform(get("/api/v1/admin/video/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].group_code").value("default"))
                .andExpect(jsonPath("$.data[0].model_code").value("sd-2-c4"))
                .andExpect(jsonPath("$.data[0].timeout_seconds").value(900));
    }

    @Test
    void aNormalUserCannotReadVideoProducts() throws Exception {
        loginAs(false);

        mockMvc.perform(get("/api/v1/admin/video/products"))
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    private void loginAs(boolean platformStaff) {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        when(adminBootstrap.isPlatformStaff(USER)).thenReturn(platformStaff);
    }
}
