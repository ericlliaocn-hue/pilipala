package art.pilipala.api.admin.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.AuditLogView;
import art.pilipala.api.admin.application.AdminConsoleViews.BannerView;
import art.pilipala.api.admin.application.AdminConsoleViews.DictionaryView;
import art.pilipala.api.admin.application.AdminConsoleViews.LedgerExportRow;
import art.pilipala.api.admin.application.AdminConsoleViews.PermissionView;
import art.pilipala.api.admin.application.AdminConsoleViews.PhoneRevealView;
import art.pilipala.api.admin.application.AdminConsoleViews.RechargeExportRow;
import art.pilipala.api.admin.application.AdminConsoleViews.RoleView;
import art.pilipala.api.admin.application.AdminConsoleViews.StaffView;
import art.pilipala.api.admin.application.AdminConsoleViews.StatsExceptionView;
import art.pilipala.api.admin.application.AdminConsoleViews.StatsOverviewView;
import art.pilipala.api.admin.application.AdminConsoleViews.TenantMemberView;
import art.pilipala.api.admin.application.AdminConsoleViews.TenantView;
import art.pilipala.api.admin.application.AdminConsoleViews.UserStatusView;
import art.pilipala.api.admin.infra.AdminConsoleMapper;
import art.pilipala.api.admin.infra.AdminConsoleMapper.MemberRow;
import art.pilipala.api.admin.infra.AdminConsoleMapper.RoleRow;
import art.pilipala.api.admin.infra.AdminConsoleMapper.StaffRow;
import art.pilipala.api.admin.infra.AuditLogMapper;
import art.pilipala.api.admin.infra.PlatformRoleMapper;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 步骤 4 新管理接口的用例集合：角色 / 员工 / 租户 / 用户状态 / Banner / 字典 /
 * 统计 / 异常 / 审计 / 导出 / 视频产品上下架。
 *
 * <p>权限闸门在控制器（{@link AdminGuard#require}），这里只做业务校验：
 * 所有不合法状态一律 {@code VALIDATION_ERROR}，不新造错误码。写操作都追加
 * {@code audit_log}；{@code reason} 一类的备注只进 {@code audit_log.summary}，
 * 不给流水加列。钱包金额 SQL 不在这里出现。
 */
@Service
public class ManageAdminConsole {

    /** brief 写死的统计 / 导出跨度上限。 */
    static final Duration MAX_RANGE = Duration.ofDays(31);

    private static final Set<String> SCOPES = Set.of("platform", "tenant");
    private static final Set<String> TENANT_TYPES = Set.of("personal", "organization");
    private static final Set<String> TENANT_STATUSES = Set.of("active", "disabled");
    private static final Set<String> PLACEMENTS = Set.of("home_promo", "membership");
    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final String PLATFORM_OWNER = "platform_owner";

    private final AdminConsoleMapper mapper;
    private final PlatformRoleMapper platformRoles;
    private final AuditLogMapper audit;

    public ManageAdminConsole(AdminConsoleMapper mapper, PlatformRoleMapper platformRoles, AuditLogMapper audit) {
        this.mapper = mapper;
        this.platformRoles = platformRoles;
        this.audit = audit;
    }

    // ----- 权限与角色 -----

    public List<PermissionView> listPermissions() {
        return mapper.findPermissions();
    }

    public List<RoleView> listRoles() {
        return mapper.findRoles().stream().map(this::roleView).toList();
    }

    @Transactional
    public RoleView createRole(UUID actor, String code, String name, String scope, List<String> permissionCodes) {
        String roleCode = requireText(code);
        String roleName = requireText(name);
        if (scope == null || !SCOPES.contains(scope)) {
            throw JobException.validation();
        }
        if (mapper.findRoleByCode(roleCode) != null) {
            throw JobException.validation();
        }
        List<String> permissions = requirePermissions(permissionCodes, scope);
        UUID id = UUID.randomUUID();
        mapper.insertRole(id, roleCode, roleName, scope);
        permissions.forEach(p -> mapper.insertRolePermission(id, p, scope));
        audit.insert(actor, "role.write", null, "role", id.toString(), null,
                "create role " + roleCode + " scope=" + scope);
        return roleView(mapper.findRoleById(id));
    }

    @Transactional
    public RoleView updateRolePermissions(UUID actor, UUID roleId, List<String> permissionCodes) {
        RoleRow role = requireRole(roleId);
        if (role.system()) {
            throw JobException.validation();
        }
        List<String> permissions = requirePermissions(permissionCodes, role.scope());
        mapper.deleteRolePermissions(roleId);
        permissions.forEach(p -> mapper.insertRolePermission(roleId, p, role.scope()));
        audit.insert(actor, "role.write", null, "role", roleId.toString(), null,
                "update permissions of role " + role.code());
        return roleView(mapper.findRoleById(roleId));
    }

    @Transactional
    public RoleView deleteRole(UUID actor, UUID roleId) {
        RoleRow role = requireRole(roleId);
        if (role.system() || mapper.countRoleAssignments(roleId) > 0) {
            throw JobException.validation();
        }
        RoleView view = roleView(role);
        mapper.deleteRolePermissions(roleId);
        mapper.deleteRole(roleId);
        audit.insert(actor, "role.write", null, "role", roleId.toString(), null, "delete role " + role.code());
        return view;
    }

    // ----- 员工 -----

    public List<StaffView> listStaff() {
        return mapper.findStaff().stream()
                .map(row -> new StaffView(row.userId(), row.username(), row.status(), splitCodes(row.roleCodes())))
                .toList();
    }

    @Transactional
    public StaffView replaceStaffRoles(UUID actor, UUID userId, List<String> roleCodes) {
        if (mapper.countUser(userId) == 0) {
            throw JobException.validation();
        }
        List<String> codes = roleCodes == null ? List.of() : roleCodes.stream().distinct().toList();
        for (String code : codes) {
            RoleRow role = code == null ? null : mapper.findRoleByCode(code);
            if (role == null || !"platform".equals(role.scope())) {
                throw JobException.validation();
            }
        }
        boolean losesOwner = mapper.countUserPlatformRole(userId, PLATFORM_OWNER) > 0
                && !codes.contains(PLATFORM_OWNER);
        if (losesOwner && platformRoles.countPlatformOwners() <= 1) {
            throw JobException.validation();
        }
        mapper.deletePlatformAssignments(userId);
        codes.forEach(code -> mapper.insertPlatformAssignment(userId, code));
        audit.insert(actor, "staff.assign_role", null, "user", userId.toString(), null,
                "platform roles=" + String.join(",", codes));
        return mapper.findStaff().stream()
                .filter(row -> row.userId().equals(userId))
                .map(row -> new StaffView(row.userId(), row.username(), row.status(), splitCodes(row.roleCodes())))
                .findFirst()
                .orElseGet(() -> new StaffView(userId, null, null, codes));
    }

    // ----- 租户 -----

    public List<TenantView> listTenants(String type, String status, int limit, int offset) {
        if (type != null && !TENANT_TYPES.contains(type)) {
            throw JobException.validation();
        }
        if (status != null && !TENANT_STATUSES.contains(status)) {
            throw JobException.validation();
        }
        return mapper.findTenants(type, status, AdminLimits.limit(limit), Math.max(0, offset));
    }

    public List<TenantMemberView> listTenantMembers(UUID tenantId) {
        if (mapper.countTenant(tenantId) == 0) {
            throw JobException.validation();
        }
        return mapper.findTenantMembers(tenantId).stream()
                .map((MemberRow row) -> new TenantMemberView(row.userId(), row.username(), row.status(),
                        row.createdAt(), splitCodes(row.roleCodes())))
                .toList();
    }

    // ----- 用户状态与手机号 -----

    @Transactional
    public UserStatusView disableUser(UUID actor, UUID userId) {
        return setUserStatus(actor, userId, "disabled", "user.disable");
    }

    @Transactional
    public UserStatusView enableUser(UUID actor, UUID userId) {
        return setUserStatus(actor, userId, "active", "user.enable");
    }

    private UserStatusView setUserStatus(UUID actor, UUID userId, String status, String permission) {
        if (mapper.countUser(userId) == 0) {
            throw JobException.validation();
        }
        if ("disabled".equals(status) && actor.equals(userId)) {
            throw JobException.validation();
        }
        mapper.updateUserStatus(userId, status);
        audit.insert(actor, permission, null, "user", userId.toString(), null, "set user status=" + status);
        return new UserStatusView(userId, status);
    }

    @Transactional
    public PhoneRevealView revealPhone(UUID actor, UUID userId) {
        if (mapper.countUser(userId) == 0) {
            throw JobException.validation();
        }
        String phone = mapper.findUserPhone(userId);
        // 摘要只记动作，不把完整手机号抄进审计正文。
        audit.insert(actor, "user.read_phone", null, "user", userId.toString(), null, "reveal phone");
        return new PhoneRevealView(userId, phone);
    }

    // ----- Banner -----

    public List<BannerView> listBanners() {
        return mapper.findBanners();
    }

    /** 公开当前 Banner：没有命中的时候返回 null，控制器回空 data。 */
    public BannerView currentBanner(String placement) {
        if (placement == null || !PLACEMENTS.contains(placement)) {
            throw JobException.validation();
        }
        return mapper.findCurrentBanner(placement);
    }

    @Transactional
    public BannerView createBanner(UUID actor, BannerInput input) {
        validateBanner(input);
        UUID id = UUID.randomUUID();
        mapper.insertBanner(id, input.placement(), input.title().trim(), input.subtitle(),
                input.imageObjectKey(), input.linkUrl(), input.startsAt(), input.endsAt(),
                input.sortOrder() == null ? 0 : input.sortOrder(), Boolean.TRUE.equals(input.enabled()),
                input.campaignId());
        audit.insert(actor, "banner.write", null, "banner", id.toString(), null,
                "create banner placement=" + input.placement());
        return mapper.findBannerById(id);
    }

    @Transactional
    public BannerView updateBanner(UUID actor, UUID id, BannerInput input) {
        validateBanner(input);
        if (mapper.findBannerById(id) == null) {
            throw JobException.validation();
        }
        mapper.updateBanner(id, input.placement(), input.title().trim(), input.subtitle(),
                input.imageObjectKey(), input.linkUrl(), input.startsAt(), input.endsAt(),
                input.sortOrder() == null ? 0 : input.sortOrder(), Boolean.TRUE.equals(input.enabled()),
                input.campaignId());
        audit.insert(actor, "banner.write", null, "banner", id.toString(), null,
                "update banner placement=" + input.placement());
        return mapper.findBannerById(id);
    }

    @Transactional
    public BannerView enableBanner(UUID actor, UUID id) {
        if (mapper.findBannerById(id) == null) {
            throw JobException.validation();
        }
        mapper.setBannerEnabled(id, true);
        audit.insert(actor, "banner.write", null, "banner", id.toString(), null, "enable banner");
        return mapper.findBannerById(id);
    }

    private void validateBanner(BannerInput input) {
        if (input == null || input.placement() == null || !PLACEMENTS.contains(input.placement())
                || input.title() == null || input.title().isBlank()) {
            throw JobException.validation();
        }
    }

    /** Banner 写入请求体；imageObjectKey 由调用方传入原样入库，API 不做上传。 */
    public record BannerInput(String placement, String title, String subtitle, String imageObjectKey,
            String linkUrl, Instant startsAt, Instant endsAt, Integer sortOrder, Boolean enabled,
            UUID campaignId) {
    }

    // ----- 字典（只改 label / sort_order）-----

    public List<DictionaryView> listDictionary() {
        return mapper.findDictionaryEntries();
    }

    @Transactional
    public DictionaryView updateDictionary(UUID actor, String dictCode, String itemCode, String locale,
            String label, Integer sortOrder) {
        if (dictCode == null || itemCode == null || locale == null || !LOCALES.contains(locale)
                || label == null || label.isBlank() || sortOrder == null
                || mapper.countDictionaryEntry(dictCode, itemCode, locale) == 0) {
            throw JobException.validation();
        }
        mapper.updateDictionaryEntry(dictCode, itemCode, locale, label.trim(), sortOrder);
        audit.insert(actor, "dictionary.write", null, "dictionary_entry", dictCode + "/" + itemCode + "/" + locale,
                null, "update dictionary label");
        return new DictionaryView(dictCode, itemCode, locale, label.trim(), sortOrder);
    }

    // ----- 统计与异常 -----

    public StatsOverviewView statsOverview(String from, String to) {
        DateRange range = DateRange.parse(from, to);
        return new StatsOverviewView(
                mapper.countRegistered(range.from(), range.to()),
                mapper.countActivated(range.from(), range.to()),
                mapper.countLoginUsers(range.from(), range.to()),
                mapper.findModelStats(range.from(), range.to()),
                mapper.findDailyLedger(range.from(), range.to()));
    }

    public List<StatsExceptionView> statsExceptions() {
        return mapper.findExceptions();
    }

    // ----- 审计 -----

    public List<AuditLogView> listAuditLogs(int limit) {
        return mapper.findAuditLogs(AdminLimits.limit(limit));
    }

    // ----- 导出 -----

    public String exportLedgerCsv(String from, String to) {
        DateRange range = DateRange.parse(from, to);
        StringBuilder csv = new StringBuilder(
                "id,tenant_id,user_id,type,amount,hold_id,job_id,recharge_order_id,created_at\n");
        for (LedgerExportRow row : mapper.findLedgerForExport(range.from(), range.to())) {
            csv.append(csvRow(row.id(), row.tenantId(), row.userId(), row.type(), row.amount(), row.holdId(),
                    row.jobId(), row.rechargeOrderId(), row.createdAt())).append('\n');
        }
        return csv.toString();
    }

    public String exportRechargeCsv(String from, String to) {
        DateRange range = DateRange.parse(from, to);
        StringBuilder csv = new StringBuilder("id,tenant_id,user_id,out_trade_no,provider,provider_trade_no,"
                + "package_id,money_fen,credits,status,paid_at,refunded_at,created_at\n");
        for (RechargeExportRow row : mapper.findRechargeForExport(range.from(), range.to())) {
            csv.append(csvRow(row.id(), row.tenantId(), row.userId(), row.outTradeNo(), row.provider(),
                    row.providerTradeNo(), row.packageId(), row.moneyFen(), row.credits(), row.status(),
                    row.paidAt(), row.refundedAt(), row.createdAt())).append('\n');
        }
        return csv.toString();
    }

    // ----- 视频产品上下架 -----

    @Transactional
    public boolean setVideoProductEnabled(UUID actor, String code, boolean enabled) {
        if (code == null || code.isBlank() || mapper.updateVideoProductEnabled(code, enabled) == 0) {
            throw JobException.validation();
        }
        audit.insert(actor, "model.write", null, "model_product", code, null,
                "set video product enabled=" + enabled);
        return enabled;
    }

    // ----- 辅助 -----

    private RoleView roleView(RoleRow row) {
        return new RoleView(row.id(), row.code(), row.name(), row.scope(), row.system(),
                mapper.findRolePermissionCodes(row.id()));
    }

    private RoleRow requireRole(UUID roleId) {
        RoleRow role = roleId == null ? null : mapper.findRoleById(roleId);
        if (role == null) {
            throw JobException.validation();
        }
        return role;
    }

    /** 权限码必须已存在，且 (code, scope) 与角色 scope 一致。 */
    private List<String> requirePermissions(List<String> codes, String scope) {
        if (codes == null) {
            throw JobException.validation();
        }
        List<String> distinct = codes.stream().distinct().toList();
        for (String code : distinct) {
            if (code == null || mapper.countPermission(code, scope) == 0) {
                throw JobException.validation();
            }
        }
        return distinct;
    }

    private static String requireText(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw JobException.validation();
        }
        return value;
    }

    private static List<String> splitCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String csvRow(Object... fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvField(fields[i]));
        }
        return row.toString();
    }

    private static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /**
     * from / to 必填，跨度最长 31 天。接受 Instant，或 {@code yyyy-MM-dd}（UTC 日，
     * to 取当日结束）。
     */
    public record DateRange(Instant from, Instant to) {

        public static DateRange parse(String fromRaw, String toRaw) {
            Instant from = parseStart(fromRaw);
            Instant to = parseEnd(toRaw);
            if (from == null || to == null || to.isBefore(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
                throw JobException.validation();
            }
            return new DateRange(from, to);
        }

        private static Instant parseStart(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(raw.trim());
            } catch (RuntimeException e) {
                try {
                    return LocalDate.parse(raw.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }

        private static Instant parseEnd(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(raw.trim());
            } catch (RuntimeException e) {
                try {
                    return LocalDate.parse(raw.trim()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                            .minusNanos(1);
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
    }
}
