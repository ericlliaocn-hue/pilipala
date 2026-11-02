package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.CreditGrant;

@Mapper
public interface CreditGrantMapper {

    String COLS = "id, user_id, wallet_id, tenant_id, source_type, source_id, grant_type, credits_total, "
            + "credits_remaining, expires_at, product_id, created_at";

    @Insert("INSERT INTO credit_grant (user_id, wallet_id, tenant_id, source_type, source_id, grant_type, "
            + "credits_total, credits_remaining, expires_at, product_id) VALUES (#{userId}, #{walletId}, #{tenantId}, "
            + "#{sourceType}, #{sourceId}, #{grantType}, #{creditsTotal}, #{creditsRemaining}, #{expiresAt}, "
            + "#{productId}) RETURNING " + COLS)
    CreditGrant insert(@Param("userId") UUID userId,
            @Param("walletId") UUID walletId,
            @Param("tenantId") UUID tenantId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") String sourceId,
            @Param("grantType") String grantType,
            @Param("creditsTotal") long creditsTotal,
            @Param("creditsRemaining") long creditsRemaining,
            @Param("expiresAt") Instant expiresAt,
            @Param("productId") Long productId);

    @Select("SELECT " + COLS + " FROM credit_grant WHERE wallet_id = #{walletId} AND credits_remaining > 0 "
            + "AND (expires_at IS NULL OR expires_at > now()) "
            + "ORDER BY expires_at NULLS LAST, created_at, id FOR UPDATE")
    List<CreditGrant> listConsumableForUpdate(@Param("walletId") UUID walletId);

    @Update("UPDATE credit_grant SET credits_remaining = #{remaining} WHERE id = #{id}")
    int updateRemaining(@Param("id") long id, @Param("remaining") long remaining);

    @Select("SELECT " + COLS + " FROM credit_grant WHERE user_id = #{userId} "
            + "ORDER BY created_at DESC LIMIT #{limit}")
    List<CreditGrant> listByUser(@Param("userId") UUID userId, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM credit_grant WHERE credits_remaining > 0 AND expires_at IS NOT NULL "
            + "AND expires_at < #{before} ORDER BY expires_at LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<CreditGrant> findExpiredForUpdate(@Param("before") Instant before, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM credit_grant WHERE wallet_id = #{walletId} AND credits_remaining > 0 "
            + "AND (expires_at IS NULL OR expires_at > now()) ORDER BY expires_at NULLS LAST, created_at LIMIT #{limit}")
    List<CreditGrant> listActiveSummary(@Param("walletId") UUID walletId, @Param("limit") int limit);

    @Select("SELECT " + COLS + " FROM credit_grant WHERE tenant_id = #{tenantId} AND grant_type = 'subscription' "
            + "AND credits_remaining > 0 FOR UPDATE")
    List<CreditGrant> listSubscriptionRemainingForUpdate(@Param("tenantId") UUID tenantId);
}
