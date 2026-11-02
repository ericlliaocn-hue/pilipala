package art.pilipala.api.admin.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.AuditLogView;
import art.pilipala.api.admin.application.AdminConsoleViews.BannerView;
import art.pilipala.api.admin.application.AdminConsoleViews.DictionaryView;
import art.pilipala.api.admin.application.AdminConsoleViews.PermissionView;
import art.pilipala.api.admin.application.AdminConsoleViews.PhoneRevealView;
import art.pilipala.api.admin.application.AdminConsoleViews.RoleView;
import art.pilipala.api.admin.application.AdminConsoleViews.StaffView;
import art.pilipala.api.admin.application.AdminConsoleViews.StatsExceptionView;
import art.pilipala.api.admin.application.AdminConsoleViews.StatsOverviewView;
import art.pilipala.api.admin.application.AdminConsoleViews.SystemStatusView;
import art.pilipala.api.admin.application.AdminConsoleViews.TenantMemberView;
import art.pilipala.api.admin.application.AdminConsoleViews.TenantView;
import art.pilipala.api.admin.application.AdminConsoleViews.UserStatusView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminSystemStatus;
import art.pilipala.api.admin.application.CancelAdminJob;
import art.pilipala.api.admin.application.ListAdminJobs;
import art.pilipala.api.admin.application.ListAdminUsers;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.admin.application.RetryAdminJob;
import art.pilipala.api.admin.infra.AuditLogMapper;
import art.pilipala.api.auth.application.AccessCheck;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.job.application.JobView;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.ModelCatalogRepository;
import art.pilipala.api.settings.application.RuntimeSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 步骤 4 新管理接口的切片测试：每个新路由都验「有权限成功、无权限 FORBIDDEN」。
 * 用例层（校验、SQL、审计）由 ManageAdminConsole 的服务测试与既有 AdminOpsTest 覆盖，
 * 这里只测控制器、权限码、信封与 CSV 形状。
 */
