package art.pilipala.api.common;

/**
 * 已登记的稳定错误码。
 *
 * <p>完整注册表见 docs/engineering/data-model/errors.md。本文件只收录已登记的码，
 * 不发明新码——需要新码时先改那份文档。
 *
 * <p>WP4 启用的码：UNAUTHORIZED、VALIDATION_ERROR、JOB_NOT_FOUND、JOB_CANCELLED、
 * IDEMPOTENCY_CONFLICT、JOB_FAILED；WP5 起启用 PROVIDER_UNAVAILABLE；
 * WP6 起启用 WALLET_INSUFFICIENT。
 */
public final class ErrorCodes {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_CANCELLED = "JOB_CANCELLED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String JOB_FAILED = "JOB_FAILED";
    /** WP5 起启用：上游不可用或未识别（worker 写进 job.error_code）。 */
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    /** WP6 起启用：冻结失败，余额不足；不入队。 */
    public static final String WALLET_INSUFFICIENT = "WALLET_INSUFFICIENT";
    /** WP10 起启用：项目不存在或不属于当前用户。 */
    public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    /** WP10 起启用：素材不存在或不属于当前用户。 */
    public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
    /** WP11 起启用：已登录但不是管理员，访问 /api/v1/admin/** 被拒。 */
    public static final String FORBIDDEN = "FORBIDDEN";
    /** WP12 起启用：视频轮询到上限仍未进入成功终态（可重试）。 */
    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    /** WP12 起启用：上游响应结构不合法（拿不到任务 id）。 */
    public static final String GATEWAY_INVALID_RESPONSE = "GATEWAY_INVALID_RESPONSE";
    /** WP12 起启用：上游视频任务进入失败终态（不可重试）。 */
    public static final String UPSTREAM_VIDEO_FAILED = "UPSTREAM_VIDEO_FAILED";
    /** WP21 起启用：视频产品没有可用 target（全部禁用/不兼容/超成本上限）。 */
    public static final String NO_AVAILABLE_CHANNEL = "NO_AVAILABLE_CHANNEL";
    /** WP16：充值订单不存在或不属于当前用户。 */
    public static final String RECHARGE_ORDER_NOT_FOUND = "RECHARGE_ORDER_NOT_FOUND";
    /** WP16：z-pay 下单/查单/退款上游失败。 */
    public static final String RECHARGE_PROVIDER_ERROR = "RECHARGE_PROVIDER_ERROR";
    /** WP16：订单不可退款或余额不足以扣回。 */
    public static final String RECHARGE_NOT_REFUNDABLE = "RECHARGE_NOT_REFUNDABLE";
    /** WP17：验证码发送冷却或小时/日发送上限。 */
    public static final String OTP_RATE_LIMITED = "OTP_RATE_LIMITED";
    /** M4 步骤 2：账号已停用，登录与重置密码都拒绝，不发会话。 */
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    private ErrorCodes() {
    }
}
