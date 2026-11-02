package art.pilipala.api.admin.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 步骤 4 新管理接口的视图集合。
 *
 * <p>字段都直接对应 V24 已有表列，不新造列。手机号只出现在 {@link PhoneRevealView}：
 * 用户列表仍然没有手机号字段。
 */
public final class AdminConsoleViews {

    private AdminConsoleViews() {
    }

    /** 权限码一行：身份是 (code, scope)。 */
    public record PermissionView(String code, String scope, String description) {
    }

    /** 角色行 + 它的权限码集合。 */
    public record RoleView(UUID id, String code, String name, String scope, boolean system,
            List<String> permissions) {
    }

    /** 员工行：有平台角色分配的用户 + 他的平台角色码。 */
    public record StaffView(UUID userId, String username, String status, List<String> roleCodes) {
    }

    /** 租户行（分页列表）。 */
    public record TenantView(UUID id, String type, String name, String status, Instant createdAt, Instant updatedAt) {
    }

    /** 租户成员行 + 该成员在这个租户上的角色码。 */
    public record TenantMemberView(UUID userId, String username, String status, Instant createdAt,
            List<String> roleCodes) {
    }

    /** 封禁 / 启用后的用户状态。 */
    public record UserStatusView(UUID userId, String status) {
    }

    /** 完整手机号揭示：只走 user.read_phone 且写审计。 */
    public record PhoneRevealView(UUID userId, String phone) {
    }

    /** Banner 行。imageObjectKey 原样存取，API 不签发上传地址。 */
    public record BannerView(UUID id, String placement, String title, String subtitle, String imageObjectKey,
            String linkUrl, Instant startsAt, Instant endsAt, int sortOrder, boolean enabled, UUID campaignId,
            Instant createdAt, Instant updatedAt) {
    }

    /** 字典行：只允许改 label 与 sortOrder。 */
    public record DictionaryView(String dictCode, String itemCode, String locale, String label, int sortOrder) {
    }

    /** 审计行（只追加，只读出）。 */
    public record AuditLogView(UUID id, UUID actorUserId, String permissionCode, UUID tenantId, String targetType,
            String targetId, String idempotencyKey, String summary, Instant createdAt) {
    }

    /** 按模型的成功 / 失败加死信（不含取消）。 */
    public record ModelStatView(String model, long succeeded, long failed) {
    }

    /** 流水按日合计（UTC 日）。 */
    public record DailyLedgerView(LocalDate day, long topup, long confirm, long release) {
    }

    /** 统计概览。 */
    public record StatsOverviewView(long registered, long activated, long loginUsers, List<ModelStatView> models,
            List<DailyLedgerView> dailyLedger) {
    }

    /**
     * 异常一行。四类共用一个形状：任务类异常带 jobId / holdId，
     * 充值类异常带 rechargeOrderId。
     */
    public record StatsExceptionView(String type, UUID jobId, UUID rechargeOrderId, UUID tenantId, UUID holdId,
            String status, Instant updatedAt) {
    }

    /** 系统状态：api 恒 up；没有配置的探针一律 unknown，不猜地址。 */
    public record SystemStatusView(String api, String worker, String newApi, long pendingOutbox, long runningJobs) {
    }

    /** CSV 导出的一行流水。 */
    public record LedgerExportRow(UUID id, UUID tenantId, UUID userId, String type, long amount, UUID holdId,
            UUID jobId, UUID rechargeOrderId, Instant createdAt) {
    }

    /** CSV 导出的一行充值订单。 */
    public record RechargeExportRow(UUID id, UUID tenantId, UUID userId, String outTradeNo, String provider,
            String providerTradeNo, String packageId, long moneyFen, long credits, String status, Instant paidAt,
            Instant refundedAt, Instant createdAt) {
    }
}
