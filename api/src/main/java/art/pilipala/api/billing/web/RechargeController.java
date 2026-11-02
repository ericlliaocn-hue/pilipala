package art.pilipala.api.billing.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.billing.application.CreateRechargeOrder;
import art.pilipala.api.billing.application.GetRechargeOrder;
import art.pilipala.api.billing.application.ListRechargePackages;
import art.pilipala.api.billing.application.RechargeOrderView;
import art.pilipala.api.billing.application.RechargePackageView;
import art.pilipala.api.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/recharge")
public class RechargeController {

    private final AuthSessionService sessions;
    private final CurrentTenant currentTenant;
    private final ListRechargePackages packages;
    private final CreateRechargeOrder create;
    private final GetRechargeOrder get;

    public RechargeController(
            AuthSessionService sessions,
            CurrentTenant currentTenant,
            ListRechargePackages packages,
            CreateRechargeOrder create,
            GetRechargeOrder get) {
        this.sessions = sessions;
        this.currentTenant = currentTenant;
        this.packages = packages;
        this.create = create;
        this.get = get;
    }

    @GetMapping("/packages")
    public ApiResponse<List<RechargePackageView>> listPackages() {
        return ApiResponse.ok(packages.list());
    }

    @PostMapping("/orders")
    public ApiResponse<RechargeOrderView> create(@RequestBody CreateRequest body, HttpServletRequest request) {
        UUID userId = userId();
        return ApiResponse.ok(create.create(
                userId, currentTenant.require(userId),
                body.packageId(), body.productCode(), body.credits(), body.device(), request));
    }

    @GetMapping("/orders/{outTradeNo}")
    public ApiResponse<RechargeOrderView> get(@PathVariable String outTradeNo) {
        return ApiResponse.ok(get.get(currentTenant.require(userId()), outTradeNo));
    }

    private UUID userId() {
        return UUID.fromString(sessions.currentUserId());
    }

    public record CreateRequest(String packageId, String productCode, Long credits, String device) {
    }
}
