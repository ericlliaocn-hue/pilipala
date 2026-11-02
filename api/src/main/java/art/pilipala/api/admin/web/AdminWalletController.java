package art.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.admin.application.AdminGuard;
import art.pilipala.api.admin.application.AdminLedgerEntryView;
import art.pilipala.api.admin.application.ListWalletLedger;
import art.pilipala.api.admin.application.TopUpUserWallet;
import art.pilipala.api.billing.application.WalletView;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维后台：手工充值与钱包流水排查。
 *
 * <p>充值走既有 {@code TopUpWallet}，余额与流水同时落库（ADR 0001 单账本）。
 */
@RestController
@RequestMapping("/api/v1/admin/wallets")
public class AdminWalletController {

    private final AdminGuard guard;
    private final TopUpUserWallet topUp;
    private final ListWalletLedger ledger;

    public AdminWalletController(AdminGuard guard, TopUpUserWallet topUp, ListWalletLedger ledger) {
        this.guard = guard;
        this.topUp = topUp;
        this.ledger = ledger;
    }

    /**
     * 手工充值。
     *
     * @param amount 必须为正整数
     */
    @PostMapping("/{userId}/topup")
    public ApiResponse<WalletView> topUp(@PathVariable String userId, @RequestBody TopUpRequest request) {
        guard.require("wallet.topup");
        return ApiResponse.ok(topUp.topUp(guard.currentUserId(), userIdOf(userId), request.amount(),
                request.idempotencyKey(), request.reason()));
    }

    /**
     * 查某个用户最近的账本流水（时间倒序）。
     */
    @GetMapping("/{userId}/ledger")
    public ApiResponse<List<AdminLedgerEntryView>> ledger(@PathVariable String userId,
                                                           @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.require("ledger.read");
        return ApiResponse.ok(ledger.list(userIdOf(userId), limit));
    }

    /** 路径上的 user id 非法时按参数不合法处理。 */
    private UUID userIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }

    /**
     * 充值请求体。
     *
     * @param amount 充值的 credit 数，必须大于 0
     * @param idempotencyKey 必填，1 到 64 字符；同一运维同一个键只入账一次
     * @param reason 必填的操作原因，1 到 200 字符，写进审计流水
     */
    public record TopUpRequest(long amount, String idempotencyKey, String reason) {
    }
}
