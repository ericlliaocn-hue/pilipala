package art.pilipala.api.project.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.project.application.AssetService;
import art.pilipala.api.project.application.DeleteProjectView;
import art.pilipala.api.project.application.UserAssetView;
import art.pilipala.api.project.infra.ProjectException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端云端素材接口。
 *
 * <p>素材表只存 TOS 对象键；列表里的读地址每次现签（ADR 0003）。
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AuthSessionService sessions;
    private final CurrentTenant currentTenant;
    private final AssetService assets;

    public AssetController(AuthSessionService sessions, CurrentTenant currentTenant, AssetService assets) {
        this.sessions = sessions;
        this.currentTenant = currentTenant;
        this.assets = assets;
    }

    @GetMapping
    public ApiResponse<List<UserAssetView>> list(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        UUID userId = currentUserId();
        return ApiResponse.ok(assets.listUserAssets(currentTenant.require(userId), limit));
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<DeleteProjectView> delete(@PathVariable String assetId) {
        UUID userId = currentUserId();
        return ApiResponse.ok(assets.deleteAsset(currentTenant.require(userId), assetIdOf(assetId)));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }

    /** 路径上的 id 非法时按「不存在」处理，不泄露格式细节。 */
    private UUID assetIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ProjectException.assetNotFound();
        }
    }
}
