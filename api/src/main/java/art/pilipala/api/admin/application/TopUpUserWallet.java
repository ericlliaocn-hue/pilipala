package art.pilipala.api.admin.application;

import java.util.UUID;

import art.pilipala.api.admin.infra.AuditLogMapper;
import art.pilipala.api.auth.application.PersonalTenants;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.billing.application.TopUpWallet;
import art.pilipala.api.billing.application.WalletView;
import art.pilipala.api.billing.domain.Wallet;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：运维手工给某个用户的个人租户加额。
 *
 * <p>单账本红线（ADR 0001）：<b>只</b>走 {@link TopUpWallet#topUp}，
 * 由它在同一事务里加余额并写一条 {@code type='topup'} 的流水。
 * 严禁绕过它直接 {@code UPDATE wallet} —— 只改余额不写流水会让账本对不上。
 *
 * <p>钱进的是被加额用户那个<b>已存在的</b>个人租户（{@link PersonalTenants#require}），
 * 找不到租户就直接失败，不在这里替用户建租户。
 *
 * <p>{@code (操作者, wallet.topup, 幂等键)} 只执行一次：重放时不第二次入账也不写第二条审计，
 * 只返回第一次的钱包快照。审计里的 {@code actor_user_id} 是点按钮的运维，不是收款用户。
 */
@Service
public class TopUpUserWallet {

    /** 幂等记录与审计共用的动作名。 */
    public static final String ACTION = "wallet.topup";

    private final UserRepository users;
    private final PersonalTenants tenants;
    private final TopUpWallet topUpWallet;
    private final AdminIdempotency idempotency;
    private final AuditLogMapper audit;

    public TopUpUserWallet(
            UserRepository users,
            PersonalTenants tenants,
            TopUpWallet topUpWallet,
            AdminIdempotency idempotency,
            AuditLogMapper audit) {
        this.users = users;
        this.tenants = tenants;
        this.topUpWallet = topUpWallet;
        this.idempotency = idempotency;
        this.audit = audit;
    }

    /**
     * 加额。金额必须为正；目标用户不存在按参数不合法处理。
     *
     * @param actorUserId 操作者（登录的运维）
     * @param targetUserId 收款用户
     * @param amount 充值的 credit 数，必须大于 0
     * @param idempotencyKey 必填，1..64
     * @param reason 必填的操作原因，1..200，写进审计摘要
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WalletView topUp(UUID actorUserId, UUID targetUserId, long amount, String idempotencyKey, String reason) {
        String key = AdminIdempotency.requireKey(idempotencyKey);
        String note = AdminIdempotency.requireReason(reason);
        // 键校验在目标校验之前：同一个键换个目标也返回第一次的结果，不会二次入账。
        Wallet[] credited = new Wallet[1];
        return idempotency.execute(actorUserId, ACTION, key, WalletView.class, () -> {
            if (amount <= 0 || users.findById(targetUserId).isEmpty()) {
                throw JobException.validation();
            }
            Wallet wallet = topUpWallet.topUp(tenants.require(targetUserId), targetUserId, amount);
            credited[0] = wallet;
            return new WalletView(targetUserId.toString(), wallet.available(), wallet.frozen(), wallet.updatedAt());
        }, ignored -> audit.insert(actorUserId, ACTION, credited[0].tenantId(), "wallet",
                credited[0].id().toString(), key, note));
    }
}
