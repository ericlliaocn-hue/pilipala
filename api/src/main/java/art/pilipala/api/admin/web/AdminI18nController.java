package art.pilipala.api.admin.web;

import java.util.List;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.I18nMessageView;
import art.pilipala.api.admin.application.ListI18nMessages;
import art.pilipala.api.admin.application.UpsertI18nMessage;
import art.pilipala.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：动态文案（i18n_message）的查询与热更新。
 *
 * <p>写入即生效：{@code I18nMessageService} 每次解析都查表，不需要重启 api。
 */
@RestController
@RequestMapping("/api/v1/admin/i18n-messages")
public class AdminI18nController {

    private final AdminGuard guard;
    private final ListI18nMessages messages;
    private final UpsertI18nMessage upsert;

    public AdminI18nController(AdminGuard guard, ListI18nMessages messages, UpsertI18nMessage upsert) {
        this.guard = guard;
        this.messages = messages;
        this.upsert = upsert;
    }

    /**
     * @param query 关键字；为空时列出全部
     */
    @GetMapping
    public ApiResponse<List<I18nMessageView>> list(@RequestParam(value = "q", required = false) String query) {
        guard.require("i18n.read");
        return ApiResponse.ok(messages.list(query));
    }

    /**
     * 新增或覆盖一条词条。
     */
    @PutMapping
    public ApiResponse<I18nMessageView> upsert(@RequestBody UpsertRequest request) {
        guard.require("i18n.write");
        return ApiResponse.ok(upsert.upsert(request.key(), request.locale(), request.template()));
    }

    /**
     * 词条请求体。
     *
     * @param key 稳定键，1..128
     * @param locale zh 或 en
     * @param template 短句模板，1..1000
     */
    public record UpsertRequest(String key, String locale, String template) {
    }
}
