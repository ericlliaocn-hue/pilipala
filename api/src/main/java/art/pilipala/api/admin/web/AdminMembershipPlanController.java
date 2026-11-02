package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageMembershipPlans;
import art.pilipala.api.admin.application.ManageMembershipPlans.AdminMembershipPlanPriceView;
import art.pilipala.api.admin.application.ManageMembershipPlans.AdminMembershipPlanView;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/membership-plans")
public class AdminMembershipPlanController {

    private final AdminGuard guard;
    private final ManageMembershipPlans plans;

    public AdminMembershipPlanController(AdminGuard guard, ManageMembershipPlans plans) {
        this.guard = guard;
        this.plans = plans;
    }

    @GetMapping
    public ApiResponse<List<AdminMembershipPlanView>> list() {
        guard.require("membership.read");
        return ApiResponse.ok(plans.listPlans());
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<AdminMembershipPlanView> enable(@PathVariable long id) {
        guard.require("membership.write");
        return ApiResponse.ok(plans.setPlanEnabled(id, true));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<AdminMembershipPlanView> disable(@PathVariable long id) {
        guard.require("membership.write");
        return ApiResponse.ok(plans.setPlanEnabled(id, false));
    }

    @GetMapping("/prices")
    public ApiResponse<List<AdminMembershipPlanPriceView>> listPrices(
            @RequestParam(value = "plan_id", required = false) Long planId) {
        guard.require("membership.read");
        return ApiResponse.ok(plans.listPrices(planId));
    }

    @PostMapping("/prices/{id}/enable")
    public ApiResponse<AdminMembershipPlanPriceView> enablePrice(@PathVariable long id) {
        guard.require("membership.write");
        return ApiResponse.ok(plans.setPriceEnabled(id, true));
    }

    @PostMapping("/prices/{id}/disable")
    public ApiResponse<AdminMembershipPlanPriceView> disablePrice(@PathVariable long id) {
        guard.require("membership.write");
        return ApiResponse.ok(plans.setPriceEnabled(id, false));
    }
}
