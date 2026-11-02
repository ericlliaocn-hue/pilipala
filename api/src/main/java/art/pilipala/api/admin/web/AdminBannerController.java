package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminConsoleViews.BannerView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.admin.application.ManageAdminConsole.BannerInput;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：Banner 列表 / 创建 / 更新 / 启用。
 *
 * <p>image_object_key 由请求体传入原样入库：API 现有能力只有 TosUrlSigner 的
 * 预签名读地址，没有管理端上传，本步不新写 TOS PUT、不改 worker。
 */
@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminBannerController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping
    public ApiResponse<List<BannerView>> list() {
        guard.require("banner.read");
        return ApiResponse.ok(console.listBanners());
    }

    @PostMapping
    public ApiResponse<BannerView> create(@RequestBody BannerInput request) {
        guard.require("banner.write");
        return ApiResponse.ok(console.createBanner(guard.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<BannerView> update(@PathVariable String id, @RequestBody BannerInput request) {
        guard.require("banner.write");
        return ApiResponse.ok(console.updateBanner(guard.currentUserId(), idOf(id), request));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<BannerView> enable(@PathVariable String id) {
        guard.require("banner.write");
        return ApiResponse.ok(console.enableBanner(guard.currentUserId(), idOf(id)));
    }

    private UUID idOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }
}
