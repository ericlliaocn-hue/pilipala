package art.pilipala.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.PasswordHasher;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.billing.application.TopUpWallet;
import art.pilipala.api.common.ErrorCodes;
import art.pilipala.api.i18n.application.I18nDefaultCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthValidationMessagesTest {

    private static final String PHONE = "+8617277889776";

    @Mock
    private UserLookup lookup;
    @Mock
    private PasswordHasher hasher;
    @Mock
    private AuthSessionService sessions;
    @Mock
    private AdminBootstrap adminBootstrap;
    @Mock
    private OtpService otpService;
    @Mock
    private EmailAuthPolicy emailAuthPolicy;
    @Mock
    private UserRepository users;
    @Mock
    private TopUpWallet topUpWallet;

    @InjectMocks
    private LoginUser loginUser;
    @InjectMocks
    private RegisterUser registerUser;
    @InjectMocks
    private ResetPassword resetPassword;
    @InjectMocks
    private RebindPhone rebindPhone;
    @InjectMocks
    private RebindEmail rebindEmail;

    @Test
    void otpLoginOfAnUnknownAccountKeepsValidationCodeAndAccountNotFoundKey() {
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUser.login(new LoginCommand(PHONE, null, null, "132442")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND));
        verify(otpService, never()).verifyAndConsume(any(), anyString(), anyString());
    }

    @Test
    void otpLoginWithAWrongCodeUsesOtpInvalidKey() {
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.of(phoneUser()));
        when(otpService.verifyAndConsume(OtpPurpose.LOGIN, PHONE, "000000")).thenReturn(false);

        assertThatThrownBy(() -> loginUser.login(new LoginCommand(PHONE, null, null, "000000")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_OTP_INVALID));
    }

    @Test
    void registerRejectsAnExistingPhoneBeforeConsumingOtp() {
        when(users.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> registerUser.register(new RegisterCommand(PHONE, null, "secret123", "132442", "zh")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_ALREADY_REGISTERED));
        verify(otpService, never()).verifyAndConsume(any(), anyString(), anyString());
    }

    @Test
    void registerWithAWrongCodeUsesOtpInvalidKey() {
        when(users.existsByPhone(PHONE)).thenReturn(false);
        when(otpService.verifyAndConsume(OtpPurpose.REGISTER, PHONE, "000000")).thenReturn(false);

        assertThatThrownBy(() -> registerUser.register(new RegisterCommand(PHONE, null, "secret123", "000000", "zh")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_OTP_INVALID));
    }

    @Test
    void resetPasswordOfAnUnknownAccountDoesNotBurnTheOtp() {
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetPassword.reset(PHONE, null, "132442", "secret123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND));
        verify(otpService, never()).verifyAndConsume(any(), anyString(), anyString());
    }

    @Test
    void resetPasswordWithAWrongCodeUsesOtpInvalidKey() {
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.of(phoneUser()));
        when(otpService.verifyAndConsume(OtpPurpose.RESET_PASSWORD, PHONE, "000000")).thenReturn(false);

        assertThatThrownBy(() -> resetPassword.reset(PHONE, null, "000000", "secret123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_OTP_INVALID));
    }

    @Test
    void rebindPhoneWithAWrongCodeUsesOtpInvalidKey() {
        User current = phoneUser();
        when(sessions.currentUserId()).thenReturn(current.id().toString());
        when(users.findById(current.id())).thenReturn(Optional.of(current));
        when(users.findByPhone("+8613800138000")).thenReturn(Optional.empty());
        when(otpService.verify(eq(OtpPurpose.REBIND_PHONE), eq("+8613800138000"), eq("000000"))).thenReturn(false);

        assertThatThrownBy(() -> rebindPhone.rebind("13800138000", "000000"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_OTP_INVALID));
        verify(otpService, never()).consume(any(), anyString());
    }

    @Test
    void rebindEmailWithAWrongCodeUsesOtpInvalidKey() {
        User current = phoneUser();
        when(sessions.currentUserId()).thenReturn(current.id().toString());
        when(users.findById(current.id())).thenReturn(Optional.of(current));
        when(users.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(otpService.verify(eq(OtpPurpose.REBIND_EMAIL), eq("user@example.com"), eq("000000"))).thenReturn(false);

        assertThatThrownBy(() -> rebindEmail.rebind("User@Example.COM", "000000"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, AuthException.MESSAGE_KEY_OTP_INVALID));
        verify(otpService, never()).consume(any(), anyString());
    }

    @Test
    void catalogSeedsMatchTheFriendlyCopy() {
        assertThat(I18nDefaultCatalog.resolveDefault(AuthException.MESSAGE_KEY_OTP_INVALID, "zh", null))
                .isEqualTo("验证码错误或已过期。");
        assertThat(I18nDefaultCatalog.resolveDefault(AuthException.MESSAGE_KEY_OTP_INVALID, "en", null))
                .isEqualTo("Invalid or expired verification code.");
        assertThat(I18nDefaultCatalog.resolveDefault(AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND, "zh", null))
                .isEqualTo("该账号尚未注册，请先注册。");
        assertThat(I18nDefaultCatalog.resolveDefault(AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND, "en", null))
                .isEqualTo("This account is not registered yet. Please sign up first.");
        assertThat(I18nDefaultCatalog.resolveDefault(AuthException.MESSAGE_KEY_ALREADY_REGISTERED, "zh", null))
                .isEqualTo("该手机号或邮箱已注册，请直接登录。");
        assertThat(I18nDefaultCatalog.resolveDefault(AuthException.MESSAGE_KEY_ALREADY_REGISTERED, "en", null))
                .isEqualTo("This phone number or email is already registered. Please log in.");
    }

    private static void assertAuth(AuthException error, String messageKey) {
        assertThat(error.errorCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR);
        assertThat(error.messageKey()).isEqualTo(messageKey);
    }

    private static User phoneUser() {
        Instant now = Instant.now();
        return new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), null, PHONE, null, "hash", "zh", now, now);
    }
}
