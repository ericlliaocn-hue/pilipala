package art.pilipala.api.billing.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.Wallet;

import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包余额的读写。{@code wallet.user_id} 已在 {@code V24} 删除，钱包只按 {@code tenant_id} 定位。
 *
 * <p>五条金额 SQL 的公式是账本红线，改动金额语义需要在 brief 里说明，不要在业务步骤里顺手改。
 */
@Mapper
public interface WalletMapper {

    String COLS = "id, tenant_id, available, frozen, updated_at";

    @Select("SELECT " + COLS + " FROM wallet WHERE id = #{walletId}")
    Wallet findWallet(@Param("walletId") UUID walletId);

    @Select("SELECT " + COLS + " FROM wallet WHERE tenant_id = #{tenantId}")
    Wallet findByTenantId(@Param("tenantId") UUID tenantId);

    /** 钱包属于租户：给新租户开一只 0 余额钱包，返回新钱包 id。 */
    @Select("INSERT INTO wallet (tenant_id, available, frozen) VALUES (#{tenantId}, 0, 0) RETURNING id")
    UUID openForTenant(@Param("tenantId") UUID tenantId);

    @Update("UPDATE wallet SET available = available - #{amount}, frozen = frozen + #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND available >= #{amount}")
    int reserve(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET available = available + #{amount}, updated_at = now() WHERE id = #{walletId}")
    int addAvailable(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET available = available - #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND available >= #{amount}")
    int deductAvailable(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET frozen = frozen - #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND frozen >= #{amount}")
    int consumeFrozen(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET available = available + #{amount}, frozen = frozen - #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND frozen >= #{amount}")
    int releaseFrozen(@Param("walletId") UUID walletId, @Param("amount") long amount);
}
