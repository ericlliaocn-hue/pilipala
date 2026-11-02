package art.pilipala.api.billing.web;

import java.util.UUID;

import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.billing.application.GetWallet;
import art.pilipala.api.billing.application.WalletView;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端钱包接口：查询当前租户的余额快照。
 *
 * <p>鉴权闸门由 WP3 的 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}，
 * 这里只负责从会话取 user_id（禁止信任请求体里的 user id）。
 *
 * <p>失败一律走统一信封（{@code ok=false} + {@code error_code} + {@code message}），
 * HTTP 状态码恒为 200。
 */
@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final AuthSessionService sessions;
    private final CurrentTenant currentTenant;
    private final GetWallet getWallet;

    public WalletController(AuthSessionService sessions, CurrentTenant currentTenant, GetWallet getWallet) {
        this.sessions = sessions;
        this.currentTenant = currentTenant;
        this.getWallet = getWallet;
    }

    @GetMapping
    public ApiResponse<WalletView> get(@RequestParam(value = "tenant_id", required = false) String tenantId) {
        UUID userId = currentUserId();
        return ApiResponse.ok(getWallet.get(userId, currentTenant.require(userId, tenantId)));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }
}
