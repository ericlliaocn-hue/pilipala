package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.job.domain.Job;

@Mapper
public interface JobMapper {

    String COLS = "id, tenant_id, user_id, status, capability, model, product_id, target_id, gateway_group, gateway_model, "
            + "gateway_timeout_seconds, quoted_price, pricing_version, "
            + "params::text AS params_json, attempt, max_attempts, "
            + "hold_id, idempotency_key, cancel_requested_at, error_code, "
            + "result_object_keys::text AS result_object_keys_json, created_at, updated_at";

    @Select("INSERT INTO job (tenant_id, user_id, status, capability, model, product_id, target_id, gateway_group, gateway_model, "
            + "gateway_timeout_seconds, quoted_price, pricing_version, params, idempotency_key, hold_id) "
            + "VALUES (#{tenantId}, #{userId}, 'created', #{capability}, #{model}, #{productId,jdbcType=OTHER}, "
            + "#{targetId,jdbcType=OTHER}, #{gatewayGroup,jdbcType=VARCHAR}, #{gatewayModel,jdbcType=VARCHAR}, "
            + "#{gatewayTimeoutSeconds,jdbcType=INTEGER}, #{quotedPrice,jdbcType=BIGINT}, #{pricingVersion,jdbcType=VARCHAR}, "
            + "#{paramsJson,jdbcType=VARCHAR}::jsonb, #{idempotencyKey}, #{holdId,jdbcType=OTHER}) "
            + "ON CONFLICT (user_id, idempotency_key) DO NOTHING RETURNING " + COLS)
    Job insertIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("capability") String capability,
            @Param("model") String model,
            @Param("productId") UUID productId,
            @Param("targetId") UUID targetId,
            @Param("gatewayGroup") String gatewayGroup,
            @Param("gatewayModel") String gatewayModel,
            @Param("gatewayTimeoutSeconds") Integer gatewayTimeoutSeconds,
            @Param("quotedPrice") Long quotedPrice,
            @Param("pricingVersion") String pricingVersion,
            @Param("paramsJson") String paramsJson,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("holdId") UUID holdId);

    @Select("SELECT " + COLS + " FROM job WHERE id = #{jobId}")
    Job findById(@Param("jobId") UUID jobId);

    /** 只查当前租户的 job：他人租户的 job 与不存在的 job 一样返回空。 */
    @Select("SELECT " + COLS + " FROM job WHERE id = #{jobId} AND tenant_id = #{tenantId}")
    Job findByIdAndTenant(@Param("jobId") UUID jobId, @Param("tenantId") UUID tenantId);

    @Select("UPDATE job SET status = 'running', attempt = attempt + 1, updated_at = now() "
            + "WHERE id = #{jobId} AND status = 'queued' RETURNING " + COLS)
    Job claim(@Param("jobId") UUID jobId);

    @Update("UPDATE job SET status = #{to}, error_code = #{errorCode}, updated_at = now() "
            + "WHERE id = #{jobId} AND status = #{from}")
    int updateStatus(
            @Param("jobId") UUID jobId,
            @Param("from") String from,
            @Param("to") String to,
            @Param("errorCode") String errorCode);

    @Update("UPDATE job SET result_object_keys = #{resultObjectKeysJson,jdbcType=VARCHAR}::jsonb, updated_at = now() "
            + "WHERE id = #{jobId} AND status = 'running'")
    int updateResult(@Param("jobId") UUID jobId, @Param("resultObjectKeysJson") String resultObjectKeysJson);

    @Update("UPDATE job SET cancel_requested_at = COALESCE(cancel_requested_at, now()), updated_at = now() WHERE id = #{jobId}")
    int requestCancel(@Param("jobId") UUID jobId);

    @Select("SELECT " + COLS + " FROM job WHERE tenant_id = #{tenantId} ORDER BY created_at DESC LIMIT #{limit}")
    List<Job> findByTenantOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId, @Param("limit") int limit);

    @Update("<script>UPDATE job SET status = 'queued', error_code = NULL, cancel_requested_at = NULL, "
            + "max_attempts = GREATEST(max_attempts, attempt) + 1, updated_at = now() "
            + "<if test='clearHoldId'>, hold_id = NULL </if>"
            + "WHERE id = #{jobId} AND status IN ('dead', 'failed')</script>")
    int requeueForAdmin(@Param("jobId") UUID jobId, @Param("clearHoldId") boolean clearHoldId);
}
