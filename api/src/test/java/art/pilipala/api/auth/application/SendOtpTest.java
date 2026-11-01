package art.pilipala.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.common.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendOtpTest {

    private static final String PHONE = "+8617277889776";
    private static final String EMAIL = "user@example.com";
    private static final UUID CURRENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private OtpService otpService;
    @Mock
    private EmailAuthPolicy emailAuthPolicy;
    @Mock
    private UserRepository users;
    @Mock
    private AuthSessionService sessions;

    private SendOtp sendOtp;

    @BeforeEach
    void setUp() {
        sendOtp = new SendOtp(otpService, emailAuthPolicy, users, sessions);
    }

    @Test
    void loginOtpRejectsUnknownPhoneBeforeSending() {
        when(users.existsByPhone(PHONE)).thenReturn(false);

        assertThatThrownBy(() -> sendOtp.send("sms", PHONE, "login"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, ErrorCodes.VALIDATION_ERROR, AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND));
        verify(otpService, never()).send(anyString(), anyString(), any());
    }

    @Test
    void resetPasswordOtpRejectsUnknownEmailBeforeSending() {
        when(users.existsByEmail(EMAIL)).thenReturn(false);

        assertThatThrownBy(() -> sendOtp.send("email", EMAIL, "reset_password"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, ErrorCodes.VALIDATION_ERROR, AuthException.MESSAGE_KEY_ACCOUNT_NOT_FOUND));
        verify(otpService, never()).send(anyString(), anyString(), any());
    }

    @Test
    void registerOtpRejectsExistingPhoneBeforeSending() {
        when(users.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> sendOtp.send("sms", PHONE, "register"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, ErrorCodes.VALIDATION_ERROR, AuthException.MESSAGE_KEY_ALREADY_REGISTERED));
        verify(otpService, never()).send(anyString(), anyString(), any());
    }

    @Test
    void registerOtpSendsWhenPhoneIsFree() {
        when(users.existsByPhone(PHONE)).thenReturn(false);
        when(otpService.send("sms", PHONE, OtpPurpose.REGISTER)).thenReturn(new OtpService.SendOtpResult(300, 60));

        OtpService.SendOtpResult result = sendOtp.send("sms", "17277889776", "register");

        assertThat(result.cooldown()).isEqualTo(60);
        verify(otpService).send("sms", PHONE, OtpPurpose.REGISTER);
    }

    @Test
    void loginOtpSendsWhenAccountExists() {
        when(users.existsByPhone(PHONE)).thenReturn(true);
        when(otpService.send("sms", PHONE, OtpPurpose.LOGIN)).thenReturn(new OtpService.SendOtpResult(300, 60));

        sendOtp.send("sms", PHONE, "login");

        verify(otpService).send("sms", PHONE, OtpPurpose.LOGIN);
    }

    @Test
    void rebindPhoneRejectsNumberOccupiedByAnotherUser() {
        when(sessions.currentUserId()).thenReturn(CURRENT_ID.toString());
        when(users.findByPhone("+8613800138000")).thenReturn(Optional.of(user(OTHER_ID, "+8613800138000", null)));

        assertThatThrownBy(() -> sendOtp.assertRecipientAllowed(OtpPurpose.REBIND_PHONE, "sms", "+8613800138000"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, ErrorCodes.VALIDATION_ERROR, AuthException.MESSAGE_KEY_IDENTITY_OCCUPIED));
    }

    @Test
    void rebindPhoneAllowsCurrentUsersOwnNumber() {
        when(sessions.currentUserId()).thenReturn(CURRENT_ID.toString());
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(user(CURRENT_ID, PHONE, null)));

        sendOtp.assertRecipientAllowed(OtpPurpose.REBIND_PHONE, "sms", PHONE);
    }

    @Test
    void rebindEmailRejectsAddressOccupiedByAnotherUser() {
        when(sessions.currentUserId()).thenReturn(CURRENT_ID.toString());
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user(OTHER_ID, null, EMAIL)));

        assertThatThrownBy(() -> sendOtp.assertRecipientAllowed(OtpPurpose.REBIND_EMAIL, "email", EMAIL))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertAuth((AuthException) e, ErrorCodes.VALIDATION_ERROR, AuthException.MESSAGE_KEY_IDENTITY_OCCUPIED));
    }

    @Test
    void rebindEmailAllowsUnoccupiedAddress() {
        when(sessions.currentUserId()).thenReturn(CURRENT_ID.toString());
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());

        sendOtp.assertRecipientAllowed(OtpPurpose.REBIND_EMAIL, "email", "new@example.com");
    }

    private static void assertAuth(AuthException error, String errorCode, String messageKey) {
        assertThat(error.errorCode()).isEqualTo(errorCode);
        assertThat(error.messageKey()).isEqualTo(messageKey);
    }

    private static User user(UUID id, String phone, String email) {
        Instant now = Instant.now();
        return new User(id, null, phone, email, "hash", "zh", now, now);
    }
}
