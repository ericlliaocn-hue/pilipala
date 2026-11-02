package art.pilipala.api.admin.application;

import java.util.UUID;

import art.pilipala.api.auth.application.AccessCheck;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import org.springframework.stereotype.Component;

/**
 * 运维后台的权限闸门。
 *
 * <p>登录校验由 WP3 的 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}：
 * 未登录时已经在闸门外被拦掉（UNAUTHORIZED）。这里按权限码逐接口判定：
 * 当前操作者在<b>平台范围</b>持有该码才放行，否则 FORBIDDEN。
 *
 * <p>不再有「有任意平台角色就全过」：{@code finance} 只有加额相关的码，改不动模型配置；
 * {@code support} 读不到上游原文。权限每次都从库里读（{@link AccessCheck}），
 * 降权后下一次请求就失效，不依赖会话里的旧值。
 */
@Component
public class AdminGuard {

    private final AuthSessionService sessions;
    private final AccessCheck access;

    public AdminGuard(AuthSessionService sessions, AccessCheck access) {
        this.sessions = sessions;
        this.access = access;
    }

    /**
     * 要求当前登录者在平台范围持有 {@code code}，否则抛异常。
     *
     * @throws AuthException 未登录（UNAUTHORIZED）或没有该权限码（FORBIDDEN）
     */
    public void require(String code) {
        if (!has(code)) {
            throw AuthException.forbidden();
        }
    }

    /** 当前登录者是否持有平台范围的 {@code code}；用于按权限裁剪同一份响应里的字段。 */
    public boolean has(String code) {
        return access.hasPlatform(currentUserId(), code);
    }

    /** 当前登录的操作者；会话里的 id 不合法时按未登录处理。 */
    public UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }
}
