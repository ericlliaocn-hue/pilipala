package art.pilipala.api.admin.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminJobRepository;
import art.pilipala.api.billing.infra.WalletHoldRepository;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobMessagePayload;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.job.infra.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：把 dead / failed 的任务重新入队。
 *
 * <p>三个必须守住的点：
 * <ol>
 *   <li><b>保留 attempt 与历史 step</b>：{@code job_step} 有 {@code UNIQUE(job_id, attempt)}
 *       约束，且 claim 会做 {@code attempt + 1}；重置 attempt 会让审计痕迹自相矛盾。
 *       {@code max_attempts} 改成 {@code GREATEST(max_attempts, attempt) + 1}。</li>
 *   <li><b>已结清的冻结单要解绑</b>：任务进 dead 时 {@code HoldSettlement} 可能已经把
 *       冻结单结算成 released；冻结单终态只进一次，重投时二次结算会被账本拒绝。
 *       所以 hold 不再是 frozen 时把 {@code job.hold_id} 置空。</li>
 *   <li><b>同事务写 outbox</b>：投递由既有 {@code OutboxDispatcher} 负责，
 *       消息体只有 {@code job_id} 与 {@code event_id}（ADR 0003）。</li>
 * </ol>
 */
@Service
public class RetryAdminJob {

    private final JobRepository jobs;
    private final WalletHoldRepository holds;
    private final OutboxRepository outbox;
    private final AdminJobRepository adminJobs;
    private final AdminJobViewAssembler assembler;

    public RetryAdminJob(JobRepository jobs, WalletHoldRepository holds, OutboxRepository outbox,
            AdminJobRepository adminJobs, AdminJobViewAssembler assembler) {
        this.jobs = jobs;
        this.holds = holds;
        this.outbox = outbox;
        this.adminJobs = adminJobs;
        this.assembler = assembler;
    }

    /**
     * 重投一个 dead / failed 任务。
     *
     * @param jobId 目标任务
     * @return 重投后的任务视图（status = queued）
     */
    @Transactional
    public AdminJobView retry(UUID jobId) {
        Job job = jobs.findById(jobId).orElseThrow(JobException::notFound);
        if (job.status() != JobStatus.DEAD && job.status() != JobStatus.FAILED) {
            throw JobException.validation();
        }
        if (!jobs.requeueForAdmin(jobId, holdSettled(job.holdId()))) {
            throw JobException.validation();
        }
        UUID eventId = UUID.randomUUID();
        outbox.insertPending(eventId, "job", jobId, JobMessagePayload.of(jobId, eventId));
        // 重投是运维自己的动作，raw 裁剪只针对 GET 列表；这里始终带原文。
        return assembler.assembleAll(List.of(adminJobs.findJob(jobId).orElseThrow(JobException::notFound)), true).get(0);
    }

    /** 冻结单还在冻着就保留 hold 关联；否则视为已结清，重投不再二次结算。 */
    private boolean holdSettled(UUID holdId) {
        if (holdId == null) {
            return false;
        }
        return holds.findHold(holdId).map(hold -> hold.status() != WalletHoldStatus.FROZEN).orElse(true);
    }
}
