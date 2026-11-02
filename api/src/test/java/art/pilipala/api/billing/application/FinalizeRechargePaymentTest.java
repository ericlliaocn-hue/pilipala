package art.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.support.TestPersonalTenants;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryCreditProductStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import org.junit.jupiter.api.Test;

class FinalizeRechargePaymentTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = TestPersonalTenants.TENANT;
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Test
    void idempotentPaidDoesNotDoubleTopup() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        StubOrders orders = new StubOrders(paidOrder());
        SettleRechargePayment settle = settle(store, orders);
        FinalizeRechargePayment finalize = new FinalizeRechargePayment(orders, settle);

        assertThat(finalize.applyPaid("flr_1", "t1", 1000)).isFalse();
        assertThat(store.findLedgerByUserId(USER)).isEmpty();
    }

    @Test
    void creditsOnceForPendingPay() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        StubOrders orders = new StubOrders(pendingOrder());
        SettleRechargePayment settle = settle(store, orders);
        FinalizeRechargePayment finalize = new FinalizeRechargePayment(orders, settle);

        assertThat(finalize.applyPaid("flr_1", "t1", 1000)).isTrue();
        assertThat(store.walletOf(TENANT).available()).isEqualTo(100);
        assertThat(store.findLedgerByUserId(USER).get(0).type()).isEqualTo(WalletLedgerType.TOPUP);
        assertThat(store.findLedgerByUserId(USER).get(0).rechargeOrderId()).isEqualTo(ORDER_ID);
        assertThat(finalize.applyPaid("flr_1", "t1", 1000)).isFalse();
        assertThat(store.walletOf(TENANT).available()).isEqualTo(100);
    }

    private static SettleRechargePayment settle(InMemoryWalletStore store, StubOrders orderRepo) {
        InMemoryCreditGrantStore grantStore = new InMemoryCreditGrantStore();
        CreditGrantService grantService = new CreditGrantService(grantStore, store, store);
        WalletService wallets = new WalletService(store, store, store, grantService);
        return new SettleRechargePayment(
                orderRepo,
                new InMemoryCreditProductStore(),
                wallets,
                grantService,
                new FirstPurchaseBonusPolicy(orderRepo),
                null);
    }

    private static RechargeOrder pendingOrder() {
        return new RechargeOrder(
                ORDER_ID, TENANT, USER, "flr_1", "zpay", null, "p10", 1000, 100, RechargeOrderStatus.PENDING_PAY,
                "127.0.0.1", null, null, null, "n", null, null, 0L, null, null, null, null,
                art.pilipala.api.billing.domain.RechargeOrderKind.RECHARGE, null, Instant.now(), Instant.now());
    }

    private static RechargeOrder paidOrder() {
        return new RechargeOrder(
                ORDER_ID, TENANT, USER, "flr_1", "zpay", "t1", "p10", 1000, 100, RechargeOrderStatus.PAID,
                "127.0.0.1", null, null, null, "n", null, null, 0L, UUID.randomUUID(), Instant.now(), null, null,
                art.pilipala.api.billing.domain.RechargeOrderKind.RECHARGE, null, Instant.now(), Instant.now());
    }

    private static final class StubOrders implements RechargeOrderRepository {
        private RechargeOrder order;

        StubOrders(RechargeOrder order) {
            this.order = order;
        }

        @Override
        public RechargeOrder insertCreated(RechargeOrder draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RechargeOrder> findByOutTradeNo(String outTradeNo) {
            return Optional.of(order);
        }

        @Override
        public Optional<RechargeOrder> findById(UUID id) {
            return Optional.of(order);
        }

        @Override
        public boolean markPendingPay(UUID id, String providerTradeNo, String payUrl, String qrcodeUrl) {
            return false;
        }

        @Override
        public boolean markFailed(UUID id, String reason) {
            return false;
        }

        @Override
        public boolean claimPaidIfPending(String outTradeNo, String providerTradeNo) {
            if (order.status() == RechargeOrderStatus.PAID) {
                return false;
            }
            order = copy(order, RechargeOrderStatus.PAID, providerTradeNo, order.ledgerId(), Instant.now());
            return true;
        }

        @Override
        public boolean attachLedger(String outTradeNo, UUID ledgerId) {
            order = copy(order, order.status(), order.providerTradeNo(), ledgerId, order.paidAt());
            return true;
        }

        private static RechargeOrder copy(
                RechargeOrder order,
                RechargeOrderStatus status,
                String providerTradeNo,
                UUID ledgerId,
                Instant paidAt) {
            return new RechargeOrder(
                    order.id(),
                    order.tenantId(),
                    order.userId(),
                    order.outTradeNo(),
                    order.provider(),
                    providerTradeNo,
                    order.packageId(),
                    order.moneyFen(),
                    order.credits(),
                    status,
                    order.clientIp(),
                    order.device(),
                    order.payUrl(),
                    order.qrcodeUrl(),
                    order.productName(),
                    order.productId(),
                    order.productCode(),
                    order.bonusCredits(),
                    ledgerId,
                    paidAt,
                    order.refundedAt(),
                    order.failReason(),
                    order.orderKind(),
                    order.planPriceId(),
                    order.createdAt(),
                    Instant.now());
        }

        @Override
        public boolean markClosed(UUID id) {
            return false;
        }

        @Override
        public boolean markRefunded(UUID id) {
            return false;
        }

        @Override
        public java.util.List<RechargeOrder> findPendingPayBefore(java.time.Instant before, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<RechargeOrder> findAdmin(UUID userId, String status, String outTradeNo, int limit) {
            return java.util.List.of();
        }

        @Override
        public int countPaidByUserAndProductCodeExcluding(UUID userId, String productCode, UUID excludeOrderId) {
            return 0;
        }

        @Override
        public boolean updateBonusCredits(String outTradeNo, long bonusCredits) {
            order = copy(order, order.status(), order.providerTradeNo(), order.ledgerId(), order.paidAt());
            return true;
        }
    }
}
