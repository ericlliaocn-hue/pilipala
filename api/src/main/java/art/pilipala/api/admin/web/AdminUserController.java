package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.PhoneRevealView;
import art.pilipala.api.admin.application.AdminConsoleViews.UserStatusView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminUserView;
import art.pilipala.api.admin.application.ListAdminUsers;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：用户查询。
 *
 * <p>登录闸门由 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}；
 * 这里再过一道 {@link AdminGuard}：普通用户一律 FORBIDDEN。
 *
 * <p>失败一律走统一信封（HTTP 200 + ok=false + error_code + message）。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminGuard guard;
    private final ListAdminUsers users;
    private final ManageAdminConsole console;

    public AdminUserController(AdminGuard guard, ListAdminUsers users, ManageAdminConsole console) {
        this.guard = guard;
        this.users = users;
        this.console = console;
    }

    /**
     * @param query 用户名关键字；为空时列出全部
     * @param limit 单页上限，内部收敛到 1..200
     */
    @GetMapping
    public ApiResponse<List<AdminUserView>> list(@RequestParam(value = "q", required = false) String query,
                                                 @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.require("user.read");
        return ApiResponse.ok(users.list(query, limit));
    }

    /** 封禁：不能封禁自己；不删除钱包和画布，只改 users.status 并写审计。 */
    @PostMapping("/{id}/disable")
    public ApiResponse<UserStatusView> disable(@PathVariable String id) {
        guard.require("user.disable");
        return ApiResponse.ok(console.disableUser(guard.currentUserId(), userIdOf(id)));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<UserStatusView> enable(@PathVariable String id) {
        guard.require("user.enable");
        return ApiResponse.ok(console.enableUser(guard.currentUserId(), userIdOf(id)));
    }

    /** 返回完整手机号并写审计；不触发第二次短信。 */
    @PostMapping("/{id}/phone-reveal")
    public ApiResponse<PhoneRevealView> phoneReveal(@PathVariable String id) {
        guard.require("user.read_phone");
        return ApiResponse.ok(console.revealPhone(guard.currentUserId(), userIdOf(id)));
    }

    private UUID userIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }
}
