package art.pilipala.api.billing.application;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.billing.domain.CreditProduct;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderKind;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.infra.CreditProductRepository;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettleRechargePayment {

    private final RechargeOrderRepository orders;
    private final CreditProductRepository products;
    private final WalletService wallets;
    private final CreditGrantService grantService;
    private final FirstPurchaseBonusPolicy firstPurchase;
    private final MembershipActivationService membershipActivation;

    public SettleRechargePayment(
            RechargeOrderRepository orders,
            CreditProductRepository products,
            WalletService wallets,
            CreditGrantService grantService,
            FirstPurchaseBonusPolicy firstPurchase,
            MembershipActivationService membershipActivation) {
        this.orders = orders;
        this.products = products;
        this.wallets = wallets;
        this.grantService = grantService;
        this.firstPurchase = firstPurchase;
        this.membershipActivation = membershipActivation;
    }

    @Transactional
    public WalletLedgerEntry creditOrder(RechargeOrder order) {
        if (order.orderKind() == RechargeOrderKind.MEMBERSHIP) {
            return membershipActivation.activateFromPaidOrder(order);
        }
        Wallet wallet = wallets.ensureWallet(order.tenantId());
        Optional<CreditProduct> product = order.productId() != null
                ? products.findById(order.productId())
                : Optional.empty();
        long baseCredits = order.credits();
        long fixedBonus = order.bonusCredits();
        long firstBonus = 0;
        CreditProduct resolved = product.orElseGet(() -> order.productCode() == null
                ? null
                : products.findByCode(order.productCode()).orElse(null));
        if (resolved != null) {
            firstBonus = firstPurchase.compute(order.userId(), resolved, order.id(), baseCredits);
            long totalBonus = fixedBonus + firstBonus;
            if (totalBonus != order.bonusCredits()) {
                orders.updateBonusCredits(order.outTradeNo(), totalBonus);
            }
        }
        long topUpAmount;
        if (resolved != null || order.productCode() != null) {
            topUpAmount = grantService.grantForRecharge(
                    order.tenantId(), order.userId(), wallet, order.id(), resolved, baseCredits, fixedBonus, firstBonus);
        } else {
            topUpAmount = grantService.grantLegacyRecharge(
                    order.tenantId(), order.userId(), wallet, order.id(), baseCredits);
        }
        return wallets.credit(order.tenantId(), order.userId(), topUpAmount, order.id());
    }
}
