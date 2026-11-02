package art.pilipala.api.billing.web;

import java.util.UUID;

import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.billing.application.CreateMembershipCheckout;
import art.pilipala.api.billing.application.GetMembershipMe;
import art.pilipala.api.billing.application.ListMembershipPlans;
import art.pilipala.api.billing.application.RechargeOrderView;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class MembershipController {

    private final CurrentTenant currentTenant;
    private final ListMembershipPlans listPlans;
    private final GetMembershipMe membershipMe;
    private final CreateMembershipCheckout checkout;

    public MembershipController(
            CurrentTenant currentTenant,
            ListMembershipPlans listPlans, GetMembershipMe membershipMe, CreateMembershipCheckout checkout) {
        this.currentTenant = currentTenant;
        this.listPlans = listPlans;
        this.membershipMe = membershipMe;
        this.checkout = checkout;
    }

    @GetMapping({"/membership-plans", "/membership/membership-plans"})
    public ApiResponse<ListMembershipPlans.MembershipPlansView> plans(
            @RequestParam(value = "audience", required = false) String audience) {
        return ApiResponse.ok(listPlans.listPublic(audience));
    }

    @GetMapping("/me")
    public ApiResponse<GetMembershipMe.MembershipMeView> me() {
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return ApiResponse.ok(membershipMe.get(currentTenant.require(userId)).orElse(null));
    }

    @PostMapping("/checkout")
    public ApiResponse<RechargeOrderView> checkout(@RequestBody CheckoutRequest body, HttpServletRequest request) {
        if (body == null || body.priceId() == null || body.priceId() <= 0) {
            throw JobException.validation();
        }
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return ApiResponse.ok(checkout.checkout(
                userId, currentTenant.require(userId), body.priceId(), body.device(), request));
    }

    public record CheckoutRequest(Long price_id, String device) {
        public Long priceId() {
            return price_id;
        }
    }
}
