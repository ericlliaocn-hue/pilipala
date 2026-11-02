package art.pilipala.api.billing.domain;

import java.util.UUID;

/**
 * 待结算的冻结单：冻结单还冻着，但关联的任务已经进了终态。
 *
 * <p>由后台对账器与钱包查询接口扫出来，再交给 {@code WalletService} 幂等结算。
 *
 * @param holdId   冻结单 id
 * @param tenantId 冻结单所属租户（钱包按它定位）
 * @param jobId    关联任务
 * @param amount   冻结额
 * @param jobState 任务终态
 */
public record PendingSettlement(
        UUID holdId,
        UUID tenantId,
        UUID jobId,
        long amount,
        JobTerminalState jobState) {
}
