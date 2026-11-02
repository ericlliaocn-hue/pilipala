package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.ModelCatalogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：视频产品 / group 视图与产品上下架。
 *
 * <p>不暴露渠道 Key；写接口只改 {@code model_product.enabled}，不改价格，
 * 也不再复制一套 /video-products 目录。provider_task 原文仍走
 * {@code GET /api/v1/admin/jobs} 的任务详情。
 */
@RestController
@RequestMapping("/api/v1/admin/video")
public class AdminVideoController {

    private final AdminGuard guard;
    private final ModelCatalogRepository catalog;
    private final ManageAdminConsole console;

    public AdminVideoController(AdminGuard guard, ModelCatalogRepository catalog, ManageAdminConsole console) {
        this.guard = guard;
        this.catalog = catalog;
        this.console = console;
    }

    @GetMapping("/products")
    public ApiResponse<List<ModelProduct>> products() {
        guard.require("model.read");
        return ApiResponse.ok(catalog.findAllProducts());
    }

    @GetMapping("/groups")
    public ApiResponse<List<ModelProductGroup>> groups() {
        guard.require("model.read");
        return ApiResponse.ok(catalog.findAllGroups());
    }

    /** 只改 enabled：enabled 必填，改完回读产品行（价格原样）。 */
    @PutMapping("/products/{code}/enabled")
    public ApiResponse<ModelProduct> setProductEnabled(@PathVariable String code,
            @RequestBody EnabledRequest request) {
        guard.require("model.write");
        if (request.enabled() == null) {
            throw JobException.validation();
        }
        console.setVideoProductEnabled(guard.currentUserId(), code, request.enabled());
        return ApiResponse.ok(catalog.findProductByCode(code).orElseThrow(JobException::validation));
    }

    public record EnabledRequest(Boolean enabled) {
    }
}
