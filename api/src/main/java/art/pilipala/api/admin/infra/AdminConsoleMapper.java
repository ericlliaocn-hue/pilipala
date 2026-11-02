package art.pilipala.api.admin.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.admin.application.AdminConsoleViews.AuditLogView;
import art.pilipala.api.admin.application.AdminConsoleViews.BannerView;
import art.pilipala.api.admin.application.AdminConsoleViews.DailyLedgerView;
import art.pilipala.api.admin.application.AdminConsoleViews.DictionaryView;
import art.pilipala.api.admin.application.AdminConsoleViews.LedgerExportRow;
import art.pilipala.api.admin.application.AdminConsoleViews.ModelStatView;
import art.pilipala.api.admin.application.AdminConsoleViews.PermissionView;
import art.pilipala.api.admin.application.AdminConsoleViews.RechargeExportRow;
import art.pilipala.api.admin.application.AdminConsoleViews.StatsExceptionView;
import art.pilipala.api.admin.application.AdminConsoleViews.TenantView;

/**
 * 步骤 4 新管理接口的 SQL。表与列全部来自 V24 已有结构；缺列不在这里猜，
 * 由服务层停下来报告。
 *
 * <p>权限身份是 {@code (code, scope)}：所有权限存在性校验都带 scope。
 * 钱包金额 SQL 不在这里出现：加额 / 退款 / 取消仍走既有用例。
 */
@Mapper
public interface AdminConsoleMapper {

    /** 角色行（权限码集合单独查，避免联表聚合映射）。 */
    record RoleRow(UUID id, String code, String name, String scope, boolean system, Instant createdAt) {
    }

    /** 员工行：roleCodes 是逗号拼接的平台角色码，服务层拆开。 */
    record StaffRow(UUID userId, String username, String status, String roleCodes) {
    }

    /** 租户成员行：roleCodes 是逗号拼接的该租户角色码。 */
    record MemberRow(UUID userId, String username, String status, Instant createdAt, String roleCodes) {
    }

    // ----- 权限与角色 -----

    @Select("SELECT code, scope, description FROM permission ORDER BY scope, code")
    List<PermissionView> findPermissions();

    @Select("SELECT id, code, name, scope, system, created_at FROM role ORDER BY scope, code")
    List<RoleRow> findRoles();

    @Select("SELECT id, code, name, scope, system, created_at FROM role WHERE id = #{id}")
    RoleRow findRoleById(@Param("id") UUID id);

    @Select("SELECT id, code, name, scope, system, created_at FROM role WHERE code = #{code}")
    RoleRow findRoleByCode(@Param("code") String code);

    @Select("SELECT permission_code FROM role_permission WHERE role_id = #{roleId} ORDER BY permission_code")
    List<String> findRolePermissionCodes(@Param("roleId") UUID roleId);

    @Select("SELECT count(*) FROM permission WHERE code = #{code} AND scope = #{scope}")
    int countPermission(@Param("code") String code, @Param("scope") String scope);

    @Insert("INSERT INTO role (id, code, name, scope, system) VALUES (#{id}, #{code}, #{name}, #{scope}, false)")
    int insertRole(@Param("id") UUID id, @Param("code") String code, @Param("name") String name,
            @Param("scope") String scope);

    @Insert("INSERT INTO role_permission (role_id, permission_code, permission_scope) "
            + "VALUES (#{roleId}, #{code}, #{scope}) ON CONFLICT DO NOTHING")
    int insertRolePermission(@Param("roleId") UUID roleId, @Param("code") String code, @Param("scope") String scope);

