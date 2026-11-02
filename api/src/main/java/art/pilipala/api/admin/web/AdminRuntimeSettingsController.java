package art.pilipala.api.admin.web;

import java.util.Map;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.infra.AuditLogMapper;
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
    private final AuditLogMapper audit;

    public AdminRuntimeSettingsController(AdminGuard guard, RuntimeSettingService settings, AuditLogMapper audit) {
        this.guard = guard;
        this.settings = settings;
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> list() {
        guard.require("runtime.read");
        return ApiResponse.ok(settings.listAll());
    }

    /**
     * 沿用这一条 PATCH 作为唯一写入口：只允许 auth.email_login_enabled /
     * auth.email_register_enabled，值只能是 true / false（校验在
     * {@link RuntimeSettingService#patch} 里收窄），写完追加审计。
     */
    @PatchMapping
    public ApiResponse<Map<String, String>> patch(@RequestBody Map<String, String> body) {
        guard.require("runtime.write");
        Map<String, String> changed = settings.patch(body);
        audit.insert(guard.currentUserId(), "runtime.write", null, "runtime_setting",
                String.join(",", changed.keySet()), null, "patch runtime settings " + changed);
        return ApiResponse.ok(changed);
    }
}
