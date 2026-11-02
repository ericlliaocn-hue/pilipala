package art.pilipala.api.admin.application;

import java.util.UUID;

import art.pilipala.api.admin.infra.AuditLogMapper;
import art.pilipala.api.billing.application.RechargeOrderView;
import art.pilipala.api.billing.application.RefundRechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：运维退款一笔充值订单，带幂等键与审计。
 *
 * <p>扣余额仍然走既有 {@link RefundRechargeOrder}（退款的扣减语义与渠道退款顺序都不在这里改）。
 *
 * <p>{@code (操作者, recharge.refund, 幂等键)} 只执行一次；重放时不再扣款、不再退渠道、
 * 也不写第二条审计，只返回第一次的订单视图。
 */
@Service
public class RefundAdminRechargeOrder {

    /** 幂等记录与审计共用的动作名。 */
    public static final String ACTION = "recharge.refund";

    private final RechargeOrderRepository orders;
    private final RefundRechargeOrder refund;
    private final AdminIdempotency idempotency;
    private final AuditLogMapper audit;

    public RefundAdminRechargeOrder(
            RechargeOrderRepository orders,
            RefundRechargeOrder refund,
            AdminIdempotency idempotency,
            AuditLogMapper audit) {
        this.orders = orders;
        this.refund = refund;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    /**
     * @param actorUserId 操作者（登录的运维）
     * @param orderId 退款订单
     * @param idempotencyKey 必填，1..64
     * @param reason 必填的操作原因，1..200，写进审计摘要
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RechargeOrderView refund(UUID actorUserId, UUID orderId, String idempotencyKey, String reason) {
        String key = AdminIdempotency.requireKey(idempotencyKey);
        String note = AdminIdempotency.requireReason(reason);
        RechargeOrder[] target = new RechargeOrder[1];
        RechargeOrderView view = idempotency.execute(actorUserId, ACTION, key, RechargeOrderView.class,
                () -> {
                    target[0] = orders.findById(orderId).orElseThrow(JobException::rechargeOrderNotFound);
                    return refund.refund(orderId);
                },
                ignored -> audit.insert(actorUserId, ACTION, target[0].tenantId(),
                        "recharge_order", orderId.toString(), key, note));
        return view;
    }
}
