package art.pilipala.api.auth.application;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.IdentityNormalizer;
import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.PasswordHasher;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ResetPassword {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;
    private final OtpService otpService;
    private final UserLookup lookup;
    private final EmailAuthPolicy emailAuthPolicy;

    public ResetPassword(UserRepository users, PasswordHasher hasher, AuthSessionService sessions, AdminBootstrap adminBootstrap,
            OtpService otpService, UserLookup lookup, EmailAuthPolicy emailAuthPolicy) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
        this.otpService = otpService;
        this.lookup = lookup;
        this.emailAuthPolicy = emailAuthPolicy;
    }

    public AuthSession reset(String rawPhone, String rawEmail, String otp, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw AuthException.validation();
        }
        boolean hasPhone = rawPhone != null && !rawPhone.isBlank();
        boolean hasEmail = rawEmail != null && !rawEmail.isBlank();
        if (hasPhone == hasEmail) {
            throw AuthException.validation();
        }
        if (hasEmail) {
            emailAuthPolicy.assertLoginWithEmail();
        }
        String phone = hasPhone ? IdentityNormalizer.normalizePhone(rawPhone) : null;
        String email = hasEmail ? IdentityNormalizer.normalizeEmail(rawEmail) : null;
        if (hasPhone && phone == null || hasEmail && email == null) {
            throw AuthException.validation();
        }
        User user = lookup.byLoginIdentity(rawPhone, rawEmail).orElseThrow(AuthException::accountNotFound);
        String dest = phone != null ? phone : email;
        if (!otpService.verifyAndConsume(OtpPurpose.RESET_PASSWORD, dest, otp)) {
            throw AuthException.otpInvalid();
        }
        users.updatePasswordHash(user.id(), hasher.hash(newPassword));
        User refreshed = users.findById(user.id()).orElseThrow(AuthException::validation);
        return sessions.start(adminBootstrap.ensureOwner(refreshed));
    }
}
