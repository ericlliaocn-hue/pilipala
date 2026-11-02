package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.StaffView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运维后台：员工（有平台角色分配的用户）与平台角色替换。 */
@RestController
@RequestMapping("/api/v1/admin/staff")
public class AdminStaffController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminStaffController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping
    public ApiResponse<List<StaffView>> list() {
        guard.require("staff.read");
        return ApiResponse.ok(console.listStaff());
    }

    /** 替换该用户的平台角色集合；删到最后一个 platform_owner 时用例层拒绝。 */
    @PutMapping("/{userId}/roles")
    public ApiResponse<StaffView> replaceRoles(@PathVariable String userId, @RequestBody RolesRequest request) {
        guard.require("staff.assign_role");
        return ApiResponse.ok(console.replaceStaffRoles(guard.currentUserId(), userIdOf(userId),
                request.roleCodes()));
    }

    private UUID userIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }

    public record RolesRequest(List<String> roleCodes) {
    }
}
