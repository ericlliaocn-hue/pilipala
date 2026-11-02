package art.pilipala.api.billing.application;

import java.util.UUID;

import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.billing.infra.ZpayClient;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundRechargeOrder {

    private final RechargeOrderRepository orders;
    private final WalletService wallets;
    private final ZpayClient zpay;

    public RefundRechargeOrder(RechargeOrderRepository orders, WalletService wallets, ZpayClient zpay) {
        this.orders = orders;
        this.wallets = wallets;
        this.zpay = zpay;
    }

    @Transactional
    public RechargeOrderView refund(UUID orderId) {
        RechargeOrder order = orders.findById(orderId).orElseThrow(JobException::rechargeOrderNotFound);
        if (order.status() != RechargeOrderStatus.PAID) {
            throw JobException.rechargeNotRefundable();
        }
        Wallet wallet = wallets.ensureWallet(order.tenantId());
        if (wallet.available() < order.credits()) {
            throw JobException.rechargeNotRefundable();
        }
        try {
            zpay.refundByOutTradeNo(order.outTradeNo(), order.moneyFen());
        } catch (JobException provider) {
            if (order.providerTradeNo() != null) {
                zpay.refundByTradeNo(order.providerTradeNo(), order.moneyFen());
            } else {
                throw provider;
            }
        }
        wallets.refundDebit(order.tenantId(), order.userId(), order.credits(), order.id());
        if (!orders.markRefunded(order.id())) {
            throw JobException.rechargeNotRefundable();
        }
        RechargeOrder refunded = orders.findById(orderId).orElseThrow();
        return RechargeOrderView.ofStatusOnly(refunded);
    }
}
