package art.pilipala.api.admin.web;

import art.pilipala.api.admin.application.AdminConsoleViews.SystemStatusView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminSystemStatus;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运维后台：系统状态（API / Worker / NewAPI + 待投递 outbox + running 任务数）。 */
@RestController
@RequestMapping("/api/v1/admin/status")
public class AdminStatusController {

    private final AdminGuard guard;
    private final AdminSystemStatus status;

    public AdminStatusController(AdminGuard guard, AdminSystemStatus status) {
        this.guard = guard;
        this.status = status;
    }

    @GetMapping
    public ApiResponse<SystemStatusView> status() {
        guard.require("status.read");
        return ApiResponse.ok(status.status());
    }
}