@WebMvcTest(controllers = { AdminRoleController.class, AdminStaffController.class, AdminTenantController.class,
        AdminUserController.class, AdminJobController.class, AdminBannerController.class,
        PublicBannerController.class, AdminDictionaryController.class, AdminRuntimeSettingsController.class,
        AdminVideoController.class, AdminStatsController.class, AdminStatusController.class,
        AdminAuditController.class, AdminExportController.class },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
@Import(AdminGuard.class)
class AdminConsoleControllerSliceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID BANNER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final String RANGE = "from=2026-10-01T00:00:00Z&to=2026-10-31T00:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionService sessions;

    @MockitoBean
    private AccessCheck access;

    @MockitoBean
    private ManageAdminConsole console;

    @MockitoBean
    private ListAdminUsers listAdminUsers;

    @MockitoBean
    private ListAdminJobs listAdminJobs;

    @MockitoBean
    private RetryAdminJob retryAdminJob;

    @MockitoBean
    private CancelAdminJob cancelAdminJob;

    @MockitoBean
    private AdminSystemStatus systemStatus;

    @MockitoBean
    private RuntimeSettingService runtimeSettings;

    @MockitoBean
    private AuditLogMapper auditLog;

    @MockitoBean
    private ModelCatalogRepository catalog;

    // ----- 角色与权限 -----

    @Test
    void permissionsNeedRoleRead() throws Exception {
        when(console.listPermissions()).thenReturn(List.of(new PermissionView("role.read", "platform", "查看角色")));
        assertForbidden(get("/api/v1/admin/permissions"));
        loginWith("role.read");
        mockMvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data[0].code").value("role.read"))
                .andExpect(jsonPath("$.data[0].scope").value("platform"));
    }

    @Test
    void rolesNeedRoleRead() throws Exception {
        when(console.listRoles()).thenReturn(List.of(roleView()));
        assertForbidden(get("/api/v1/admin/roles"));
        loginWith("role.read");
        mockMvc.perform(get("/api/v1/admin/roles"))
                .andExpect(jsonPath("$.data[0].code").value("ops"))
                .andExpect(jsonPath("$.data[0].system").value(false))
                .andExpect(jsonPath("$.data[0].permissions[0]").value("role.read"));
    }

    @Test
    void createRoleNeedsRoleWrite() throws Exception {
        when(console.createRole(eq(USER), eq("ops"), eq("运营"), eq("platform"), any()))
                .thenReturn(roleView());
        MockHttpServletRequestBuilder call = post("/api/v1/admin/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ops\",\"name\":\"运营\",\"scope\":\"platform\","
                        + "\"permission_codes\":[\"role.read\"]}");
        assertForbidden(call);
        loginWith("role.write");
        mockMvc.perform(call)
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.code").value("ops"));
    }

    @Test
    void updateRolePermissionsNeedsRoleWrite() throws Exception {
        when(console.updateRolePermissions(eq(USER), eq(ROLE_ID), any())).thenReturn(roleView());
        MockHttpServletRequestBuilder call = put("/api/v1/admin/roles/" + ROLE_ID + "/permissions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permission_codes\":[\"role.read\"]}");
        assertForbidden(call);
        loginWith("role.write");
        mockMvc.perform(call).andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void deleteRoleNeedsRoleWrite() throws Exception {
        when(console.deleteRole(eq(USER), eq(ROLE_ID))).thenReturn(roleView());
        assertForbidden(delete("/api/v1/admin/roles/" + ROLE_ID));
        loginWith("role.write");
        mockMvc.perform(delete("/api/v1/admin/roles/" + ROLE_ID))
                .andExpect(jsonPath("$.ok").value(true));
    }

    // ----- 员工 -----

    @Test
    void staffNeedsStaffRead() throws Exception {
        when(console.listStaff()).thenReturn(List.of(
                new StaffView(TARGET, "demo", "active", List.of("platform_owner"))));
        assertForbidden(get("/api/v1/admin/staff"));
        loginWith("staff.read");
        mockMvc.perform(get("/api/v1/admin/staff"))
                .andExpect(jsonPath("$.data[0].role_codes[0]").value("platform_owner"));
    }

    @Test
    void replaceStaffRolesNeedsAssignRole() throws Exception {
        when(console.replaceStaffRoles(eq(USER), eq(TARGET), any()))
                .thenReturn(new StaffView(TARGET, "demo", "active", List.of("platform_support")));
        MockHttpServletRequestBuilder call = put("/api/v1/admin/staff/" + TARGET + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role_codes\":[\"platform_support\"]}");
        assertForbidden(call);
        loginWith("staff.assign_role");
        mockMvc.perform(call)
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.role_codes[0]").value("platform_support"));
    }

    // ----- 租户 -----

    @Test
    void tenantsNeedTenantRead() throws Exception {
        when(console.listTenants(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new TenantView(TENANT_ID, "personal", "demo", "active", NOW, NOW)));
        assertForbidden(get("/api/v1/admin/tenants"));
        loginWith("tenant.read");
        mockMvc.perform(get("/api/v1/admin/tenants").param("type", "personal"))
                .andExpect(jsonPath("$.data[0].type").value("personal"));
    }

    @Test
    void tenantMembersNeedMemberRead() throws Exception {
        when(console.listTenantMembers(TENANT_ID)).thenReturn(List.of(
                new TenantMemberView(TARGET, "demo", "active", NOW, List.of("tenant_owner"))));
        assertForbidden(get("/api/v1/admin/tenants/" + TENANT_ID + "/members"));
        loginWith("tenant.member.read");
        mockMvc.perform(get("/api/v1/admin/tenants/" + TENANT_ID + "/members"))
                .andExpect(jsonPath("$.data[0].role_codes[0]").value("tenant_owner"));
    }

    // ----- 用户状态与手机号 -----

    @Test
    void disableNeedsUserDisable() throws Exception {
        when(console.disableUser(USER, TARGET)).thenReturn(new UserStatusView(TARGET, "disabled"));
        assertForbidden(post("/api/v1/admin/users/" + TARGET + "/disable"));
        loginWith("user.disable");
        mockMvc.perform(post("/api/v1/admin/users/" + TARGET + "/disable"))
                .andExpect(jsonPath("$.data.status").value("disabled"));
    }

    @Test
    void enableNeedsUserEnable() throws Exception {
        when(console.enableUser(USER, TARGET)).thenReturn(new UserStatusView(TARGET, "active"));
        assertForbidden(post("/api/v1/admin/users/" + TARGET + "/enable"));
        loginWith("user.enable");
        mockMvc.perform(post("/api/v1/admin/users/" + TARGET + "/enable"))
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void phoneRevealNeedsReadPhone() throws Exception {
        when(console.revealPhone(USER, TARGET)).thenReturn(new PhoneRevealView(TARGET, "+8613800000000"));
        assertForbidden(post("/api/v1/admin/users/" + TARGET + "/phone-reveal"));
        loginWith("user.read_phone");
        mockMvc.perform(post("/api/v1/admin/users/" + TARGET + "/phone-reveal"))
                .andExpect(jsonPath("$.data.phone").value("+8613800000000"));
    }

    // ----- 后台取消任务 -----

    @Test
    void cancelJobNeedsJobCancel() throws Exception {
        when(cancelAdminJob.cancel(USER, JOB_ID))
                .thenReturn(new JobView(JOB_ID.toString(), "cancelled", 0, 3, null, null, NOW.toString()));
        assertForbidden(post("/api/v1/admin/jobs/" + JOB_ID + "/cancel"));
        loginWith("job.cancel");
        mockMvc.perform(post("/api/v1/admin/jobs/" + JOB_ID + "/cancel"))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.status").value("cancelled"));
    }

    // ----- Banner -----

    @Test
    void bannersNeedBannerRead() throws Exception {
        when(console.listBanners()).thenReturn(List.of(bannerView()));
        assertForbidden(get("/api/v1/admin/banners"));
        loginWith("banner.read");
        mockMvc.perform(get("/api/v1/admin/banners"))
                .andExpect(jsonPath("$.data[0].placement").value("home_promo"))
                .andExpect(jsonPath("$.data[0].image_object_key").value("banners/a.png"));
    }

    @Test
    void createBannerNeedsBannerWrite() throws Exception {
        when(console.createBanner(eq(USER), any())).thenReturn(bannerView());
        MockHttpServletRequestBuilder call = post("/api/v1/admin/banners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placement\":\"home_promo\",\"title\":\"活动\",\"image_object_key\":\"banners/a.png\"}");
        assertForbidden(call);
        loginWith("banner.write");
        mockMvc.perform(call).andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void updateBannerNeedsBannerWrite() throws Exception {
        when(console.updateBanner(eq(USER), eq(BANNER_ID), any())).thenReturn(bannerView());
        MockHttpServletRequestBuilder call = put("/api/v1/admin/banners/" + BANNER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placement\":\"home_promo\",\"title\":\"活动\"}");
        assertForbidden(call);
        loginWith("banner.write");
        mockMvc.perform(call).andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void enableBannerNeedsBannerWrite() throws Exception {
        when(console.enableBanner(USER, BANNER_ID)).thenReturn(bannerView());
        assertForbidden(post("/api/v1/admin/banners/" + BANNER_ID + "/enable"));
        loginWith("banner.write");
        mockMvc.perform(post("/api/v1/admin/banners/" + BANNER_ID + "/enable"))
                .andExpect(jsonPath("$.ok").value(true));
    }

    /** 公开当前 Banner：免登录（闸门在 AuthPathPolicy，这里只验控制器形状）。 */
    @Test
    void currentBannerIsPublicShape() throws Exception {
        when(console.currentBanner("home_promo")).thenReturn(bannerView());
        mockMvc.perform(get("/api/v1/banners/current").param("placement", "home_promo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.placement").value("home_promo"));
    }

    @Test
    void currentBannerRejectsAnUnknownPlacement() throws Exception {
        when(console.currentBanner("sidebar")).thenThrow(JobException.validation());
        mockMvc.perform(get("/api/v1/banners/current").param("placement", "sidebar"))
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
    }

    // ----- 字典 -----

    @Test
    void dictionariesNeedDictionaryRead() throws Exception {
        when(console.listDictionary()).thenReturn(List.of(
                new DictionaryView("job_status", "queued", "zh", "已入队", 20)));
        assertForbidden(get("/api/v1/admin/dictionaries"));
        loginWith("dictionary.read");
        mockMvc.perform(get("/api/v1/admin/dictionaries"))
                .andExpect(jsonPath("$.data[0].dict_code").value("job_status"))
                .andExpect(jsonPath("$.data[0].label").value("已入队"));
    }

    @Test
    void updateDictionaryNeedsDictionaryWrite() throws Exception {
        when(console.updateDictionary(eq(USER), eq("job_status"), eq("queued"), eq("zh"), eq("排队中"), eq(21)))
                .thenReturn(new DictionaryView("job_status", "queued", "zh", "排队中", 21));
        MockHttpServletRequestBuilder call = put("/api/v1/admin/dictionaries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dict_code\":\"job_status\",\"item_code\":\"queued\",\"locale\":\"zh\","
                        + "\"label\":\"排队中\",\"sort_order\":21}");
        assertForbidden(call);
        loginWith("dictionary.write");
        mockMvc.perform(call)
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.label").value("排队中"));
    }

    // ----- 运行时开关 -----

    @Test
    void runtimeListNeedsRuntimeRead() throws Exception {
        when(runtimeSettings.listAll()).thenReturn(Map.of("auth.email_login_enabled", "true"));
        assertForbidden(get("/api/v1/admin/runtime-settings"));
        loginWith("runtime.read");
        mockMvc.perform(get("/api/v1/admin/runtime-settings"))
                .andExpect(jsonPath("$.data['auth.email_login_enabled']").value("true"));
    }

    @Test
    void runtimePatchNeedsRuntimeWrite() throws Exception {
        when(runtimeSettings.patch(any())).thenReturn(Map.of("auth.email_login_enabled", "true"));
        MockHttpServletRequestBuilder call = patch("/api/v1/admin/runtime-settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"auth.email_login_enabled\":\"true\"}");
        assertForbidden(call);
        loginWith("runtime.write");
        mockMvc.perform(call)
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data['auth.email_login_enabled']").value("true"));
    }

    /** 任意键 / 非 true/false 的值：用例层收窄后回 VALIDATION_ERROR。 */
    @Test
    void runtimePatchRejectsOtherKeys() throws Exception {
        loginWith("runtime.write");
        when(runtimeSettings.patch(any())).thenThrow(JobException.validation());
        mockMvc.perform(patch("/api/v1/admin/runtime-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studio.promo.enabled\":\"true\"}"))
                .andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
    }

    // ----- 视频产品上下架 -----

    @Test
    void videoProductEnabledNeedsModelWrite() throws Exception {
        ModelProduct product = new ModelProduct(UUID.randomUUID(), "video-stable", "video.generate",
                "Video", "test", 30, "{}", false, 10);
        when(console.setVideoProductEnabled(USER, "video-stable", false)).thenReturn(false);
        when(catalog.findProductByCode("video-stable")).thenReturn(java.util.Optional.of(product));
        MockHttpServletRequestBuilder call = put("/api/v1/admin/video/products/video-stable/enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}");
        assertForbidden(call);
        loginWith("model.write");
        mockMvc.perform(call)
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.code").value("video-stable"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                // 只改 enabled，价格原样
                .andExpect(jsonPath("$.data.price_credits").value(30));
    }

    // ----- 统计 / 异常 / 状态 / 审计 -----

    @Test
    void statsOverviewNeedsStatsRead() throws Exception {
        when(console.statsOverview(any(), any())).thenReturn(new StatsOverviewView(3, 1, 2,
                List.of(new art.pilipala.api.admin.application.AdminConsoleViews.ModelStatView("m1", 5, 1)),
                List.of(new art.pilipala.api.admin.application.AdminConsoleViews.DailyLedgerView(
                        LocalDate.parse("2026-10-02"), 100, 40, 10))));
        assertForbidden(get("/api/v1/admin/stats/overview?" + RANGE));
        loginWith("stats.read");
        mockMvc.perform(get("/api/v1/admin/stats/overview?" + RANGE))
                .andExpect(jsonPath("$.data.registered").value(3))
                .andExpect(jsonPath("$.data.activated").value(1))
                .andExpect(jsonPath("$.data.login_users").value(2))
                .andExpect(jsonPath("$.data.models[0].succeeded").value(5))
                .andExpect(jsonPath("$.data.daily_ledger[0].topup").value(100));
    }

    @Test
    void statsExceptionsNeedStatsRead() throws Exception {
        when(console.statsExceptions()).thenReturn(List.of(new StatsExceptionView(
                "succeeded_hold_unconfirmed", JOB_ID, null, TENANT_ID, UUID.randomUUID(), "succeeded", NOW)));
        assertForbidden(get("/api/v1/admin/stats/exceptions"));
        loginWith("stats.read");
        mockMvc.perform(get("/api/v1/admin/stats/exceptions"))
                .andExpect(jsonPath("$.data[0].type").value("succeeded_hold_unconfirmed"));
    }

    @Test
    void statusNeedsStatusRead() throws Exception {
        when(systemStatus.status()).thenReturn(new SystemStatusView("up", "unknown", "unknown", 2, 1));
        assertForbidden(get("/api/v1/admin/status"));
        loginWith("status.read");
        mockMvc.perform(get("/api/v1/admin/status"))
                .andExpect(jsonPath("$.data.api").value("up"))
                .andExpect(jsonPath("$.data.worker").value("unknown"))
                .andExpect(jsonPath("$.data.pending_outbox").value(2))
                .andExpect(jsonPath("$.data.running_jobs").value(1));
    }

    @Test
    void auditLogsNeedAuditRead() throws Exception {
        when(console.listAuditLogs(anyInt())).thenReturn(List.of(new AuditLogView(UUID.randomUUID(), USER,
                "wallet.topup", TENANT_ID, "wallet", "w1", "k1", "客服补偿", NOW)));
        assertForbidden(get("/api/v1/admin/audit-logs"));
        loginWith("audit.read");
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(jsonPath("$.data[0].permission_code").value("wallet.topup"));
    }

    // ----- 导出 -----

    @Test
    void ledgerExportNeedsLedgerRead() throws Exception {
        when(console.exportLedgerCsv(any(), any()))
                .thenReturn("id,tenant_id,user_id,type,amount,hold_id,job_id,recharge_order_id,created_at\n");
        assertForbidden(get("/api/v1/admin/exports/ledger?" + RANGE));
        loginWith("ledger.read");
        mockMvc.perform(get("/api/v1/admin/exports/ledger?" + RANGE))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString("type,amount")));
    }

    @Test
    void rechargeExportNeedsRechargeRead() throws Exception {
        when(console.exportRechargeCsv(any(), any())).thenReturn("id,tenant_id\n");
        assertForbidden(get("/api/v1/admin/exports/recharge-orders?" + RANGE));
        loginWith("recharge.read");
        mockMvc.perform(get("/api/v1/admin/exports/recharge-orders?" + RANGE))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", containsString("text/csv")));
    }

    // ----- 辅助 -----

    /** 未持任何平台权限码时，统一回 FORBIDDEN 信封。 */
    private void assertForbidden(MockHttpServletRequestBuilder call) throws Exception {
        loginWith();
        mockMvc.perform(call)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    private void loginWith(String... platformCodes) {
        when(sessions.currentUserId()).thenReturn(USER.toString());
        for (String code : platformCodes) {
            when(access.hasPlatform(USER, code)).thenReturn(true);
        }
    }

    private RoleView roleView() {
        return new RoleView(ROLE_ID, "ops", "运营", "platform", false, List.of("role.read"));
    }

    private BannerView bannerView() {
        return new BannerView(BANNER_ID, "home_promo", "活动", null, "banners/a.png", null, null, null, 0,
                true, null, NOW, NOW);
    }
}
