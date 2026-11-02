package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.PermissionView;
import art.pilipala.api.admin.application.AdminConsoleViews.RoleView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：权限码与角色。种子角色（system=true）不能改权限、不能删，
 * 由用例层拒绝并回 VALIDATION_ERROR。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminRoleController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminRoleController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionView>> permissions() {
        guard.require("role.read");
        return ApiResponse.ok(console.listPermissions());
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> roles() {
        guard.require("role.read");
        return ApiResponse.ok(console.listRoles());
    }

    /** 只创建 system=false 的角色；权限码必须已存在且 scope 与角色一致。 */
    @PostMapping("/roles")
    public ApiResponse<RoleView> createRole(@RequestBody CreateRoleRequest request) {
        guard.require("role.write");
        return ApiResponse.ok(console.createRole(guard.currentUserId(), request.code(), request.name(),
                request.scope(), request.permissionCodes()));
    }

    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<RoleView> updatePermissions(@PathVariable String id,
            @RequestBody PermissionsRequest request) {
        guard.require("role.write");
        return ApiResponse.ok(console.updateRolePermissions(guard.currentUserId(), idOf(id),
                request.permissionCodes()));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<RoleView> deleteRole(@PathVariable String id) {
        guard.require("role.write");
        return ApiResponse.ok(console.deleteRole(guard.currentUserId(), idOf(id)));
    }

    private UUID idOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }

    public record CreateRoleRequest(String code, String name, String scope, List<String> permissionCodes) {
    }

    public record PermissionsRequest(List<String> permissionCodes) {
    }
}
