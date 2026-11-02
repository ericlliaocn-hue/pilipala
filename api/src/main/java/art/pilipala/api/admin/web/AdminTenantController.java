package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.TenantMemberView;
import art.pilipala.api.admin.application.AdminConsoleViews.TenantView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运维后台：租户分页列表（可按 type / status 过滤）与租户成员。 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminTenantController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping
    public ApiResponse<List<TenantView>> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        guard.require("tenant.read");
        return ApiResponse.ok(console.listTenants(type, status, limit, offset));
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<TenantMemberView>> members(@PathVariable String id) {
        guard.require("tenant.member.read");
        return ApiResponse.ok(console.listTenantMembers(idOf(id)));
    }

    private UUID idOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }
}
