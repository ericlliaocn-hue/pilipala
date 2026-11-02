package art.pilipala.api.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminConsoleMapper;
import art.pilipala.api.admin.infra.AdminConsoleMapper.RoleRow;
import art.pilipala.api.admin.infra.AuditLogMapper;
import art.pilipala.api.admin.infra.PlatformRoleMapper;
import art.pilipala.api.job.application.CancelJob;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.settings.application.RuntimeSettingService;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper;
import org.junit.jupiter.api.Test;

/**
 * 步骤 4 用例层校验：种子角色不可改 / 不可删、最后一个 platform_owner 不可删、
 * 权限码必须已存在且 scope 一致、封禁不能封自己、31 天跨度、字典未知条目拒绝、
 * 运行时开关收窄、后台取消终态拒绝。这些规则都在控制器闸门之后，
 * 切片测试覆盖不到，所以在这里用 mock 仓储逐条验。
 */
class AdminConsoleServiceTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private record Fixture(ManageAdminConsole console, AdminConsoleMapper mapper, PlatformRoleMapper owners,
            AuditLogMapper audit) {
    }

    private static Fixture fixture() {
        AdminConsoleMapper mapper = mock(AdminConsoleMapper.class);
        PlatformRoleMapper owners = mock(PlatformRoleMapper.class);
        AuditLogMapper audit = mock(AuditLogMapper.class);
        return new Fixture(new ManageAdminConsole(mapper, owners, audit), mapper, owners, audit);
    }

    // ----- 角色 -----

    @Test
    void createRoleRejectsAnUnknownPermissionCode() {
        Fixture f = fixture();
        when(f.mapper().countPermission("nope", "platform")).thenReturn(0);

        assertThatThrownBy(() -> f.console().createRole(ACTOR, "ops", "运营", "platform", List.of("nope")))
                .isInstanceOf(JobException.class);
        verify(f.mapper(), never()).insertRole(any(), any(), any(), any());
    }

    /** job.read 在两个 scope 各有一行：给 tenant 角色配平台的 (code, scope) 必须被存在性校验挡下。 */
    @Test
    void createRoleRejectsAPermissionFromTheOtherScope() {
        Fixture f = fixture();
        when(f.mapper().countPermission("wallet.topup", "tenant")).thenReturn(0);

        assertThatThrownBy(() -> f.console().createRole(ACTOR, "t1", "租户角色", "tenant", List.of("wallet.topup")))
                .isInstanceOf(JobException.class);
        verify(f.mapper(), never()).insertRole(any(), any(), any(), any());
    }

    @Test
    void createRoleRejectsABadScopeAndADuplicateCode() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.console().createRole(ACTOR, "ops", "运营", "global", List.of()))
                .isInstanceOf(JobException.class);
        when(f.mapper().findRoleByCode("ops")).thenReturn(new RoleRow(UUID.randomUUID(), "ops", "运营",
                "platform", false, null));
        assertThatThrownBy(() -> f.console().createRole(ACTOR, "ops", "运营", "platform", List.of()))
                .isInstanceOf(JobException.class);
    }

    @Test
    void createRoleWritesTheRolePermissionsAndAudit() {
        Fixture f = fixture();
        UUID roleId = UUID.randomUUID();
        when(f.mapper().countPermission("role.read", "platform")).thenReturn(1);
        when(f.mapper().findRoleById(any())).thenReturn(new RoleRow(roleId, "ops", "运营", "platform", false, null));
        when(f.mapper().findRolePermissionCodes(roleId)).thenReturn(List.of("role.read"));

        var view = f.console().createRole(ACTOR, "ops", "运营", "platform", List.of("role.read"));

        assertThat(view.code()).isEqualTo("ops");
        assertThat(view.system()).isFalse();
        assertThat(view.permissions()).containsExactly("role.read");
        verify(f.mapper()).insertRole(any(), eq("ops"), eq("运营"), eq("platform"));
        verify(f.mapper()).insertRolePermission(any(), eq("role.read"), eq("platform"));
        verify(f.audit()).insert(eq(ACTOR), eq("role.write"), any(), eq("role"), any(), any(), any());
    }

    @Test
    void updatingASystemRolePermissionsIsAValidationError() {
        Fixture f = fixture();
        UUID roleId = UUID.randomUUID();
        when(f.mapper().findRoleById(roleId)).thenReturn(new RoleRow(roleId, "platform_owner", "平台超级管理员",
                "platform", true, null));

        assertThatThrownBy(() -> f.console().updateRolePermissions(ACTOR, roleId, List.of("role.read")))
                .isInstanceOf(JobException.class);
        verify(f.mapper(), never()).deleteRolePermissions(any());
    }

    @Test
    void deletingASystemRoleOrAnAssignedRoleIsAValidationError() {
        Fixture f = fixture();
        UUID systemId = UUID.randomUUID();
        UUID assignedId = UUID.randomUUID();
        when(f.mapper().findRoleById(systemId)).thenReturn(new RoleRow(systemId, "platform_owner", "x",
                "platform", true, null));
        when(f.mapper().findRoleById(assignedId)).thenReturn(new RoleRow(assignedId, "ops", "x",
                "platform", false, null));
        when(f.mapper().countRoleAssignments(assignedId)).thenReturn(1);

        assertThatThrownBy(() -> f.console().deleteRole(ACTOR, systemId)).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> f.console().deleteRole(ACTOR, assignedId)).isInstanceOf(JobException.class);
        verify(f.mapper(), never()).deleteRole(any());
    }

    // ----- 员工：最后一个 platform_owner -----

    @Test
    void removingTheLastPlatformOwnerIsAValidationErrorAndWritesNothing() {
        Fixture f = fixture();
        when(f.mapper().countUser(TARGET)).thenReturn(1);
        when(f.mapper().findRoleByCode("platform_support"))
                .thenReturn(new RoleRow(UUID.randomUUID(), "platform_support", "x", "platform", true, null));
        when(f.mapper().countUserPlatformRole(TARGET, "platform_owner")).thenReturn(1);
        when(f.owners().countPlatformOwners()).thenReturn(1);

        assertThatThrownBy(() -> f.console().replaceStaffRoles(ACTOR, TARGET, List.of("platform_support")))
                .isInstanceOf(JobException.class);
        verify(f.mapper(), never()).deletePlatformAssignments(any());
        verify(f.mapper(), never()).insertPlatformAssignment(any(), any());
    }

    @Test
    void replacingStaffRolesRejectsTenantScopedRoles() {
        Fixture f = fixture();
        when(f.mapper().countUser(TARGET)).thenReturn(1);
        when(f.mapper().findRoleByCode("tenant_owner"))
                .thenReturn(new RoleRow(UUID.randomUUID(), "tenant_owner", "x", "tenant", true, null));

        assertThatThrownBy(() -> f.console().replaceStaffRoles(ACTOR, TARGET, List.of("tenant_owner")))
                .isInstanceOf(JobException.class);
        verify(f.mapper(), never()).deletePlatformAssignments(any());
    }

    // ----- 用户状态 -----

    @Test
    void anAdminCannotDisableThemselves() {
        Fixture f = fixture();
        when(f.mapper().countUser(ACTOR)).thenReturn(1);

        assertThatThrownBy(() -> f.console().disableUser(ACTOR, ACTOR)).isInstanceOf(JobException.class);
        verify(f.mapper(), never()).updateUserStatus(any(), any());
    }

    @Test
    void disableAndEnableOnlyFlipStatusAndAudit() {
        Fixture f = fixture();
        when(f.mapper().countUser(TARGET)).thenReturn(1);

        assertThat(f.console().disableUser(ACTOR, TARGET).status()).isEqualTo("disabled");
        assertThat(f.console().enableUser(ACTOR, TARGET).status()).isEqualTo("active");
        verify(f.mapper()).updateUserStatus(TARGET, "disabled");
        verify(f.mapper()).updateUserStatus(TARGET, "active");
        verify(f.audit()).insert(eq(ACTOR), eq("user.disable"), any(), eq("user"), eq(TARGET.toString()),
                any(), any());
    }

    // ----- Banner / 字典 -----

    @Test
    void currentBannerRejectsAnUnknownPlacement() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.console().currentBanner("sidebar")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> f.console().createBanner(ACTOR,
                new ManageAdminConsole.BannerInput("sidebar", "t", null, null, null, null, null, null, null, null)))
                .isInstanceOf(JobException.class);
    }

    @Test
    void dictionaryUpdateRejectsUnknownEntries() {
        Fixture f = fixture();
        when(f.mapper().countDictionaryEntry("job_status", "nope", "zh")).thenReturn(0);

        assertThatThrownBy(() -> f.console().updateDictionary(ACTOR, "job_status", "nope", "zh", "x", 1))
                .isInstanceOf(JobException.class);
        verify(f.mapper(), never()).updateDictionaryEntry(any(), any(), any(), any(), anyInt());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    // ----- 统计 / 导出区间 -----

    @Test
    void statsRangeMustBeWithin31Days() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.console().statsOverview("2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> f.console().statsOverview(null, "2026-10-31T00:00:00Z"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> f.console().exportLedgerCsv("2026-10-01T00:00:00Z", "2026-12-01T00:00:00Z"))
                .isInstanceOf(JobException.class);
        // 正好 31 天整：放行（仓储返回空集即可）
        when(f.mapper().countRegistered(any(), any())).thenReturn(0L);
        when(f.mapper().countActivated(any(), any())).thenReturn(0L);
        when(f.mapper().countLoginUsers(any(), any())).thenReturn(0L);
        when(f.mapper().findModelStats(any(), any())).thenReturn(List.of());
        when(f.mapper().findDailyLedger(any(), any())).thenReturn(List.of());
        assertThatCode(() -> f.console().statsOverview("2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z"))
                .doesNotThrowAnyException();
    }

    @Test
    void ledgerExportBuildsCsvWithAHeader() {
        Fixture f = fixture();
        UUID id = UUID.randomUUID();
        when(f.mapper().findLedgerForExport(any(), any())).thenReturn(List.of(
                new art.pilipala.api.admin.application.AdminConsoleViews.LedgerExportRow(
                        id, TARGET, ACTOR, "topup", 100, null, null, null,
                        java.time.Instant.parse("2026-10-02T00:00:00Z"))));

        String csv = f.console().exportLedgerCsv("2026-10-01", "2026-10-31");

        assertThat(csv).startsWith("id,tenant_id,user_id,type,amount,hold_id,job_id,recharge_order_id,created_at\n");
        assertThat(csv).contains(id.toString()).contains("topup,100");
    }

    // ----- 视频产品 -----

    @Test
    void videoProductEnabledRejectsAnUnknownCode() {
        Fixture f = fixture();
        when(f.mapper().updateVideoProductEnabled("nope", true)).thenReturn(0);
        assertThatThrownBy(() -> f.console().setVideoProductEnabled(ACTOR, "nope", true))
                .isInstanceOf(JobException.class);
    }

    // ----- 后台取消任务 -----

    @Test
    void adminCancelRejectsTerminalJobsInsteadOfReturningSuccess() {
        JobRepository jobs = mock(JobRepository.class);
        CancelJob cancelJob = mock(CancelJob.class);
        AuditLogMapper audit = mock(AuditLogMapper.class);
        UUID jobId = UUID.randomUUID();
        when(jobs.findById(jobId)).thenReturn(Optional.of(job(jobId, JobStatus.SUCCEEDED)));
        CancelAdminJob service = new CancelAdminJob(jobs, cancelJob, audit);

        assertThatThrownBy(() -> service.cancel(ACTOR, jobId)).isInstanceOf(JobException.class);
        verify(cancelJob, never()).cancel(any(), any(), any());
    }

    @Test
    void adminCancelDelegatesToTheExistingCancelByTenant() {
        JobRepository jobs = mock(JobRepository.class);
        CancelJob cancelJob = mock(CancelJob.class);
        AuditLogMapper audit = mock(AuditLogMapper.class);
        UUID jobId = UUID.randomUUID();
        Job running = job(jobId, JobStatus.RUNNING);
        when(jobs.findById(jobId)).thenReturn(Optional.of(running));
        when(cancelJob.cancel(running.tenantId(), jobId, "zh"))
                .thenReturn(new art.pilipala.api.job.application.JobView(jobId.toString(), "running", 1, 3,
                        null, null, "2026-11-01T00:00:00Z"));
        CancelAdminJob service = new CancelAdminJob(jobs, cancelJob, audit);

        var view = service.cancel(ACTOR, jobId);

        assertThat(view.jobId()).isEqualTo(jobId.toString());
        verify(cancelJob).cancel(running.tenantId(), jobId, "zh");
        verify(audit).insert(eq(ACTOR), eq("job.cancel"), eq(running.tenantId()), eq("job"),
                eq(jobId.toString()), any(), any());
    }

    private static Job job(UUID id, JobStatus status) {
        java.time.Instant now = java.time.Instant.parse("2026-11-01T00:00:00Z");
        return new Job(id, TARGET, ACTOR, status, "video.generate", "m1", null, null, null, null, null, null,
                null, "{}", 1, 3, null, "k1", null, null, null, now, now);
    }

    // ----- 运行时开关收窄 -----

    @Test
    void runtimePatchOnlyAllowsTheTwoAuthKeysWithBooleanValues() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        RuntimeSettingService service = new RuntimeSettingService(mapper);

        assertThat(service.patch(Map.of("auth.email_login_enabled", "true")))
                .containsEntry("auth.email_login_enabled", "true");
        verify(mapper).upsert("auth.email_login_enabled", "true");

        assertThatThrownBy(() -> service.patch(Map.of("studio.promo.enabled", "true")))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> service.patch(Map.of("auth.email_login_enabled", "yes")))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> service.patch(Map.of("auth.email_register_enabled", "")))
                .isInstanceOf(JobException.class);
    }
}
