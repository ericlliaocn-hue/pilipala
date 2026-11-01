package art.pilipala.api.auth.infra;

import cn.dev33.satoken.stp.StpUtil;
import art.pilipala.api.admin.infra.AdminBootstrap;
import art.pilipala.api.auth.application.AuthSession;
import art.pilipala.api.auth.domain.IdentityMasker;
import art.pilipala.api.auth.domain.User;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionService {

    private final AdminBootstrap adminBootstrap;

    public AuthSessionService(AdminBootstrap adminBootstrap) {
        this.adminBootstrap = adminBootstrap;
    }

    public AuthSession start(User user) {
        StpUtil.login(user.id().toString());
        return toSession(user, StpUtil.getTokenValue());
    }

    public AuthSession toSession(User user, String token) {
        return new AuthSession(
                user.id(),
                IdentityMasker.maskPhone(user.phone()),
                IdentityMasker.maskEmail(user.email()),
                user.locale(),
                token,
                adminBootstrap.isPlatformStaff(user.id()) ? "admin" : "user");
    }

    public String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    public void end() {
        StpUtil.logout();
    }
}
