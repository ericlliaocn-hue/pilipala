package art.pilipala.api.job.application;

import java.util.UUID;

import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobRepository;
import org.springframework.stereotype.Service;

/**
 * 查询任务状态（给画布轮询用）。不触发生成。
 *
 * <p>他人租户的 job 与不存在的 job 一样返回 JOB_NOT_FOUND，不泄露存在性。
 *
 * <p>任务已是终态时顺手做一次幂等结算兜底：worker 刚写完终态，
 * 客户端立刻查 job 时账本就已经收敛（ADR 0004：结算只在 api 侧做）。
 */
@Service
public class GetJob {

    private final JobRepository jobs;
    private final HoldSettlement holds;
    private final I18nMessageService i18n;

    public GetJob(JobRepository jobs, HoldSettlement holds, I18nMessageService i18n) {
        this.jobs = jobs;
        this.holds = holds;
        this.i18n = i18n;
    }

    public JobView get(UUID tenantId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndTenant(jobId, tenantId).orElseThrow(JobException::notFound);
        holds.settle(job.holdId(), job.status());
        return JobView.of(job, message(job, locale));
    }

    /** 失败任务按当前语言给一句友好短句；成功任务没有 message。 */
    private String message(Job job, String locale) {
        return job.errorCode() == null ? null : i18n.resolve(job.errorCode(), locale);
    }
}
