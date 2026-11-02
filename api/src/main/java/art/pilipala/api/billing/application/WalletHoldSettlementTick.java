package art.pilipala.api.billing.application;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.billing.domain.PendingSettlement;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.infra.WalletHoldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 冻结单的对账结算：扫出「任务已终态但冻结单还冻着」的记录，逐笔幂等结算。
 *
 * <p>worker 只改 job.status，不碰钱包（ADR 0004），所以任务终态与冻结单终态之间
 * 存在一个窗口；这个用例负责把窗口补上。
 *
 * <p>每一笔结算跑在自己的短事务里：{@link WalletService#settleForJob} 通过代理调用，
 * 本类本身不带事务。
 */
@Service
public class WalletHoldSettlementTick {

    private static final Logger log = LoggerFactory.getLogger(WalletHoldSettlementTick.class);

    private final WalletHoldRepository holds;
    private final WalletService wallets;
    private final BillingProperties properties;

    public WalletHoldSettlementTick(WalletHoldRepository holds, WalletService wallets, BillingProperties properties) {
        this.holds = holds;
        this.wallets = wallets;
        this.properties = properties;
    }

    /**
     * 全量扫一轮待结算的冻结单。
     *
     * @return 本轮结算的条数
     */
    public int tick() {
        return settle(holds.findPendingSettlement(null, properties.settleBatchSize()));
    }

    /**
     * 只结算某个租户的待结算冻结单（读接口兜底用，保证客户端立刻看到最新余额）。
     *
     * @return 本轮结算的条数
     */
    public int settleForTenant(UUID tenantId) {
        return settle(holds.findPendingSettlement(tenantId, properties.settleBatchSize()));
    }

    private int settle(List<PendingSettlement> pending) {
        int done = 0;
        for (PendingSettlement item : pending) {
            try {
                wallets.settleForJob(item.holdId(), item.jobState());
                done++;
            } catch (RuntimeException e) {
                log.warn("冻结单结算失败 hold={} job={}", item.holdId(), item.jobId(), e);
            }
        }
        return done;
    }
}
