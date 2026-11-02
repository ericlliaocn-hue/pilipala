package art.pilipala.api.job.support;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.IdempotencyRecord;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.domain.JobStep;
import art.pilipala.api.job.domain.JobStepStatus;
import art.pilipala.api.job.domain.OutboxMessage;
import art.pilipala.api.job.infra.IdempotencyRepository;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.job.infra.JobStepRepository;
import art.pilipala.api.job.infra.OutboxRepository;

/**
 * 内存版仓储：状态机测试不需要 Postgres / Redis / RabbitMQ。
 *
 * <p>行为对齐 {@code Jdbc*}Repository 的语义（唯一约束、状态守卫、claim 的原子迁移），
 * 只是把数据放在内存 Map 里。四个仓储接口一起实现，方便用例直接拿到同一份数据。
 */
public class InMemoryJobStore implements JobRepository, JobStepRepository, OutboxRepository, IdempotencyRepository {

    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final Map<UUID, JobStep> steps = new LinkedHashMap<>();
    private final Map<UUID, OutboxMessage> outbox = new LinkedHashMap<>();
    private final Map<String, IdempotencyRecord> idempotency = new LinkedHashMap<>();

    @Override
    public Optional<Job> insertIfAbsent(UUID tenantId, UUID userId, String capability, String model, UUID productId,
            UUID targetId,
            String gatewayGroup, String gatewayModel, Integer gatewayTimeoutSeconds, Long quotedPrice,
            String pricingVersion, String paramsJson, String idempotencyKey, UUID holdId) {
        boolean exists = jobs.values().stream()
                .anyMatch(job -> job.userId().equals(userId) && job.idempotencyKey().equals(idempotencyKey));
        if (exists) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Job job = new Job(UUID.randomUUID(), tenantId, userId, JobStatus.CREATED, capability, model, productId,
                targetId,
                gatewayGroup, gatewayModel, gatewayTimeoutSeconds, quotedPrice, pricingVersion, paramsJson,
                0, 3, holdId, idempotencyKey, null, null, null, now, now);
        jobs.put(job.id(), job);
        return Optional.of(job);
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<Job> findByIdAndTenant(UUID jobId, UUID tenantId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    @Override
    public Optional<Job> claim(UUID jobId) {
        Job job = jobs.get(jobId);
        if (job == null || job.status() != JobStatus.QUEUED) {
            return Optional.empty();
        }
        Job claimed = copy(job, JobStatus.RUNNING, job.errorCode(), job.attempt() + 1, job.cancelRequestedAt(), job.resultObjectKeysJson());
        jobs.put(jobId, claimed);
        return Optional.of(claimed);
    }

    @Override
    public boolean updateStatus(UUID jobId, JobStatus from, JobStatus to, String errorCode) {
        Job job = jobs.get(jobId);
        if (job == null || job.status() != from) {
            return false;
        }
        jobs.put(jobId, copy(job, to, errorCode, job.attempt(), job.cancelRequestedAt(), job.resultObjectKeysJson()));
        return true;
    }

    @Override
    public boolean updateResult(UUID jobId, String resultObjectKeysJson) {
        Job job = jobs.get(jobId);
        if (job == null || job.status() != JobStatus.RUNNING) {
            return false;
        }
        jobs.put(jobId, copy(job, job.status(), job.errorCode(), job.attempt(), job.cancelRequestedAt(), resultObjectKeysJson));
        return true;
    }

    @Override
    public boolean requestCancel(UUID jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return false;
        }
        if (job.cancelRequestedAt() == null) {
            jobs.put(jobId, copy(job, job.status(), job.errorCode(), job.attempt(), Instant.now(), job.resultObjectKeysJson()));
        }
        return true;
    }

    @Override
    public List<Job> findByTenantOrderByCreatedAtDesc(UUID tenantId, int limit) {
        return jobs.values().stream()
                .filter(job -> job.tenantId().equals(tenantId))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<JobStep> findByJobIds(List<UUID> jobIds) {
        return steps.values().stream()
                .filter(step -> jobIds.contains(step.jobId()))
                .sorted(Comparator.comparing(JobStep::jobId).thenComparingInt(JobStep::attempt))
                .toList();
    }

    @Override
    public boolean requeueForAdmin(UUID jobId, boolean clearHoldId) {
        Job job = jobs.get(jobId);
        if (job == null || (job.status() != JobStatus.DEAD && job.status() != JobStatus.FAILED)) {
            return false;
        }
        jobs.put(jobId, new Job(job.id(), job.tenantId(), job.userId(), JobStatus.QUEUED, job.capability(), job.model(),
                job.productId(), job.targetId(), job.gatewayGroup(), job.gatewayModel(), job.gatewayTimeoutSeconds(),
                job.quotedPrice(), job.pricingVersion(),
                job.paramsJson(), job.attempt(), Math.max(job.maxAttempts(), job.attempt()) + 1,
                clearHoldId ? null : job.holdId(), job.idempotencyKey(), null, null, null,
                job.createdAt(), Instant.now()));
        return true;
    }

    @Override
    public JobStep start(UUID jobId, int attempt) {
        JobStep step = new JobStep(UUID.randomUUID(), jobId, attempt, JobStepStatus.RUNNING, null, null, Instant.now(), null);
        steps.put(step.id(), step);
        return step;
    }

    @Override
    public void end(UUID jobId, int attempt, JobStepStatus status, String errorCode) {
        steps.values().stream()
                .filter(step -> step.jobId().equals(jobId) && step.attempt() == attempt)
                .findFirst()
                .ifPresent(step -> steps.put(step.id(),
                        new JobStep(step.id(), step.jobId(), step.attempt(), status, errorCode, step.providerTaskId(), step.startedAt(), Instant.now())));
    }

    @Override
    public OutboxMessage insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
        OutboxMessage message = new OutboxMessage(id, aggregateType, aggregateId, payloadJson, "pending", Instant.now(), null);
        outbox.put(id, message);
        return message;
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return outbox.values().stream().filter(message -> "pending".equals(message.status())).limit(limit).toList();
    }

    @Override
    public boolean markSent(UUID id) {
        OutboxMessage message = outbox.get(id);
        if (message == null || !"pending".equals(message.status())) {
            return false;
        }
        outbox.put(id, new OutboxMessage(message.id(), message.aggregateType(), message.aggregateId(), message.payloadJson(),
                "sent", message.createdAt(), Instant.now()));
        return true;
    }

    @Override
    public boolean markFailed(UUID id) {
        OutboxMessage message = outbox.get(id);
        if (message == null || !"pending".equals(message.status())) {
            return false;
        }
        outbox.put(id, new OutboxMessage(message.id(), message.aggregateType(), message.aggregateId(), message.payloadJson(),
                "failed", message.createdAt(), message.sentAt()));
        return true;
    }

    @Override
    public Optional<IdempotencyRecord> findByUserAndKey(UUID userId, String key) {
        return Optional.ofNullable(idempotency.get(userId + "|" + key));
    }

    @Override
    public void insert(UUID userId, String key, UUID jobId, String requestHash) {
        idempotency.put(userId + "|" + key,
                new IdempotencyRecord(UUID.randomUUID(), userId, key, jobId, requestHash, Instant.now()));
    }

    /** 所有 job 行，测试用来断言「没有重复插入」。 */
    public List<Job> allJobs() {
        return List.copyOf(jobs.values());
    }

    /** 所有 outbox 行。 */
    public List<OutboxMessage> allOutbox() {
        return List.copyOf(outbox.values());
    }

    /** 所有 step 行。 */
    public List<JobStep> allSteps() {
        return List.copyOf(steps.values());
    }

    /** 任务当前状态的小写值；钱包对账器要知道任务是不是终态。 */
    public String statusOf(UUID jobId) {
        Job job = jobs.get(jobId);
        return job == null ? null : job.status().value();
    }

    private static Job copy(Job job, JobStatus status, String errorCode, int attempt, Instant cancelRequestedAt, String resultObjectKeysJson) {
        return new Job(job.id(), job.tenantId(), job.userId(), status, job.capability(), job.model(),
                job.productId(), job.targetId(), job.gatewayGroup(), job.gatewayModel(), job.gatewayTimeoutSeconds(),
                job.quotedPrice(), job.pricingVersion(),
                job.paramsJson(), attempt,
                job.maxAttempts(), job.holdId(), job.idempotencyKey(), cancelRequestedAt, errorCode,
                resultObjectKeysJson, job.createdAt(), Instant.now());
    }
}
