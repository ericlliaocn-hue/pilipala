package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminJobView;
import art.pilipala.api.admin.application.CancelAdminJob;
import art.pilipala.api.admin.application.ListAdminJobs;
import art.pilipala.api.admin.application.RetryAdminJob;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.application.JobView;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：任务排障与死信重投。
 *
 * <p>本控制器是 {@code provider_task.raw_status} / {@code raw_body} 的唯一出口，
 * C 端 {@code /api/v1/jobs/**} 严禁带这两个字段（ADR 0005）。
 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {

    private final AdminGuard guard;
    private final ListAdminJobs jobs;
    private final RetryAdminJob retryJob;
    private final CancelAdminJob cancelJob;

    public AdminJobController(AdminGuard guard, ListAdminJobs jobs, RetryAdminJob retryJob,
            CancelAdminJob cancelJob) {
        this.guard = guard;
        this.jobs = jobs;
        this.retryJob = retryJob;
        this.cancelJob = cancelJob;
    }

    /**
     * @param status 任务状态过滤；为空时不过滤，非法值按参数不合法处理
     * @param userId 用户过滤；为空时不过滤
     * @param limit 单页上限，内部收敛到 1..200
     */
    @GetMapping
    public ApiResponse<List<AdminJobView>> list(@RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "user_id", required = false) String userId,
                                                @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.require("job.read");
        return ApiResponse.ok(jobs.list(status, userIdOf(userId), limit, guard.has("job.provider_raw")));
    }

    /** 把 dead / failed 的任务重新入队。 */
    @PostMapping("/{jobId}/retry")
    public ApiResponse<AdminJobView> retry(@PathVariable String jobId) {
        guard.require("job.retry");
        return ApiResponse.ok(retryJob.retry(jobIdOf(jobId)));
    }

    /** 后台取消：按 job id 全局查，只取消非终态；终态回 VALIDATION_ERROR。 */
    @PostMapping("/{jobId}/cancel")
    public ApiResponse<JobView> cancel(@PathVariable String jobId) {
        guard.require("job.cancel");
        return ApiResponse.ok(cancelJob.cancel(guard.currentUserId(), jobIdOf(jobId)));
    }

    /** 查询参数里的 user id 非法时按参数不合法处理；为空表示不过滤。 */
    private UUID userIdOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }

    /** 路径上的 job id 非法时按「不存在」处理，不泄露格式细节。 */
    private UUID jobIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.notFound();
        }
    }
}
