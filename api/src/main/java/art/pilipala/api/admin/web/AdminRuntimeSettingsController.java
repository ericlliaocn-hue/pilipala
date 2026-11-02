package art.pilipala.api.admin.web;

import java.util.Map;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.settings.application.RuntimeSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/runtime-settings")
public class AdminRuntimeSettingsController {

    private final AdminGuard guard;
    private final RuntimeSettingService settings;

    public AdminRuntimeSettingsController(AdminGuard guard, RuntimeSettingService settings) {
        this.guard = guard;
        this.settings = settings;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> list() {
        guard.require("runtime.read");
        return ApiResponse.ok(settings.listAll());
    }

    @PatchMapping
    public ApiResponse<Map<String, String>> patch(@RequestBody Map<String, String> body) {
        guard.require("runtime.write");
        return ApiResponse.ok(settings.patch(body));
    }
}
