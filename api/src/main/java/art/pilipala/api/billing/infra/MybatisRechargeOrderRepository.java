package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderStatus;

@Repository
public class MybatisRechargeOrderRepository implements RechargeOrderRepository {

    private final RechargeOrderMapper mapper;

    public MybatisRechargeOrderRepository(RechargeOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RechargeOrder insertCreated(RechargeOrder draft) {
        return mapper.insertCreated(
                draft.tenantId(),
                draft.userId(),
                draft.outTradeNo(),
                draft.packageId(),
                draft.moneyFen(),
                draft.credits(),
                draft.status().value(),
                draft.clientIp(),
                draft.device(),
                draft.productName(),
                draft.productId(),
                draft.productCode(),
                draft.bonusCredits(),
                draft.orderKind().value(),
                draft.planPriceId());
    }

    @Override
    public Optional<RechargeOrder> findByOutTradeNo(String outTradeNo) {
        return Optional.ofNullable(mapper.findByOutTradeNo(outTradeNo));
    }

    @Override
    public Optional<RechargeOrder> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public boolean markPendingPay(UUID id, String providerTradeNo, String payUrl, String qrcodeUrl) {
        return mapper.markPendingPay(id, providerTradeNo, payUrl, qrcodeUrl) > 0;
    }

    @Override
    public boolean markFailed(UUID id, String reason) {
        return mapper.markFailed(id, reason) > 0;
    }

    @Override
    public boolean claimPaidIfPending(String outTradeNo, String providerTradeNo) {
        return mapper.claimPaidIfPending(outTradeNo, providerTradeNo) > 0;
    }

    @Override
    public boolean attachLedger(String outTradeNo, UUID ledgerId) {
        return mapper.attachLedger(outTradeNo, ledgerId) > 0;
    }

    @Override
    public boolean markClosed(UUID id) {
        return mapper.markClosed(id) > 0;
    }

    @Override
    public boolean markRefunded(UUID id) {
        return mapper.markRefunded(id) > 0;
    }

    @Override
    public List<RechargeOrder> findPendingPayBefore(Instant before, int limit) {
        return mapper.findPendingPayBefore(before, limit);
    }

    @Override
    public List<RechargeOrder> findAdmin(UUID userId, String status, String outTradeNo, int limit) {
        return mapper.findAdmin(userId, status, outTradeNo, limit);
    }

    @Override
    public int countPaidByUserAndProductCodeExcluding(UUID userId, String productCode, UUID excludeOrderId) {
        return mapper.countPaidByUserAndProductCodeExcluding(userId, productCode, excludeOrderId);
    }

    @Override
    public boolean updateBonusCredits(String outTradeNo, long bonusCredits) {
        return mapper.updateBonusCredits(outTradeNo, bonusCredits) > 0;
    }
}
