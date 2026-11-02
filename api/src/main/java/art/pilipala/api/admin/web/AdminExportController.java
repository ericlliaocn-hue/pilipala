package art.pilipala.api.admin.web;

import java.nio.charset.StandardCharsets;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.ManageAdminConsole;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：流水与充值订单 CSV 导出。from / to 必填，跨度最长 31 天。
 * 响应直接是 text/csv，不套统一信封；权限失败仍走信封（异常先于返回值产生）。
 */
@RestController
@RequestMapping("/api/v1/admin/exports")
public class AdminExportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminGuard guard;
    private final ManageAdminConsole console;

    public AdminExportController(AdminGuard guard, ManageAdminConsole console) {
        this.guard = guard;
        this.console = console;
    }

    @GetMapping("/ledger")
    public ResponseEntity<String> ledger(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        guard.require("ledger.read");
        return ResponseEntity.ok().contentType(TEXT_CSV).body(console.exportLedgerCsv(from, to));
    }

    @GetMapping("/recharge-orders")
    public ResponseEntity<String> rechargeOrders(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to) {
        guard.require("recharge.read");
        return ResponseEntity.ok().contentType(TEXT_CSV).body(console.exportRechargeCsv(from, to));
    }
}
