package art.pilipala.api.job.application;

import java.util.UUID;

import art.pilipala.api.common.ErrorCodes;
import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.domain.JobTransitions;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取消任务。
 *
 * <p>非终态：先写 {@code cancel_requested_at}；
 * 仍处于 created / queued → 直接 cancelled（同事务解冻退回）；
 * 已在 running → 保留意图，等 Claim 或驱动下一步时收尾成 cancelled。
 * 已终态 → 幂等返回当前状态，并顺手做一次幂等结算兜底。
 */
@Service
public class CancelJob {

    private final JobRepository jobs;
    private final HoldSettlement holds;
    private final I18nMessageService i18n;

    public CancelJob(JobRepository jobs, HoldSettlement holds, I18nMessageService i18n) {
        this.jobs = jobs;
        this.holds = holds;
        this.i18n = i18n;
    }

    @Transactional
    public JobView cancel(UUID tenantId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndTenant(jobId, tenantId).orElseThrow(JobException::notFound);
        if (job.status().isTerminal()) {
            holds.settle(job.holdId(), job.status());
            return JobView.of(job, message(job, locale));
        }

        jobs.requestCancel(jobId);
        if (job.status() == JobStatus.QUEUED || job.status() == JobStatus.CREATED) {
            JobTransitions.requireCancellable(job.status());
            jobs.updateStatus(jobId, job.status(), JobStatus.CANCELLED, ErrorCodes.JOB_CANCELLED);
            holds.settle(job.holdId(), JobStatus.CANCELLED);
            Job cancelled = jobs.findById(jobId).orElseThrow(JobException::notFound);
            return JobView.of(cancelled, message(cancelled, locale));
        }
        // running / failed：保留取消意图，等驱动下一步时收尾。
        return JobView.of(job, message(job, locale));
    }

    /** 失败任务按当前语言给一句友好短句；成功任务没有 message。 */
    private String message(Job job, String locale) {
        return job.errorCode() == null ? null : i18n.resolve(job.errorCode(), locale);
    }
}
