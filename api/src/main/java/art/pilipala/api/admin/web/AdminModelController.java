package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ListModelDisplayConfig;
import art.pilipala.api.admin.application.ModelDisplayConfigView;
import art.pilipala.api.admin.application.UpdateModelDisplayConfig;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：模型展示配置的查看与上下架。
 *
 * <p>这张表只控制前端展示，不存任何供应商密钥（ADR 0001）。
 */
@RestController
@RequestMapping("/api/v1/admin/models")
public class AdminModelController {

    private final AdminGuard guard;
    private final ListModelDisplayConfig models;
    private final UpdateModelDisplayConfig update;

    public AdminModelController(AdminGuard guard, ListModelDisplayConfig models, UpdateModelDisplayConfig update) {
        this.guard = guard;
        this.models = models;
        this.update = update;
    }

    /** 全部展示配置（含已下架的）。 */
    @GetMapping
    public ApiResponse<List<ModelDisplayConfigView>> list() {
        guard.require("model.read");
        return ApiResponse.ok(models.list());
    }

    /**
     * 新增或更新一行展示配置。
     */
    @PutMapping("/{model}")
    public ApiResponse<ModelDisplayConfigView> update(@PathVariable String model, @RequestBody UpdateRequest request) {
        guard.require("model.write");
        return ApiResponse.ok(update.update(model, request.displayName(), request.capability(), request.enabled(),
                request.sortOrder()));
    }

    /**
     * 展示配置请求体。
     *
     * @param displayName 展示名，必填
     * @param capability 能力；为空时沿用已有值
     * @param enabled 是否上架；为空时沿用已有值
     * @param sortOrder 排序位；为空时沿用已有值
     */
    public record UpdateRequest(String displayName, String capability, Boolean enabled, Integer sortOrder) {
    }
}
