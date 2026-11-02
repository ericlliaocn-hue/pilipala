package art.pilipala.api.admin.web;

import art.pilipala.api.admin.application.AdminConsoleViews.BannerView;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 Banner：本步唯一的非后台路由，免登录（已登记进 AuthPathPolicy）。
 *
 * <p>返回该广告位当前有效的一条：enabled 且当前时间落在 starts_at / ends_at 内，
 * 按 sort_order、created_at 取第一条；没有则 data 为空。
 */
@RestController
@RequestMapping("/api/v1/banners")
public class PublicBannerController {

    private final ManageAdminConsole console;

    public PublicBannerController(ManageAdminConsole console) {
        this.console = console;
    }

    @GetMapping("/current")
    public ApiResponse<BannerView> current(@RequestParam("placement") String placement) {
        return ApiResponse.ok(console.currentBanner(placement));
    }
}
