package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminConsoleViews.AuditLogView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运维后台：审计日志（只追加，这里只读出）。 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminAuditController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping
    public ApiResponse<List<AuditLogView>> list(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.require("audit.read");
        return ApiResponse.ok(console.listAuditLogs(limit));
    }
}
