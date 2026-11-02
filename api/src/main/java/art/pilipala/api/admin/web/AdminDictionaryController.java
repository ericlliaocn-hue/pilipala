package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminConsoleViews.DictionaryView;
import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 运维后台：字典列表与改 label / sort_order。不能新增条目，不能新增状态。 */
@RestController
@RequestMapping("/api/v1/admin/dictionaries")
public class AdminDictionaryController {

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminDictionaryController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping
    public ApiResponse<List<DictionaryView>> list() {
        guard.require("dictionary.read");
        return ApiResponse.ok(console.listDictionary());
    }

    @PutMapping
    public ApiResponse<DictionaryView> update(@RequestBody UpdateRequest request) {
        guard.require("dictionary.write");
        return ApiResponse.ok(console.updateDictionary(guard.currentUserId(), request.dictCode(),
                request.itemCode(), request.locale(), request.label(), request.sortOrder()));
    }

    public record UpdateRequest(String dictCode, String itemCode, String locale, String label, Integer sortOrder) {
    }
}
