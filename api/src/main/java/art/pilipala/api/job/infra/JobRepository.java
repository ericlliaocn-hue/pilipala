package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;

/**
 * job 表的存取契约。Postgres 是权威存储，实现在 {@link MybatisJobRepository}。
 *
 * <p>定义成接口是为了让应用层用例能在没有数据库的情况下被测试：
 * 状态机测试用内存实现 + FakeTransport 覆盖成功 / 重试 / 取消 / dead。
 * SQL 只出现在 infra 的 JDBC 实现里。
 */
public interface JobRepository {

    /**
     * 插入一条 status=created 的 job。同一 {@code (user_id, idempotency_key)} 已有行时
     * 不插入（并发同键的安全网）。job 归属 {@code tenantId}，{@code userId} 是提交者。
     *
     * @param holdId 钱包冻结单；未冻结时传 null
     * @return 新建的 job；同键已存在时返回空
     */
    default Optional<Job> insertIfAbsent(UUID tenantId, UUID userId, String capability, String model, UUID productId,
            UUID targetId, Long quotedPrice, String pricingVersion, String paramsJson, String idempotencyKey,
            UUID holdId) {
        return insertIfAbsent(tenantId, userId, capability, model, productId, targetId, null, null, null, quotedPrice,
                pricingVersion, paramsJson, idempotencyKey, holdId);
    }

    Optional<Job> insertIfAbsent(UUID tenantId, UUID userId, String capability, String model, UUID productId,
            UUID targetId, String gatewayGroup, String gatewayModel, Integer gatewayTimeoutSeconds, Long quotedPrice,
            String pricingVersion, String paramsJson, String idempotencyKey, UUID holdId);

    Optional<Job> findById(UUID jobId);

    /** 只查当前租户的 job：他人租户的 job 与不存在的 job 一样返回空。 */
    Optional<Job> findByIdAndTenant(UUID jobId, UUID tenantId);

    /**
     * queued → running 且 attempt+1。只有仍处于 queued 才会成功。
     */
    Optional<Job> claim(UUID jobId);

    /**
     * 按 from → to 迁移状态。from 不匹配时返回 false（说明状态已被别人改过）。
     *
     * @param errorCode 迁移后写入的错误码；成功迁移时传 null 表示清空
     */
    boolean updateStatus(UUID jobId, JobStatus from, JobStatus to, String errorCode);

    /** 成功时写 result_object_keys。 */
    boolean updateResult(UUID jobId, String resultObjectKeysJson);

    /** 记录取消意图（重复取消不覆盖首次时间）。 */
    boolean requestCancel(UUID jobId);

    /**
     * 当前租户的任务列表，按创建时间倒序。
     *
     * <p>WP10 起用于 C 端生图历史：换浏览器登录也能看到本租户的任务。
     */
    List<Job> findByTenantOrderByCreatedAtDesc(UUID tenantId, int limit);

    /**
     * 运维重投：把 dead / failed 拉回 queued，重新入队执行。
     *
     * <p>三个关键点（WP11）：
     * <ul>
     *   <li>保留 {@code attempt} 与历史 {@code job_step}（唯一约束 UNIQUE(job_id, attempt)，
     *       且 claim 会做 attempt+1）；</li>
     *   <li>{@code max_attempts} 变成 {@code GREATEST(max_attempts, attempt) + 1}；</li>
     *   <li>{@code cancel_requested_at} 与 {@code error_code} 清空。</li>
     * </ul>
     *
     * @param clearHoldId 原冻结单已结清（非 frozen）时置 {@code hold_id = NULL}，
     *                    避免二次结算同一张冻结单
     * @return 是否真的重投了（任务不存在或状态不是 dead/failed 时返回 false）
     */
    boolean requeueForAdmin(UUID jobId, boolean clearHoldId);
}
