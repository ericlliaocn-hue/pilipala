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
import art.pilipala.api.billing.application.TopUpWallet;
import org.springframework.stereotype.Service;

@Service
public class RegisterUser {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Set<String> LOCALES = Set.of("zh", "en");

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final TopUpWallet topUpWallet;
    private final AdminBootstrap adminBootstrap;
    private final OtpService otpService;
    private final EmailAuthPolicy emailAuthPolicy;

    public RegisterUser(UserRepository users, PasswordHasher hasher, AuthSessionService sessions, TopUpWallet topUpWallet,
            AdminBootstrap adminBootstrap, OtpService otpService, EmailAuthPolicy emailAuthPolicy) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.topUpWallet = topUpWallet;
        this.adminBootstrap = adminBootstrap;
        this.otpService = otpService;
        this.emailAuthPolicy = emailAuthPolicy;
    }

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
        topUpWallet.ensureInitialWallet(user.id());
        return sessions.start(adminBootstrap.ensureOwner(user));
    }
}
