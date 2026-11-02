package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageCreditProducts;
import art.pilipala.api.admin.application.ManageCreditProducts.AdminCreditGrantView;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/credit-grants")
public class AdminCreditGrantController {

    private final AdminGuard guard;
    private final ManageCreditProducts products;

    public AdminCreditGrantController(AdminGuard guard, ManageCreditProducts products) {
        this.guard = guard;
        this.products = products;
    }

    @GetMapping
    public ApiResponse<List<AdminCreditGrantView>> list(
            @RequestParam("user_id") String userId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.require("ledger.read");
        return ApiResponse.ok(products.listGrants(parseUserId(userId), limit));
    }

    private static UUID parseUserId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }
}
