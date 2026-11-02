package art.pilipala.api.billing.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.billing.domain.PendingSettlement;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;

/**
 * 冻结单的存取契约。
 *
 * <p>{@link #finalizeIfFrozen} 是 confirm / release 的幂等闸门：只有仍处于
 * {@code frozen} 的冻结单能被终结，终态只进一次。
 */
public interface WalletHoldRepository {

    /** 新建一张 status=frozen 的冻结单；jobId 未绑定时传 null。 */
    WalletHold insert(UUID tenantId, UUID userId, UUID jobId, long amount);

    Optional<WalletHold> findHold(UUID holdId);

    /**
     * 原子终结：{@code UPDATE ... WHERE id = ? AND status = 'frozen'}。
     *
     * @return 更新成功时返回终结后的冻结单；已是终态时返回空
     */
    Optional<WalletHold> finalizeIfFrozen(UUID holdId, WalletHoldStatus target);

    /** 回填关联的 job；已绑定时不覆盖。 */
    boolean bindJob(UUID holdId, UUID jobId);

    /**
     * 冻结单还冻着、但关联 job 已进终态的候选。
     *
     * @param tenantId 只看某个租户时传 id，否则传 null
     * @param limit    单批上限
     */
    List<PendingSettlement> findPendingSettlement(UUID tenantId, int limit);
}
