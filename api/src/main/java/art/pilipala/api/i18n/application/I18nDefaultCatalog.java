package art.pilipala.api.i18n.application;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.common.ErrorCodes;

/**
 * 内置文案种子与纯函数工具：DB 查不到时的最后一道兜底。
 *
 * <p>这里的种子与 {@code V5__create_i18n_message.sql} 灌入的 stage-1 词条逐字一致，
 * 保证「表被误删 / 切片测试没挂 DB」时已登记 error_code 仍有友好短句。
 *
 * <p>ADR 0005：这里只放 pilipala 自己写的短句，绝不放上游原文。
 */
public final class I18nDefaultCatalog {

    /** 带占位符的模板 key 示例；不是 error_code，不进注册表。 */
    public static final String KEY_JOB_FAILED_RETRY = "JOB_FAILED.RETRY";

    /** 占位符只认 {name} 这种具名形式。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    /** 单个参数值的最大长度，超出截断。 */
    private static final int MAX_PARAM_LENGTH = 64;

    /** 种子超过 10 对时用 Map.ofEntries（Map.of 上限是 10 对）。 */
    private static final Map<String, Map<String, String>> SEEDS = Map.ofEntries(
            Map.entry(ErrorCodes.WALLET_INSUFFICIENT, Map.of(
                    "zh", "账户余额不足，请先充值。",
                    "en", "Insufficient wallet balance. Please top up first.")),
            Map.entry(ErrorCodes.JOB_NOT_FOUND, Map.of(
                    "zh", "任务不存在。",
                    "en", "The task does not exist.")),
            Map.entry(ErrorCodes.JOB_CANCELLED, Map.of(
                    "zh", "任务已取消。",
                    "en", "The task has been cancelled.")),
            Map.entry(ErrorCodes.JOB_FAILED, Map.of(
                    "zh", "任务执行失败。",
                    "en", "The task failed to run.")),
            Map.entry(ErrorCodes.PROVIDER_UNAVAILABLE, Map.of(
                    "zh", "生成服务暂时不可用，请稍后重试。",
                    "en", "The generation service is temporarily unavailable. Please try again later.")),
            Map.entry(ErrorCodes.IDEMPOTENCY_CONFLICT, Map.of(
                    "zh", "同一个幂等键对应了不同的请求内容。",
                    "en", "The same idempotency key was used with a different request.")),
            Map.entry(ErrorCodes.UNAUTHORIZED, Map.of(
                    "zh", "请先登录后再试。",
                    "en", "Please sign in first.")),
            Map.entry(ErrorCodes.VALIDATION_ERROR, Map.of(
                    "zh", "请求参数不合法。",
                    "en", "The request parameters are invalid.")),
            Map.entry(ErrorCodes.PROJECT_NOT_FOUND, Map.of(
                    "zh", "项目不存在。",
                    "en", "The project does not exist.")),
            Map.entry(ErrorCodes.ASSET_NOT_FOUND, Map.of(
                    "zh", "素材不存在。",
                    "en", "The asset does not exist.")),
            Map.entry(ErrorCodes.FORBIDDEN, Map.of(
                    "zh", "无权访问管理后台。",
                    "en", "You do not have permission to access the admin console.")),
            Map.entry(ErrorCodes.GATEWAY_TIMEOUT, Map.of(
                    "zh", "视频生成超时，请稍后重试。",
                    "en", "The video generation timed out. Please try again later.")),
            Map.entry(ErrorCodes.GATEWAY_INVALID_RESPONSE, Map.of(
                    "zh", "生成服务返回了无法识别的结果。",
                    "en", "The generation service returned an unrecognized result.")),
            Map.entry(ErrorCodes.UPSTREAM_VIDEO_FAILED, Map.of(
                    "zh", "视频生成失败，请调整提示词后重试。",
                    "en", "The video generation failed. Please adjust the prompt and try again.")),
            Map.entry(ErrorCodes.NO_AVAILABLE_CHANNEL, Map.of(
                    "zh", "当前没有可用的视频线路，请稍后重试。",
                    "en", "No video channel is available right now. Please try again later.")),
            Map.entry(KEY_JOB_FAILED_RETRY, Map.of(
                    "zh", "生成服务异常，正在重试（第 {attempt}/{max_attempts} 次）。",
                    "en", "Generation issue, retrying ({attempt}/{max_attempts}).")),
            Map.entry(ErrorCodes.OTP_RATE_LIMITED, Map.of(
                    "zh", "验证码发送过于频繁，请稍后再试。",
                    "en", "Too many verification code requests. Please try again later.")),
            Map.entry(ErrorCodes.ACCOUNT_DISABLED, Map.of(
                    "zh", "账号已停用。",
                    "en", "This account has been disabled.")),
            Map.entry(AuthException.MESSAGE_KEY_IDENTITY_OCCUPIED, Map.of(
                    "zh", "该手机号或邮箱已被其他账号绑定。",
                    "en", "This phone number or email is already bound to another account.")),
            Map.entry(AuthException.MESSAGE_KEY_EMAIL_REGISTER_DISABLED, Map.of(
                    "zh", "暂不支持邮箱注册。",
                    "en", "Email registration is not available yet.")),
            Map.entry(AuthException.MESSAGE_KEY_EMAIL_LOGIN_DISABLED, Map.of(
                    "zh", "暂不支持邮箱登录。",
                    "en", "Email sign-in is not available yet.")),
            Map.entry(AuthException.MESSAGE_KEY_OTP_INVALID, Map.of(
                    "zh", "验证码错误或已过期。",
                    "en", "Invalid or expired verification code.")),
            Map.entry(AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND, Map.of(
                    "zh", "该账号尚未注册，请先注册。",
                    "en", "This account is not registered yet. Please sign up first.")),
            Map.entry(AuthException.MESSAGE_KEY_ALREADY_REGISTERED, Map.of(
                    "zh", "该手机号或邮箱已注册，请直接登录。",
                    "en", "This phone number or email is already registered. Please log in.")));

    private I18nDefaultCatalog() {
    }

    /**
     * 规范化成 {@code zh} / {@code en}：既能吃 {@code Accept-Language} 头，
     * 也能吃 {@code zh} / {@code en-US} 这种裸 locale。
     */
    public static String localeOf(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return "zh";
        }
        String primary = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
        return primary.startsWith("en") ? "en" : "zh";
    }

    /**
     * 内置种子回退：locale → zh → key 本身（未知 key 的最后手段）。
     */
    public static String resolveDefault(String key, String locale, Map<String, Object> params) {
        if (key == null || key.isBlank()) {
            return resolveDefault(ErrorCodes.PROVIDER_UNAVAILABLE, locale, params);
        }
        Map<String, String> byLocale = SEEDS.get(key);
        if (byLocale == null) {
            return key;
        }
        String lang = localeOf(locale);
        String template = byLocale.get(lang);
        if (template == null) {
            template = byLocale.get("zh");
        }
        return interpolate(template, params);
    }

    /**
     * {@code {placeholder}} 安全插值：缺参保留原占位符，参数值净化后截断。
     *
     * <p>只做一遍替换：参数值里的花括号不会被二次解释。
     */
    public static String interpolate(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        if (params == null || params.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = params.get(matcher.group(1));
            String replacement = value == null ? matcher.group(0) : sanitize(String.valueOf(value));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 参数值净化：剔除换行符防注入，再截断到 64 字符。 */
    private static String sanitize(String value) {
        String cleaned = value.replace("\r", "").replace("\n", "");
        return cleaned.length() <= MAX_PARAM_LENGTH ? cleaned : cleaned.substring(0, MAX_PARAM_LENGTH);
    }
}
