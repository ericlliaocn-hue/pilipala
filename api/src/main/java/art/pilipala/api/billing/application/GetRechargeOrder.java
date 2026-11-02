package art.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.billing.infra.RechargeProperties;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

@Service
public class GetRechargeOrder {

    private static final long SYNC_COOLDOWN_MS = 3000;

    private final RechargeOrderRepository orders;
    private final RechargeProperties properties;
    private final ReconcileRechargeOrder reconcile;
    private final Map<String, Long> lastSyncMs = new ConcurrentHashMap<>();

    public GetRechargeOrder(
            RechargeOrderRepository orders, RechargeProperties properties, ReconcileRechargeOrder reconcile) {
        this.orders = orders;
        this.properties = properties;
        this.reconcile = reconcile;
    }

    public RechargeOrderView get(UUID tenantId, String outTradeNo) {
        RechargeOrder order = orders.findByOutTradeNo(outTradeNo).orElseThrow(JobException::rechargeOrderNotFound);
        if (!order.tenantId().equals(tenantId)) {
            throw JobException.rechargeOrderNotFound();
        }
        if (order.status() == RechargeOrderStatus.PENDING_PAY) {
            maybeSync(outTradeNo);
            order = orders.findByOutTradeNo(outTradeNo).orElseThrow(JobException::rechargeOrderNotFound);
        }
        Instant expireAt = order.createdAt().plus(properties.orderTtlMinutes(), ChronoUnit.MINUTES);
        return RechargeOrderView.of(order, expireAt);
    }

    private void maybeSync(String outTradeNo) {
        long now = System.currentTimeMillis();
        Long last = lastSyncMs.get(outTradeNo);
        if (last != null && now - last < SYNC_COOLDOWN_MS) {
            return;
        }
        lastSyncMs.put(outTradeNo, now);
        reconcile.trySyncPaid(outTradeNo);
    }
}
