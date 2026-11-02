package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;

@Repository
@Primary
public class MybatisJobRepository implements JobRepository {

    private final JobMapper mapper;

    public MybatisJobRepository(JobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Job> insertIfAbsent(
            UUID tenantId, UUID userId, String capability, String model, UUID productId, UUID targetId,
            String gatewayGroup, String gatewayModel, Integer gatewayTimeoutSeconds, Long quotedPrice,
            String pricingVersion, String paramsJson, String idempotencyKey, UUID holdId) {
        return Optional.ofNullable(mapper.insertIfAbsent(tenantId, userId, capability, model, productId, targetId,
                gatewayGroup, gatewayModel, gatewayTimeoutSeconds, quotedPrice, pricingVersion, paramsJson,
                idempotencyKey, holdId));
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        return Optional.ofNullable(mapper.findById(jobId));
    }

    @Override
    public Optional<Job> findByIdAndTenant(UUID jobId, UUID tenantId) {
        return Optional.ofNullable(mapper.findByIdAndTenant(jobId, tenantId));
    }

    @Override
    public Optional<Job> claim(UUID jobId) {
        return Optional.ofNullable(mapper.claim(jobId));
    }

    @Override
    public boolean updateStatus(UUID jobId, JobStatus from, JobStatus to, String errorCode) {
        return mapper.updateStatus(jobId, from.value(), to.value(), errorCode) > 0;
    }

    @Override
    public boolean updateResult(UUID jobId, String resultObjectKeysJson) {
        return mapper.updateResult(jobId, resultObjectKeysJson) > 0;
    }

    @Override
    public boolean requestCancel(UUID jobId) {
        return mapper.requestCancel(jobId) > 0;
    }

    @Override
    public List<Job> findByTenantOrderByCreatedAtDesc(UUID tenantId, int limit) {
        return mapper.findByTenantOrderByCreatedAtDesc(tenantId, limit);
    }

    @Override
    public boolean requeueForAdmin(UUID jobId, boolean clearHoldId) {
        return mapper.requeueForAdmin(jobId, clearHoldId) > 0;
    }
}
