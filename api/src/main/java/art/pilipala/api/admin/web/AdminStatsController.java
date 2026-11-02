package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminConsoleViews.StatsExceptionView;
import art.pilipala.api.admin.application.AdminConsoleViews.StatsOverviewView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运维后台：统计概览与异常清单。直接查业务表，不建 analytics_event。 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminStatsController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    /** from / to 必填，跨度最长 31 天，超过由用例层回 VALIDATION_ERROR。 */
    @GetMapping("/overview")
    public ApiResponse<StatsOverviewView> overview(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        guard.require("stats.read");
        return ApiResponse.ok(console.statsOverview(from, to));
    }

    @GetMapping("/exceptions")
    public ApiResponse<List<StatsExceptionView>> exceptions() {
        guard.require("stats.read");
        return ApiResponse.ok(console.statsExceptions());
    }
}
