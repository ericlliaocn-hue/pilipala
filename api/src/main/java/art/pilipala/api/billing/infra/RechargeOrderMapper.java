package art.pilipala.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import art.pilipala.api.billing.domain.RechargeOrder;

@Mapper
public interface RechargeOrderMapper {

    String COLS = "id, tenant_id, user_id, out_trade_no, provider, provider_trade_no, package_id, money_fen, credits, "
            + "status, client_ip, device, pay_url, qrcode_url, product_name, product_id, product_code, bonus_credits, "
            + "ledger_id, paid_at, refunded_at, fail_reason, order_kind, plan_price_id, created_at, updated_at";

    @Select("INSERT INTO recharge_order (tenant_id, user_id, out_trade_no, provider, package_id, money_fen, credits, "
            + "status, client_ip, device, product_name, product_id, product_code, bonus_credits, order_kind, "
            + "plan_price_id) "
            + "VALUES (#{tenantId}, #{userId}, #{outTradeNo}, 'zpay', #{packageId}, #{moneyFen}, #{credits}, #{status}, "
            + "#{clientIp}, #{device}, #{productName}, #{productId}, #{productCode}, #{bonusCredits}, "
            + "#{orderKind}, #{planPriceId}) RETURNING " + COLS)
    RechargeOrder insertCreated(@Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("outTradeNo") String outTradeNo,
            @Param("packageId") String packageId,
            @Param("moneyFen") long moneyFen,
            @Param("credits") long credits,
            @Param("status") String status,
            @Param("clientIp") String clientIp,
            @Param("device") String device,
            @Param("productName") String productName,
            @Param("productId") Long productId,
            @Param("productCode") String productCode,
            @Param("bonusCredits") long bonusCredits,
            @Param("orderKind") String orderKind,
            @Param("planPriceId") Long planPriceId);

    @Select("SELECT COUNT(*) FROM recharge_order WHERE user_id = #{userId} AND status = 'paid' "
            + "AND product_code = #{productCode} AND id <> #{excludeOrderId}")
    int countPaidByUserAndProductCodeExcluding(@Param("userId") UUID userId,
            @Param("productCode") String productCode,
            @Param("excludeOrderId") UUID excludeOrderId);

    @Update("UPDATE recharge_order SET bonus_credits = #{bonusCredits}, updated_at = now() "
            + "WHERE out_trade_no = #{outTradeNo}")
    int updateBonusCredits(@Param("outTradeNo") String outTradeNo, @Param("bonusCredits") long bonusCredits);

    @Select("SELECT " + COLS + " FROM recharge_order WHERE out_trade_no = #{outTradeNo}")
    RechargeOrder findByOutTradeNo(@Param("outTradeNo") String outTradeNo);

    @Select("SELECT " + COLS + " FROM recharge_order WHERE id = #{id}")
    RechargeOrder findById(@Param("id") UUID id);

    @Update("UPDATE recharge_order SET status = 'pending_pay', provider_trade_no = #{providerTradeNo}, "
            + "pay_url = #{payUrl}, qrcode_url = #{qrcodeUrl}, updated_at = now() "
            + "WHERE id = #{id} AND status = 'created'")
    int markPendingPay(@Param("id") UUID id,
            @Param("providerTradeNo") String providerTradeNo,
            @Param("payUrl") String payUrl,
            @Param("qrcodeUrl") String qrcodeUrl);

    @Update("UPDATE recharge_order SET status = 'failed', fail_reason = #{reason}, updated_at = now() "
            + "WHERE id = #{id} AND status IN ('created', 'pending_pay')")
    int markFailed(@Param("id") UUID id, @Param("reason") String reason);

    @Update("UPDATE recharge_order SET status = 'paid', provider_trade_no = COALESCE(#{providerTradeNo}, provider_trade_no), "
            + "paid_at = now(), updated_at = now() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'pending_pay'")
    int claimPaidIfPending(@Param("outTradeNo") String outTradeNo, @Param("providerTradeNo") String providerTradeNo);

    @Update("UPDATE recharge_order SET ledger_id = #{ledgerId}, updated_at = now() "
            + "WHERE out_trade_no = #{outTradeNo} AND status = 'paid' AND ledger_id IS NULL")
    int attachLedger(@Param("outTradeNo") String outTradeNo, @Param("ledgerId") UUID ledgerId);

    @Update("UPDATE recharge_order SET status = 'closed', updated_at = now() "
            + "WHERE id = #{id} AND status = 'pending_pay'")
    int markClosed(@Param("id") UUID id);

    @Update("UPDATE recharge_order SET status = 'refunded', refunded_at = now(), updated_at = now() "
            + "WHERE id = #{id} AND status = 'paid'")
    int markRefunded(@Param("id") UUID id);

    @Select("SELECT " + COLS + " FROM recharge_order WHERE status = 'pending_pay' "
            + "AND created_at < #{before} ORDER BY created_at LIMIT #{limit}")
    List<RechargeOrder> findPendingPayBefore(@Param("before") Instant before, @Param("limit") int limit);

    @Select("<script>SELECT " + COLS + " FROM recharge_order WHERE 1 = 1"
            + "<if test='userId != null'> AND user_id = #{userId}</if>"
            + "<if test='status != null and status != \"\"'> AND status = #{status}</if>"
            + "<if test='outTradeNo != null and outTradeNo != \"\"'> AND out_trade_no = #{outTradeNo}</if>"
            + " ORDER BY created_at DESC LIMIT #{limit}</script>")
    List<RechargeOrder> findAdmin(@Param("userId") UUID userId,
            @Param("status") String status,
            @Param("outTradeNo") String outTradeNo,
            @Param("limit") int limit);
}
