package art.pilipala.api.admin.application;

import java.util.UUID;

import art.pilipala.api.admin.infra.AuditLogMapper;
import art.pilipala.api.job.application.CancelJob;
import art.pilipala.api.job.application.JobView;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台取消任务（job.cancel）。
 *
 * <p>与 C 端的区别有两处：按 job id 全局查，不按当前租户把任务藏起来；
 * 终态返回 {@code VALIDATION_ERROR}，不沿用 C 端 {@link CancelJob} 对终态的
 * 幂等成功返回。非终态完全复用 {@link CancelJob}：冻结释放走既有结算，
 * 不在这里手写余额。
 */
@Service
public class CancelAdminJob {

    private final JobRepository jobs;
    private final CancelJob cancelJob;
    private final AuditLogMapper audit;

    public CancelAdminJob(JobRepository jobs, CancelJob cancelJob, AuditLogMapper audit) {
        this.jobs = jobs;
        this.cancelJob = cancelJob;
        this.audit = audit;
    }

    @Transactional
    public JobView cancel(UUID actorUserId, UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(JobException::notFound);
        if (job.status().isTerminal()) {
            throw JobException.validation();
        }
        JobView view = cancelJob.cancel(job.tenantId(), jobId, "zh");
        audit.insert(actorUserId, "job.cancel", job.tenantId(), "job", jobId.toString(), null, "cancel job");
        return view;
    }
}
