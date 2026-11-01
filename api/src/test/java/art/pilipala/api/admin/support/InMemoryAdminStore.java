package art.pilipala.api.admin.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import art.pilipala.api.admin.domain.AdminJob;
import art.pilipala.api.admin.domain.AdminUser;
import art.pilipala.api.admin.domain.ModelDisplayConfig;
import art.pilipala.api.admin.infra.AdminJobRepository;
import art.pilipala.api.admin.infra.AdminUserRepository;
import art.pilipala.api.admin.infra.ModelDisplayConfigRepository;
import art.pilipala.api.job.domain.ProviderTask;
import art.pilipala.api.job.infra.ProviderTaskRepository;

/**
 * 内存版运维仓储：Admin 用例测试不需要 Postgres。
 *
 * <p>四个仓储接口一起实现，方便用例拿到同一份数据。
 */
public class InMemoryAdminStore implements AdminUserRepository, AdminJobRepository, ModelDisplayConfigRepository,
        ProviderTaskRepository {

    private final Map<UUID, AdminUser> users = new LinkedHashMap<>();
    private final Map<UUID, AdminJob> jobs = new LinkedHashMap<>();
    private final Map<UUID, ModelDisplayConfig> models = new LinkedHashMap<>();
    private final List<ProviderTask> providerTasks = new ArrayList<>();
    private java.util.function.Function<UUID, AdminJob> liveJobs;

    // ----- AdminUserRepository -----

    @Override
    public List<AdminUser> findUsers(String query, int limit) {
        String keyword = query == null ? "" : query.trim().toLowerCase();
        return users.values().stream()
                .filter(user -> keyword.isEmpty() || user.username().toLowerCase().contains(keyword))
                .sorted(Comparator.comparing(AdminUser::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    // ----- AdminJobRepository -----

    @Override
    public List<AdminJob> findJobs(String status, UUID userId, int limit) {
        return jobRows().stream()
                .filter(job -> status == null || status.isBlank() || job.status().equals(status))
                .filter(job -> userId == null || job.userId().equals(userId))
                .sorted(Comparator.comparing(AdminJob::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<AdminJob> findJob(UUID jobId) {
        if (liveJobs != null) {
            return Optional.ofNullable(liveJobs.apply(jobId));
        }
        return Optional.ofNullable(jobs.get(jobId));
    }

    // ----- ModelDisplayConfigRepository -----

    @Override
    public List<ModelDisplayConfig> findAll() {
        return models.values().stream()
                .sorted(Comparator.comparingInt(ModelDisplayConfig::sortOrder).thenComparing(ModelDisplayConfig::model))
                .toList();
    }

    @Override
    public List<ModelDisplayConfig> findEnabled() {
        return findAll().stream().filter(ModelDisplayConfig::enabled).toList();
    }

    @Override
    public Optional<ModelDisplayConfig> findByModel(String model) {
        return models.values().stream().filter(config -> config.model().equals(model)).findFirst();
    }

    @Override
    public ModelDisplayConfig upsert(String model, String capability, String displayName, boolean enabled, int sortOrder) {
        ModelDisplayConfig config = new ModelDisplayConfig(UUID.randomUUID(), model, capability, displayName, enabled,
                sortOrder, Instant.now());
        models.values().removeIf(existing -> existing.model().equals(model));
        models.put(config.id(), config);
        return config;
    }

    // ----- ProviderTaskRepository -----

    @Override
    public List<ProviderTask> findByJobId(UUID jobId) {
        return providerTasks.stream().filter(task -> task.jobId().equals(jobId)).toList();
    }

    @Override
    public List<ProviderTask> findByJobIds(List<UUID> jobIds) {
        return providerTasks.stream()
                .filter(task -> jobIds.contains(task.jobId()))
                .sorted(Comparator.comparing(ProviderTask::createdAt))
                .toList();
    }

    // ----- 测试辅助 -----

    /**
     * 让任务行实时从 job 仓储读：模拟 JDBC 的「读己之写」。
     *
     * <p>没有它的话，重投后从 Admin 仓储读到的还是旧状态，
     * 与真实数据库的行为不一致。解析器负责把 Job 投影成 AdminJob
     * （用户名、prompt 这类静态信息由它自己带）。
     */
    public void useLiveJobs(java.util.function.Function<UUID, AdminJob> resolver) {
        this.liveJobs = resolver;
    }

    /** 原始任务行（不经过 liveJobs 解析）。 */
    public AdminJob storedJob(UUID jobId) {
        return jobs.get(jobId);
    }

    private List<AdminJob> jobRows() {
        if (liveJobs == null) {
            return List.copyOf(jobs.values());
        }
        return jobs.keySet().stream().map(liveJobs).filter(java.util.Objects::nonNull).toList();
    }

    /** 放一个用户。 */
    public AdminUser putUser(String username, long available, long frozen) {
        Instant now = Instant.now();
        AdminUser user = new AdminUser(UUID.randomUUID(), username, "zh", available, frozen, now, now);
        users.put(user.userId(), user);
        return user;
    }

    /** 放一条任务。 */
    public AdminJob putJob(UUID jobId, UUID userId, String username, String status, String prompt, int attempt,
            int maxAttempts, UUID holdId, String holdStatus) {
        Instant now = Instant.now();
        AdminJob job = new AdminJob(jobId, userId, username, status, "image.generate", "gpt-image-2",
                prompt, attempt, maxAttempts, holdId, holdStatus, "PROVIDER_UNAVAILABLE", now, now);
        jobs.put(job.jobId(), job);
        return job;
    }

    /** 放一条上游留痕。 */
    public ProviderTask putProviderTask(UUID jobId, UUID jobStepId, String upstreamRequestId, String rawStatus,
            JsonNode rawBody) {
        ProviderTask task = new ProviderTask(UUID.randomUUID(), jobId, jobStepId, upstreamRequestId, rawStatus, rawBody,
                Instant.now());
        providerTasks.add(task);
        return task;
    }
}
