package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.PendingSettlement;
import art.pilipala.api.billing.domain.WalletHold;

@Mapper
public interface WalletHoldMapper {

    String COLS = "id, tenant_id, user_id, job_id, amount, status, created_at, finalized_at";

    /** {@code tenant_id} 是被冻结钱包所属租户，{@code user_id} 是操作者。 */
    @Select("INSERT INTO wallet_hold (tenant_id, user_id, job_id, amount, status) VALUES "
            + "(#{tenantId}, #{userId}, #{jobId,jdbcType=OTHER}, #{amount}, 'frozen') RETURNING " + COLS)
    WalletHold insert(
            @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("jobId") UUID jobId,
            @Param("amount") long amount);

    @Select("SELECT " + COLS + " FROM wallet_hold WHERE id = #{holdId}")
    WalletHold findHold(@Param("holdId") UUID holdId);

    @Select("UPDATE wallet_hold SET status = #{target}, finalized_at = now() "
            + "WHERE id = #{holdId} AND status = 'frozen' RETURNING " + COLS)
    WalletHold finalizeIfFrozen(@Param("holdId") UUID holdId, @Param("target") String target);

    @Update("UPDATE wallet_hold SET job_id = #{jobId} WHERE id = #{holdId} AND job_id IS NULL")
    int bindJob(@Param("holdId") UUID holdId, @Param("jobId") UUID jobId);

    @Select("SELECT h.id AS hold_id, h.tenant_id, h.job_id, h.amount, j.status AS job_state "
            + "FROM wallet_hold h JOIN job j ON j.id = h.job_id "
            + "WHERE h.status = 'frozen' AND j.status IN ('succeeded', 'cancelled', 'dead') "
            + "ORDER BY h.created_at LIMIT #{limit}")
    List<PendingSettlement> findPendingSettlementAll(@Param("limit") int limit);

    @Select("SELECT h.id AS hold_id, h.tenant_id, h.job_id, h.amount, j.status AS job_state "
            + "FROM wallet_hold h JOIN job j ON j.id = h.job_id "
            + "WHERE h.status = 'frozen' AND j.status IN ('succeeded', 'cancelled', 'dead') "
            + "AND h.tenant_id = #{tenantId} ORDER BY h.created_at LIMIT #{limit}")
    List<PendingSettlement> findPendingSettlementByTenant(@Param("tenantId") UUID tenantId, @Param("limit") int limit);
}
