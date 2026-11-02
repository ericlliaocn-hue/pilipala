package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.WalletLedgerEntry;

@Mapper
public interface WalletLedgerMapper {

    String COLS = "id, wallet_id, user_id, type, amount, hold_id, job_id, recharge_order_id, created_at";

    @Select("INSERT INTO wallet_ledger (wallet_id, user_id, tenant_id, type, amount, hold_id, job_id, recharge_order_id) "
            + "VALUES (#{walletId}, #{userId}, #{tenantId}, #{type}, #{amount}, #{holdId,jdbcType=OTHER}, "
            + "#{jobId,jdbcType=OTHER}, #{rechargeOrderId,jdbcType=OTHER}) RETURNING " + COLS)
    WalletLedgerEntry insert(
            @Param("tenantId") UUID tenantId,
            @Param("walletId") UUID walletId,
            @Param("userId") UUID userId,
            @Param("type") String type,
            @Param("amount") long amount,
            @Param("holdId") UUID holdId,
            @Param("jobId") UUID jobId,
            @Param("rechargeOrderId") UUID rechargeOrderId);

    @Update("UPDATE wallet_ledger SET job_id = #{jobId} WHERE hold_id = #{holdId} AND job_id IS NULL")
    int bindJobId(@Param("holdId") UUID holdId, @Param("jobId") UUID jobId);

    @Select("SELECT " + COLS + " FROM wallet_ledger WHERE user_id = #{userId} ORDER BY created_at, id")
    List<WalletLedgerEntry> findLedgerByUserId(@Param("userId") UUID userId);

    @Select("SELECT " + COLS + " FROM wallet_ledger WHERE user_id = #{userId} "
            + "ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<WalletLedgerEntry> findRecentByUserId(@Param("userId") UUID userId, @Param("limit") int limit);
}
