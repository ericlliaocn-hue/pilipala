package art.pilipala.api.admin.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import art.pilipala.api.admin.domain.AdminJob;
import art.pilipala.api.job.domain.JobStep;
import art.pilipala.api.job.domain.ProviderTask;
import art.pilipala.api.job.infra.JobStepRepository;
import art.pilipala.api.job.infra.ProviderTaskRepository;
import org.springframework.stereotype.Component;

/**
 * 把 {@link AdminJob} 组装成带步骤与上游留痕的 {@link AdminJobView}。
 *
 * <p>步骤与留痕按批取（两次查询），不要一页 N 次查询。
 * 上游原文（raw_status / raw_body）只从这里流向 {@code /api/v1/admin/**}（ADR 0005）。
 */
@Component
public class AdminJobViewAssembler {

    private final JobStepRepository steps;
    private final ProviderTaskRepository providerTasks;

    public AdminJobViewAssembler(JobStepRepository steps, ProviderTaskRepository providerTasks) {
        this.steps = steps;
        this.providerTasks = providerTasks;
    }

    /**
     * 组装一批任务的视图。
     *
     * @param includeProviderRaw 调用者是否还有 {@code job.provider_raw}；没有时
     *                           provider_tasks 照常返回，但 raw_status / raw_body 为 null
     */
    public List<AdminJobView> assembleAll(List<AdminJob> jobs, boolean includeProviderRaw) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        List<UUID> jobIds = jobs.stream().map(AdminJob::jobId).toList();
        Map<UUID, List<JobStep>> stepsByJob = steps.findByJobIds(jobIds).stream()
                .collect(Collectors.groupingBy(JobStep::jobId));
        Map<UUID, List<ProviderTask>> tasksByJob = providerTasks.findByJobIds(jobIds).stream()
                .collect(Collectors.groupingBy(ProviderTask::jobId));
        return jobs.stream()
                .map(job -> assemble(job,
                        stepsByJob.getOrDefault(job.jobId(), List.of()),
                        tasksByJob.getOrDefault(job.jobId(), List.of()),
                        includeProviderRaw))
                .toList();
    }

    private AdminJobView assemble(AdminJob job, List<JobStep> jobSteps, List<ProviderTask> tasks,
            boolean includeProviderRaw) {
        return new AdminJobView(
                job.jobId(),
                job.userId(),
                job.username(),
                job.status(),
                job.capability(),
                job.model(),
                job.prompt(),
                job.attempt(),
                job.maxAttempts(),
                job.holdId(),
                job.holdStatus(),
                job.errorCode(),
                job.createdAt(),
                job.updatedAt(),
                jobSteps.stream().map(step -> new AdminJobView.Step(
                        step.id(),
                        step.attempt(),
                        step.status().value(),
                        step.errorCode(),
                        step.providerTaskId(),
                        step.startedAt(),
                        step.endedAt())).toList(),
                tasks.stream().map(task -> new AdminJobView.ProviderTaskView(
                        task.id(),
                        task.jobStepId(),
                        task.upstreamRequestId(),
                        includeProviderRaw ? task.rawStatus() : null,
                        includeProviderRaw ? task.rawBody() : null,
                        task.createdAt())).toList());
    }
}
