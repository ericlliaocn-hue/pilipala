package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminCreditProductView;
import art.pilipala.api.admin.application.ManageCreditProducts;
import art.pilipala.api.admin.application.ManageCreditProducts.AdminCreditGrantView;
import art.pilipala.api.admin.application.ManageCreditProducts.CreateCreditProductRequest;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/credit-products")
public class AdminCreditProductController {

    private final art.pilipala.api.admin.application.AdminGuard guard;
    private final ManageCreditProducts products;

    public AdminCreditProductController(art.pilipala.api.admin.application.AdminGuard guard, ManageCreditProducts products) {
        this.guard = guard;
        this.products = products;
    }

    @GetMapping
    public ApiResponse<List<AdminCreditProductView>> list(
            @RequestParam(value = "tab", required = false) String tab,
            @RequestParam(value = "enabled", required = false) Boolean enabled) {
        guard.require("credit_product.read");
        return ApiResponse.ok(products.list(tab, enabled));
    }

    @PostMapping
    public ApiResponse<AdminCreditProductView> create(@RequestBody CreateCreditProductRequest body) {
        guard.require("credit_product.write");
        return ApiResponse.ok(products.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCreditProductView> update(@PathVariable long id, @RequestBody CreateCreditProductRequest body) {
        guard.require("credit_product.write");
        return ApiResponse.ok(products.update(id, body));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<AdminCreditProductView> enable(@PathVariable long id) {
        guard.require("credit_product.write");
        return ApiResponse.ok(products.setEnabled(id, true));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<AdminCreditProductView> disable(@PathVariable long id) {
        guard.require("credit_product.write");
        return ApiResponse.ok(products.setEnabled(id, false));
    }

}
