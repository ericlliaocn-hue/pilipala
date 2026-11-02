package art.pilipala.api.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.LoginLog;
import art.pilipala.api.auth.infra.PasswordHasher;
import art.pilipala.api.common.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 登录日志与停用账号：写一条什么、什么时候不写、失败时能不能碰到令牌。
 */
@ExtendWith(MockitoExtension.class)
class LoginAuditTest {

    private static final String PHONE = "+8617277889776";
    private static final String IP = "203.0.113.7";
    private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
    private PersonalTenants personalTenants;
    @Mock
    private LoginLog loginLog;

    @InjectMocks
    private LoginUser loginUser;

    @Test
    void aDisabledAccountGetsAccountDisabledAndNeverASession() {
        User disabled = user(User.STATUS_DISABLED);
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.of(disabled));
        when(personalTenants.find(disabled.id())).thenReturn(Optional.of(TENANT));

        assertThatThrownBy(() -> loginUser.login(new LoginCommand(PHONE, null, null, "132442"), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException error = (AuthException) e;
                    assertThat(error.errorCode()).isEqualTo(ErrorCodes.ACCOUNT_DISABLED);
                    assertThat(error.messageKey()).isEqualTo(ErrorCodes.ACCOUNT_DISABLED);
                });

        verify(sessions, never()).start(any());
        verify(adminBootstrap, never()).ensureOwner(any());
        verify(otpService, never()).verifyAndConsume(any(), anyString(), anyString());
        verify(loginLog).record(disabled.id(), TENANT, false, IP);
    }

    @Test
    void aWrongPasswordWritesAFailureRowWithTheUserIdOnly() {
        User active = user(User.STATUS_ACTIVE);
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.of(active));
        when(hasher.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> loginUser.login(new LoginCommand(PHONE, null, "wrong-password", null), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).errorCode()).isEqualTo(ErrorCodes.UNAUTHORIZED));

        // 日志的入参只有用户、租户、结果、ip：验证码与密码没有可写的位置。
        verify(loginLog).record(active.id(), null, false, IP);
        verify(sessions, never()).start(any());
    }

    @Test
    void aMalformedRequestWritesNoLoginLog() {
        assertThatThrownBy(() -> loginUser.login(
                new LoginCommand(PHONE, "user@example.com", null, "132442"), IP))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).errorCode()).isEqualTo(ErrorCodes.VALIDATION_ERROR));

        verifyNoInteractions(loginLog);
    }

    @Test
    void aSuccessfulOtpLoginWritesSuccessWithTheCurrentTenant() {
        User active = user(User.STATUS_ACTIVE);
        AuthSession session = new AuthSession(active.id(), null, null, "zh", "token", "user");
        when(lookup.byLoginIdentity(PHONE, null)).thenReturn(Optional.of(active));
        when(otpService.verifyAndConsume(OtpPurpose.LOGIN, PHONE, "132442")).thenReturn(true);
        when(adminBootstrap.ensureOwner(active)).thenReturn(active);
        when(sessions.start(active)).thenReturn(session);
        when(personalTenants.find(active.id())).thenReturn(Optional.of(TENANT));

        assertThat(loginUser.login(new LoginCommand(PHONE, null, null, "132442"), IP)).isSameAs(session);

        verify(loginLog).record(active.id(), TENANT, true, IP);
    }

    private static User user(String status) {
        Instant now = Instant.now();
        return new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), null, PHONE, null, "hash", "zh",
                status, now, now);
    }
}
