package art.pilipala.api.auth.application;

import java.util.Optional;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.PasswordHasher;
import org.springframework.stereotype.Service;

@Service
public class LoginUser {

    private final UserLookup lookup;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;
    private final OtpService otpService;
    private final EmailAuthPolicy emailAuthPolicy;

    public LoginUser(UserLookup lookup, PasswordHasher hasher, AuthSessionService sessions, AdminBootstrap adminBootstrap,
            OtpService otpService, EmailAuthPolicy emailAuthPolicy) {
        this.lookup = lookup;
        this.hasher = hasher;
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
        this.otpService = otpService;
        this.emailAuthPolicy = emailAuthPolicy;
    }

    public AuthSession login(LoginCommand command) {
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
        if (hasOtp) {
            if (found.isEmpty()) {
                throw AuthException.accountNotFound();
            }
            User user = found.get();
            String dest = user.phone() != null ? user.phone() : user.email();
            if (!otpService.verifyAndConsume(OtpPurpose.LOGIN, dest, command.otp())) {
                throw AuthException.otpInvalid();
            }
            return sessions.start(adminBootstrap.ensureOwner(user));
        }

        String password = command.password();
        if (found.isEmpty()) {
            hasher.matchEqualizer(password);
            throw AuthException.unauthorized();
        }
        User user = found.get();
        if (!hasher.matches(password, user.passwordHash())) {
            throw AuthException.unauthorized();
        }
        return sessions.start(adminBootstrap.ensureOwner(user));
    }
}
