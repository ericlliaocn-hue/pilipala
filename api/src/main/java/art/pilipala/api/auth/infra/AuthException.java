package art.pilipala.api.auth.infra;

import art.pilipala.api.common.ErrorCodes;

/**
 * 带稳定 error_code 的认证异常。
 */
public class AuthException extends RuntimeException {

    public static final String MESSAGE_KEY_IDENTITY_OCCUPIED = "AUTH_IDENTITY_OCCUPIED";
    public static final String MESSAGE_KEY_EMAIL_REGISTER_DISABLED = "AUTH_EMAIL_REGISTER_DISABLED";
    public static final String MESSAGE_KEY_EMAIL_LOGIN_DISABLED = "AUTH_EMAIL_LOGIN_DISABLED";
    public static final String MESSAGE_KEY_OTP_INVALID = "AUTH_OTP_INVALID";
    public static final String MESSAGE_KEY_ACCOUNT_NOT_FOUND = "AUTH_ACCOUNT_NOT_FOUND";
    public static final String MESSAGE_KEY_ALREADY_REGISTERED = "AUTH_IDENTITY_ALREADY_REGISTERED";

    private final String errorCode;
    private final String messageKey;

    private AuthException(String errorCode, String messageKey) {
        super(errorCode, null, false, false);
        this.errorCode = errorCode;
        this.messageKey = messageKey;
    }

    public static AuthException unauthorized() {
        return new AuthException(ErrorCodes.UNAUTHORIZED, null);
    }

    public static AuthException validation() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, null);
    }

    public static AuthException identityOccupied() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, MESSAGE_KEY_IDENTITY_OCCUPIED);
    }

    public static AuthException otpRateLimited() {
        return new AuthException(ErrorCodes.OTP_RATE_LIMITED, null);
    }

    public static AuthException forbidden() {
        return new AuthException(ErrorCodes.FORBIDDEN, null);
    }

    /** 短信 / 邮件等 OTP 通道上游不可用或发送失败（详情仅服务端日志）。 */
    public static AuthException providerUnavailable() {
        return new AuthException(ErrorCodes.PROVIDER_UNAVAILABLE, null);
    }

    public static AuthException emailRegisterDisabled() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, MESSAGE_KEY_EMAIL_REGISTER_DISABLED);
    }

    public static AuthException emailLoginDisabled() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, MESSAGE_KEY_EMAIL_LOGIN_DISABLED);
    }

    public static AuthException otpInvalid() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, MESSAGE_KEY_OTP_INVALID);
    }

    public static AuthException accountNotFound() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, MESSAGE_KEY_ACCOUNT_NOT_FOUND);
    }

    public static AuthException alreadyRegistered() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR, MESSAGE_KEY_ALREADY_REGISTERED);
    }

    /** 账号已停用：登录与重置密码都拒绝，不发会话，也不引导平台角色。 */
    public static AuthException accountDisabled() {
        return new AuthException(ErrorCodes.ACCOUNT_DISABLED, ErrorCodes.ACCOUNT_DISABLED);
    }

    public String errorCode() {
        return errorCode;
    }

    /** 非空时用此 key 解析 message，error_code 仍为 {@link #errorCode()}。 */
    public String messageKey() {
        return messageKey;
    }
}
