package art.pilipala.api.auth.application;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.LoginLog;
import art.pilipala.api.auth.infra.PasswordHasher;
import org.springframework.stereotype.Service;

/**
 * 登录。
 *
 * <p>顺序：请求形状不合法 → 落用户 → 停用 → 验证码 / 密码 → 引导所有者 → 发会话。
 * 形状不合法不写 {@code login_log}；其余失败写 {@code failure}，成功后写 {@code success}。
 * 日志只记用户、租户、结果与 ip，不记验证码、密码、令牌。
 */
@Service
public class LoginUser {

    private final UserLookup lookup;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;
    private final OtpService otpService;
    private final EmailAuthPolicy emailAuthPolicy;
    private final PersonalTenants personalTenants;
    private final LoginLog loginLog;

    public LoginUser(UserLookup lookup, PasswordHasher hasher, AuthSessionService sessions, AdminBootstrap adminBootstrap,
            OtpService otpService, EmailAuthPolicy emailAuthPolicy, PersonalTenants personalTenants, LoginLog loginLog) {
        this.lookup = lookup;
        this.hasher = hasher;
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
        this.otpService = otpService;
        this.emailAuthPolicy = emailAuthPolicy;
        this.personalTenants = personalTenants;
        this.loginLog = loginLog;
    }

    /**
     * @param ip 客户端 ip，由 {@code AuthController} 用 {@code ClientIpResolver} 取出；取不到为空
     */
    public AuthSession login(LoginCommand command, String ip) {
        String rawPhone = command.phone();
        String rawEmail = command.email();
        boolean hasPhone = rawPhone != null && !rawPhone.isBlank();
        boolean hasEmail = rawEmail != null && !rawEmail.isBlank();
        if (hasPhone == hasEmail) {
            throw AuthException.validation();
        }
        if (hasEmail) {
            emailAuthPolicy.assertLoginWithEmail();
        }
        boolean hasPassword = command.password() != null && !command.password().isBlank();
        boolean hasOtp = command.otp() != null && !command.otp().isBlank();
        if (hasPassword == hasOtp) {
            throw AuthException.validation();
        }

        Optional<User> found = lookup.byLoginIdentity(rawPhone, rawEmail);

        // 停用账号：不发令牌，也不引导平台所有者。
        if (found.isPresent() && found.get().disabled()) {
            User user = found.get();
            loginLog.record(user.id(), tenantIdOf(user), false, ip);
            throw AuthException.accountDisabled();
        }

        if (hasOtp) {
            if (found.isEmpty()) {
                loginLog.record(null, null, false, ip);
                throw AuthException.accountNotFound();
            }
            User user = found.get();
            String dest = user.phone() != null ? user.phone() : user.email();
            if (!otpService.verifyAndConsume(OtpPurpose.LOGIN, dest, command.otp())) {
                loginLog.record(user.id(), null, false, ip);
                throw AuthException.otpInvalid();
            }
            return startSession(user, ip);
        }

        String password = command.password();
        if (found.isEmpty()) {
            hasher.matchEqualizer(password);
            loginLog.record(null, null, false, ip);
            throw AuthException.unauthorized();
        }
        User user = found.get();
        if (!hasher.matches(password, user.passwordHash())) {
            loginLog.record(user.id(), null, false, ip);
            throw AuthException.unauthorized();
        }
        return startSession(user, ip);
    }

    /** 引导平台所有者 → 发会话 → 记一条成功日志。 */
    private AuthSession startSession(User user, String ip) {
        User current = adminBootstrap.ensureOwner(user);
        AuthSession session = sessions.start(current);
        loginLog.record(current.id(), tenantIdOf(current), true, ip);
        return session;
    }

    /** 该用户的当前个人租户；没有个人租户时日志里留空，不补建。 */
    private UUID tenantIdOf(User user) {
        return personalTenants.find(user.id()).orElse(null);
    }
}
