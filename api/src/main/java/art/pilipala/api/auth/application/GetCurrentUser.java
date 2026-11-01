package art.pilipala.api.auth.application;

import java.util.UUID;

import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.domain.IdentityMasker;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.auth.infra.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class GetCurrentUser {

    private final UserRepository users;
    private final AuthSessionService sessions;
    private final AdminBootstrap adminBootstrap;

    public GetCurrentUser(UserRepository users, AuthSessionService sessions, AdminBootstrap adminBootstrap) {
        this.users = users;
        this.sessions = sessions;
        this.adminBootstrap = adminBootstrap;
    }

    public CurrentUser current() {
        String loginId = sessions.currentUserId();
        UUID userId;
        try {
            userId = UUID.fromString(loginId);
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
        User user = users.findById(userId).orElseThrow(AuthException::unauthorized);
        return new CurrentUser(
                user.id(),
                IdentityMasker.maskPhone(user.phone()),
                IdentityMasker.maskEmail(user.email()),
                user.locale(),
                adminBootstrap.isPlatformStaff(user.id()) ? "admin" : "user");
    }
}
