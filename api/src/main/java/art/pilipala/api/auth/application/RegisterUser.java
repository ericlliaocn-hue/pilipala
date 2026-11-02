package art.pilipala.api.auth.application;

import java.util.Set;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.IdentityNormalizer;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.PasswordHasher;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUser {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Set<String> LOCALES = Set.of("zh", "en");

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final CreatePersonalTenant personalTenant;
    private final AdminBootstrap adminBootstrap;
    private final OtpService otpService;
    private final EmailAuthPolicy emailAuthPolicy;

    public RegisterUser(UserRepository users, PasswordHasher hasher, AuthSessionService sessions,
            CreatePersonalTenant personalTenant, AdminBootstrap adminBootstrap, OtpService otpService,
            EmailAuthPolicy emailAuthPolicy) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.personalTenant = personalTenant;
        this.adminBootstrap = adminBootstrap;
        this.otpService = otpService;
        this.emailAuthPolicy = emailAuthPolicy;
    }

    /**
     * 注册。用户行、个人租户、成员、{@code tenant_owner}、钱包在同一个事务里，
     * 任一步失败一起回滚；成功后再引导平台所有者并发会话。
     */
    @Transactional
    public AuthSession register(RegisterCommand command) {
        String rawPhone = command.phone();
        String rawEmail = command.email();
        boolean hasPhone = rawPhone != null && !rawPhone.isBlank();
        boolean hasEmail = rawEmail != null && !rawEmail.isBlank();
        if (hasPhone == hasEmail) {
            throw AuthException.validation();
        }
        if (hasEmail) {
            emailAuthPolicy.assertRegisterWithEmail();
        }
        String phone = hasPhone ? IdentityNormalizer.normalizePhone(rawPhone) : null;
        String email = hasEmail ? IdentityNormalizer.normalizeEmail(rawEmail) : null;
        if (hasPhone && phone == null || hasEmail && email == null) {
            throw AuthException.validation();
        }
        String password = command.password() == null ? "" : command.password();
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw AuthException.validation();
        }
        String locale = command.locale() == null || command.locale().isBlank() ? null : command.locale().trim();
        if (locale != null && !LOCALES.contains(locale)) {
            throw AuthException.validation();
        }
        if (phone != null && users.existsByPhone(phone)) {
            throw AuthException.alreadyRegistered();
        }
        if (email != null && users.existsByEmail(email)) {
            throw AuthException.alreadyRegistered();
        }
        String otpDest = phone != null ? phone : email;
        if (!otpService.verifyAndConsume(OtpPurpose.REGISTER, otpDest, command.otp())) {
            throw AuthException.otpInvalid();
        }

        User user = users.insert(phone, email, hasher.hash(password), locale);
        personalTenant.create(user);
        return sessions.start(adminBootstrap.ensureOwner(user));
    }
}
