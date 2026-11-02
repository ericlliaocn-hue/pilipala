package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.job.infra.ModelCatalogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：视频产品 / group 只读视图。
 *
 * <p>不暴露渠道 Key、不做上下架（本期只读）；provider_task 原文仍走
 * {@code GET /api/v1/admin/jobs} 的任务详情。
 */
@RestController
@RequestMapping("/api/v1/admin/video")
public class AdminVideoController {

    private final AdminGuard guard;
    private final ModelCatalogRepository catalog;

    public AdminVideoController(AdminGuard guard, ModelCatalogRepository catalog) {
        this.guard = guard;
        this.catalog = catalog;
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
}
