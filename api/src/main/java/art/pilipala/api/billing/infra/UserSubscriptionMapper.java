package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.UserSubscription;

@Mapper
public interface UserSubscriptionMapper {

    String COLS = "id, user_id, tenant_id, plan_id, plan_code, price_id, cycle, variant_code, status, started_at, "
            + "current_period_start, current_period_end, seats, credits_per_month, last_grant_at, source_order_id, "
            + "created_at, updated_at";

    /** 该租户当前有效订阅，取最新一条活跃记录（同租户多条时不合并、不额外删除）。 */
    @Select("SELECT " + COLS + " FROM user_subscription WHERE tenant_id = #{tenantId} AND status = 'active' "
            + "ORDER BY created_at DESC LIMIT 1")
    UserSubscription findActiveByTenant(@Param("tenantId") UUID tenantId);

    @Select("SELECT " + COLS + " FROM user_subscription WHERE status = 'active' "
            + "AND current_period_end > now() ORDER BY id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<UserSubscription> listActiveForGrant(@Param("limit") int limit);

    @Insert("INSERT INTO user_subscription (user_id, tenant_id, plan_id, plan_code, price_id, cycle, variant_code, "
            + "status, started_at, current_period_start, current_period_end, seats, credits_per_month, last_grant_at, "
            + "source_order_id) VALUES (#{userId}, #{tenantId}, #{planId}, #{planCode}, #{priceId}, #{cycle}, "
            + "#{variantCode}, "
            + "#{status}, #{startedAt}, #{currentPeriodStart}, #{currentPeriodEnd}, #{seats}, #{creditsPerMonth}, "
            + "#{lastGrantAt}, #{sourceOrderId}) RETURNING " + COLS)
    UserSubscription insert(@Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            @Param("planId") long planId,
            @Param("planCode") String planCode,
            @Param("priceId") long priceId,
            @Param("cycle") String cycle,
            @Param("variantCode") String variantCode,
            @Param("status") String status,
            @Param("startedAt") Instant startedAt,
            @Param("currentPeriodStart") Instant currentPeriodStart,
            @Param("currentPeriodEnd") Instant currentPeriodEnd,
            @Param("seats") int seats,
            @Param("creditsPerMonth") long creditsPerMonth,
            @Param("lastGrantAt") Instant lastGrantAt,
            @Param("sourceOrderId") UUID sourceOrderId);

    @Update("UPDATE user_subscription SET status = 'expired', updated_at = now() WHERE tenant_id = #{tenantId} "
            + "AND status = 'active'")
    int expireActiveForTenant(@Param("tenantId") UUID tenantId);

    @Update("UPDATE user_subscription SET last_grant_at = #{lastGrantAt}, updated_at = now() WHERE id = #{id}")
    int updateLastGrantAt(@Param("id") long id, @Param("lastGrantAt") Instant lastGrantAt);

    @Update("UPDATE user_subscription SET status = 'expired', updated_at = now() WHERE id = #{id} AND status = 'active' "
            + "AND current_period_end <= #{now}")
    int expireIfEnded(@Param("id") long id, @Param("now") Instant now);
}