    @Delete("DELETE FROM role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") UUID roleId);

    @Select("SELECT count(*) FROM role_assignment WHERE role_id = #{roleId}")
    int countRoleAssignments(@Param("roleId") UUID roleId);

    @Delete("DELETE FROM role WHERE id = #{id}")
    int deleteRole(@Param("id") UUID id);

    // ----- 员工（平台角色分配）-----

    @Select("SELECT u.id AS user_id, u.username, u.status, "
            + "(SELECT string_agg(r2.code, ',' ORDER BY r2.code) FROM role_assignment ra2 "
            + "JOIN role r2 ON r2.id = ra2.role_id "
            + "WHERE ra2.user_id = u.id AND ra2.tenant_id IS NULL AND r2.scope = 'platform') AS role_codes "
            + "FROM users u WHERE EXISTS (SELECT 1 FROM role_assignment ra JOIN role r ON r.id = ra.role_id "
            + "WHERE ra.user_id = u.id AND ra.tenant_id IS NULL AND r.scope = 'platform') "
            + "ORDER BY u.created_at DESC")
    List<StaffRow> findStaff();

    @Select("SELECT count(*) FROM users WHERE id = #{userId}")
    int countUser(@Param("userId") UUID userId);

    @Select("SELECT count(*) FROM role_assignment ra JOIN role r ON r.id = ra.role_id "
            + "WHERE ra.user_id = #{userId} AND ra.tenant_id IS NULL AND r.code = #{roleCode}")
    int countUserPlatformRole(@Param("userId") UUID userId, @Param("roleCode") String roleCode);

    @Delete("DELETE FROM role_assignment WHERE user_id = #{userId} AND tenant_id IS NULL")
    int deletePlatformAssignments(@Param("userId") UUID userId);

    @Insert("INSERT INTO role_assignment (user_id, role_id) "
            + "SELECT #{userId}, r.id FROM role r WHERE r.code = #{roleCode} AND r.scope = 'platform' "
            + "ON CONFLICT DO NOTHING")
    int insertPlatformAssignment(@Param("userId") UUID userId, @Param("roleCode") String roleCode);

    // ----- 租户与成员 -----

    @Select("<script>SELECT id, type, name, status, created_at, updated_at FROM tenant "
            + "<where>"
            + "<if test='type != null'> AND type = #{type}</if>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "</where> ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<TenantView> findTenants(@Param("type") String type, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT count(*) FROM tenant WHERE id = #{tenantId}")
    int countTenant(@Param("tenantId") UUID tenantId);

    @Select("SELECT m.user_id AS user_id, u.username, m.status, m.created_at, "
            + "(SELECT string_agg(r2.code, ',' ORDER BY r2.code) FROM role_assignment ra2 "
            + "JOIN role r2 ON r2.id = ra2.role_id "
            + "WHERE ra2.user_id = m.user_id AND ra2.tenant_id = m.tenant_id AND r2.scope = 'tenant') AS role_codes "
            + "FROM tenant_membership m JOIN users u ON u.id = m.user_id "
            + "WHERE m.tenant_id = #{tenantId} ORDER BY m.created_at, m.id")
    List<MemberRow> findTenantMembers(@Param("tenantId") UUID tenantId);

    // ----- 用户状态与手机号 -----

    @Update("UPDATE users SET status = #{status}, updated_at = now() WHERE id = #{userId}")
    int updateUserStatus(@Param("userId") UUID userId, @Param("status") String status);

    @Select("SELECT phone FROM users WHERE id = #{userId}")
    String findUserPhone(@Param("userId") UUID userId);

    // ----- Banner -----

    String BANNER_COLS = "id, placement, title, subtitle, image_object_key, link_url, starts_at, ends_at, "
            + "sort_order, enabled, campaign_id, created_at, updated_at";

    @Select("SELECT " + BANNER_COLS + " FROM banner ORDER BY placement, sort_order, created_at")
    List<BannerView> findBanners();

    @Select("SELECT " + BANNER_COLS + " FROM banner WHERE id = #{id}")
    BannerView findBannerById(@Param("id") UUID id);

    @Select("SELECT " + BANNER_COLS + " FROM banner WHERE placement = #{placement} AND enabled = true "
            + "AND (starts_at IS NULL OR starts_at <= now()) AND (ends_at IS NULL OR ends_at > now()) "
            + "ORDER BY sort_order ASC, created_at ASC LIMIT 1")
    BannerView findCurrentBanner(@Param("placement") String placement);

    @Insert("INSERT INTO banner (id, placement, title, subtitle, image_object_key, link_url, starts_at, ends_at, "
            + "sort_order, enabled, campaign_id) VALUES (#{id}, #{placement}, #{title}, "
            + "#{subtitle,jdbcType=VARCHAR}, #{imageObjectKey,jdbcType=VARCHAR}, #{linkUrl,jdbcType=VARCHAR}, "
            + "#{startsAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, #{endsAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, "
            + "#{sortOrder}, #{enabled}, #{campaignId,jdbcType=OTHER})")
    int insertBanner(@Param("id") UUID id, @Param("placement") String placement, @Param("title") String title,
            @Param("subtitle") String subtitle, @Param("imageObjectKey") String imageObjectKey,
            @Param("linkUrl") String linkUrl, @Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt,
            @Param("sortOrder") int sortOrder, @Param("enabled") boolean enabled,
            @Param("campaignId") UUID campaignId);

    @Update("UPDATE banner SET placement = #{placement}, title = #{title}, subtitle = #{subtitle,jdbcType=VARCHAR}, "
            + "image_object_key = #{imageObjectKey,jdbcType=VARCHAR}, link_url = #{linkUrl,jdbcType=VARCHAR}, "
            + "starts_at = #{startsAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, "
            + "ends_at = #{endsAt,jdbcType=TIMESTAMP_WITH_TIMEZONE}, sort_order = #{sortOrder}, "
            + "enabled = #{enabled}, campaign_id = #{campaignId,jdbcType=OTHER}, updated_at = now() "
            + "WHERE id = #{id}")
    int updateBanner(@Param("id") UUID id, @Param("placement") String placement, @Param("title") String title,
            @Param("subtitle") String subtitle, @Param("imageObjectKey") String imageObjectKey,
            @Param("linkUrl") String linkUrl, @Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt,
            @Param("sortOrder") int sortOrder, @Param("enabled") boolean enabled,
            @Param("campaignId") UUID campaignId);

    @Update("UPDATE banner SET enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int setBannerEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);

    // ----- 字典 -----

    @Select("SELECT dict_code, item_code, locale, label, sort_order FROM dictionary_entry "
            + "ORDER BY dict_code, sort_order, item_code, locale")
    List<DictionaryView> findDictionaryEntries();

    @Select("SELECT count(*) FROM dictionary_entry WHERE dict_code = #{dictCode} AND item_code = #{itemCode} "
            + "AND locale = #{locale}")
    int countDictionaryEntry(@Param("dictCode") String dictCode, @Param("itemCode") String itemCode,
            @Param("locale") String locale);

    @Update("UPDATE dictionary_entry SET label = #{label}, sort_order = #{sortOrder} "
            + "WHERE dict_code = #{dictCode} AND item_code = #{itemCode} AND locale = #{locale}")
    int updateDictionaryEntry(@Param("dictCode") String dictCode, @Param("itemCode") String itemCode,
            @Param("locale") String locale, @Param("label") String label, @Param("sortOrder") int sortOrder);

    // ----- 审计 -----

    @Select("SELECT id, actor_user_id, permission_code, tenant_id, target_type, target_id, idempotency_key, "
            + "summary, created_at FROM audit_log ORDER BY created_at DESC LIMIT #{limit}")
    List<AuditLogView> findAuditLogs(@Param("limit") int limit);

    // ----- 统计（直接查业务表，不建 analytics_event）-----

    @Select("SELECT count(*) FROM users WHERE created_at BETWEEN #{from} AND #{to}")
    long countRegistered(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 激活：该租户第一笔进入 queued 的任务落在区间内。job 没有单独的入队时间列，
     * 以「状态已越过 created 的第一笔任务的 created_at」近似入队时点，且区间之前
     * 不能已有这样一笔。
     */
    @Select("SELECT count(DISTINCT j.tenant_id) FROM job j WHERE j.status <> 'created' "
            + "AND j.created_at BETWEEN #{from} AND #{to} "
            + "AND NOT EXISTS (SELECT 1 FROM job j2 WHERE j2.tenant_id = j.tenant_id AND j2.status <> 'created' "
            + "AND j2.created_at < #{from})")
    long countActivated(@Param("from") Instant from, @Param("to") Instant to);

    @Select("SELECT count(DISTINCT user_id) FROM login_log WHERE result = 'success' AND user_id IS NOT NULL "
            + "AND created_at BETWEEN #{from} AND #{to}")
    long countLoginUsers(@Param("from") Instant from, @Param("to") Instant to);

    @Select("SELECT model, count(*) FILTER (WHERE status = 'succeeded') AS succeeded, "
            + "count(*) FILTER (WHERE status IN ('failed', 'dead')) AS failed "
            + "FROM job WHERE created_at BETWEEN #{from} AND #{to} GROUP BY model ORDER BY model")
    List<ModelStatView> findModelStats(@Param("from") Instant from, @Param("to") Instant to);

    @Select("SELECT CAST(created_at AT TIME ZONE 'UTC' AS DATE) AS day, "
            + "COALESCE(sum(amount) FILTER (WHERE type = 'topup'), 0) AS topup, "
            + "COALESCE(sum(amount) FILTER (WHERE type = 'confirm'), 0) AS confirm, "
            + "COALESCE(sum(amount) FILTER (WHERE type = 'release'), 0) AS release "
            + "FROM wallet_ledger WHERE created_at BETWEEN #{from} AND #{to} "
            + "GROUP BY CAST(created_at AT TIME ZONE 'UTC' AS DATE) ORDER BY day")
    List<DailyLedgerView> findDailyLedger(@Param("from") Instant from, @Param("to") Instant to);

    /** 四类异常合到一张表返回，type 区分来源。 */
    @Select("SELECT type, job_id, recharge_order_id, tenant_id, hold_id, status, updated_at FROM ("
            + "SELECT 'succeeded_hold_unconfirmed' AS type, j.id AS job_id, NULL::uuid AS recharge_order_id, "
            + "j.tenant_id, j.hold_id, j.status, j.updated_at FROM job j "
            + "JOIN wallet_hold h ON h.id = j.hold_id "
            + "WHERE j.status = 'succeeded' AND h.status <> 'confirmed' "
            + "UNION ALL "
            + "SELECT 'terminal_hold_unreleased', j.id, NULL::uuid, j.tenant_id, j.hold_id, j.status, j.updated_at "
            + "FROM job j JOIN wallet_hold h ON h.id = j.hold_id "
            + "WHERE j.status IN ('failed', 'dead', 'cancelled') AND h.status <> 'released' "
            + "UNION ALL "
            + "SELECT 'paid_without_ledger', NULL::uuid, r.id, r.tenant_id, NULL::uuid, r.status, r.updated_at "
            + "FROM recharge_order r WHERE r.status = 'paid' "
            + "AND NOT EXISTS (SELECT 1 FROM wallet_ledger l WHERE l.recharge_order_id = r.id AND l.type = 'topup') "
            + "UNION ALL "
            + "SELECT 'running_timeout', j.id, NULL::uuid, j.tenant_id, j.hold_id, j.status, j.updated_at "
            + "FROM job j WHERE j.status = 'running' AND j.gateway_timeout_seconds IS NOT NULL "
            + "AND j.updated_at + j.gateway_timeout_seconds * INTERVAL '1 second' < now()"
            + ") e ORDER BY type, updated_at DESC")
    List<StatsExceptionView> findExceptions();

    // ----- 状态 -----

    @Select("SELECT count(*) FROM outbox_message WHERE status = 'pending'")
    long countPendingOutbox();

    @Select("SELECT count(*) FROM job WHERE status = 'running'")
    long countRunningJobs();

    // ----- 导出 -----

    @Select("SELECT id, tenant_id, user_id, type, amount, hold_id, job_id, recharge_order_id, created_at "
            + "FROM wallet_ledger WHERE created_at BETWEEN #{from} AND #{to} ORDER BY created_at, id")
    List<LedgerExportRow> findLedgerForExport(@Param("from") Instant from, @Param("to") Instant to);

    @Select("SELECT id, tenant_id, user_id, out_trade_no, provider, provider_trade_no, package_id, money_fen, "
            + "credits, status, paid_at, refunded_at, created_at FROM recharge_order "
            + "WHERE created_at BETWEEN #{from} AND #{to} ORDER BY created_at, id")
    List<RechargeExportRow> findRechargeForExport(@Param("from") Instant from, @Param("to") Instant to);

    // ----- 视频产品上下架（只改 enabled，不改价格）-----

    @Update("UPDATE model_product SET enabled = #{enabled}, updated_at = now() WHERE code = #{code}")
    int updateVideoProductEnabled(@Param("code") String code, @Param("enabled") boolean enabled);
}
