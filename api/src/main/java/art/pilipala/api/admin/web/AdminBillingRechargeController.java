package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminRechargeOrderView;
import art.pilipala.api.admin.application.ListAdminRechargeOrders;
import art.pilipala.api.admin.application.RefundAdminRechargeOrder;
import art.pilipala.api.billing.application.RechargeOrderView;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing/recharge-orders")
public class AdminBillingRechargeController {

    private final AdminGuard guard;
    private final ListAdminRechargeOrders list;
    private final RefundAdminRechargeOrder refund;

    public AdminBillingRechargeController(AdminGuard guard, ListAdminRechargeOrders list,
            RefundAdminRechargeOrder refund) {
        this.guard = guard;
        this.list = list;
        this.refund = refund;
    }

    @GetMapping
    public ApiResponse<List<AdminRechargeOrderView>> list(
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "out_trade_no", required = false) String outTradeNo,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.require("recharge.read");
        return ApiResponse.ok(list.list(userId, status, outTradeNo, limit));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<RechargeOrderView> refund(@PathVariable String id, @RequestBody RefundRequest request) {
        guard.require("recharge.refund");
        return ApiResponse.ok(refund.refund(guard.currentUserId(), orderId(id),
                request.idempotencyKey(), request.reason()));
    }

    private UUID orderId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }

    /**
     * 退款请求体。
     *
     * @param idempotencyKey 必填，1 到 64 字符；同一运维同一个键只退款一次
     * @param reason 必填的操作原因，1 到 200 字符，写进审计流水
     */
    public record RefundRequest(String idempotencyKey, String reason) {
    }
}
