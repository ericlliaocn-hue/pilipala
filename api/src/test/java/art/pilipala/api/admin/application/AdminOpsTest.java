package art.pilipala.api.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import art.pilipala.api.auth.application.PersonalTenants;
import art.pilipala.api.auth.support.TestPersonalTenants;
import art.pilipala.api.admin.domain.AdminJob;
import art.pilipala.api.admin.support.InMemoryAdminIdempotencyStore;
import art.pilipala.api.admin.support.InMemoryAdminStore;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.billing.application.CreditGrantService;
import art.pilipala.api.billing.application.RechargeOrderView;
import art.pilipala.api.billing.application.RefundRechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrder;
import art.pilipala.api.billing.domain.RechargeOrderKind;
import art.pilipala.api.billing.domain.RechargeOrderStatus;
import art.pilipala.api.billing.infra.RechargeOrderRepository;
import art.pilipala.api.billing.application.TopUpWallet;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.i18n.InMemoryI18nMessageRepository;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.support.InMemoryJobStore;
import org.junit.jupiter.api.Test;

/**
 * 运维后台用例测试：加额走账本并按幂等键只入账一次、退款同样只退一次并留审计、
 * 上游原文按权限码裁剪、重投守状态与冻结单、文案热更新、模型上下架。
 */
class AdminOpsTest {

    private static final UUID USER = UUID.randomUUID();
    /** 点按钮的运维（审计里的 actor）。 */
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TENANT = TestPersonalTenants.TENANT;

    // ----- 加额（单账本红线 + 幂等 + 审计）-----

    @Test
    void topUpCreditsTheTenantsWalletAndWritesALedgerEntry() {
        Fixtures fixtures = fixtures();

        var view = fixtures.topUp().topUp(ACTOR, USER, 100, "k1", "客服补偿");

        assertThat(view.available()).isEqualTo(100L);
        assertThat(view.frozen()).isZero();
        assertThat(view.userId()).isEqualTo(USER.toString());
        assertThat(fixtures.wallets().walletOf(TENANT).available()).isEqualTo(100L);
        List<WalletLedgerType> types = fixtures.wallets().findLedgerByUserId(USER).stream()
                .map(WalletLedgerEntry::type)
                .toList();
        assertThat(types).containsExactly(WalletLedgerType.TOPUP);
        assertThat(fixtures.wallets().findLedgerByUserId(USER).get(0).amount()).isEqualTo(100L);

        var audit = fixtures.idempotency().audits().get(0);
        assertThat(fixtures.idempotency().audits()).hasSize(1);
        assertThat(audit.actorUserId()).isEqualTo(ACTOR);
        assertThat(audit.permissionCode()).isEqualTo("wallet.topup");
        assertThat(audit.tenantId()).isEqualTo(TENANT);
        assertThat(audit.targetType()).isEqualTo("wallet");
        assertThat(audit.targetId()).isEqualTo(fixtures.wallets().walletOf(TENANT).id().toString());
        assertThat(audit.idempotencyKey()).isEqualTo("k1");
        assertThat(audit.summary()).isEqualTo("客服补偿");
    }

    /** 同一运维、同一键：只入账一次，重放返回第一次的响应，也不写第二条审计。 */
    @Test
    void theSameTopUpKeyCredtisOnlyOnce() {
        Fixtures fixtures = fixtures();
        var first = fixtures.topUp().topUp(ACTOR, USER, 100, "k1", "客服补偿");

        var again = fixtures.topUp().topUp(ACTOR, USER, 100, "k1", "换个原因再来一次");

        assertThat(again).isEqualTo(first);
        assertThat(fixtures.wallets().findLedgerByUserId(USER)).hasSize(1);
        assertThat(fixtures.wallets().walletOf(TENANT).available()).isEqualTo(100L);
        assertThat(fixtures.idempotency().audits()).hasSize(1);
        assertThat(fixtures.idempotency().recordCount()).isEqualTo(1);
    }

    /** 同一个键换个目标用户也不算第二次入账：锁键是（运维, 动作, 键）。 */
    @Test
    void theSameKeyOnAnotherTargetStillReturnsTheFirstResult() {
        Fixtures fixtures = fixtures();
        UUID otherUser = UUID.randomUUID();
        when(fixtures.users().findById(otherUser)).thenReturn(Optional.of(user(otherUser, "other")));
        var first = fixtures.topUp().topUp(ACTOR, USER, 100, "k1", "客服补偿");

        var replay = fixtures.topUp().topUp(ACTOR, otherUser, 100, "k1", "手滑换了个人");

        assertThat(replay).isEqualTo(first);
        assertThat(fixtures.wallets().findLedgerByUserId(USER)).hasSize(1);
        assertThat(fixtures.idempotency().audits()).hasSize(1);
    }

    @Test
    void topUpRejectsANonPositiveAmount() {
        Fixtures fixtures = fixtures();
        TopUpUserWallet topUp = fixtures.topUp();

        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 0, "k1", "客服补偿")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, -5, "k2", "客服补偿")).isInstanceOf(JobException.class);
        assertThat(fixtures.wallets().findLedgerByUserId(USER)).isEmpty();
    }

    @Test
    void topUpRejectsAnUnknownUser() {
        Fixtures fixtures = fixtures();
        when(fixtures.users().findById(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixtures.topUp().topUp(ACTOR, USER, 100, "k1", "客服补偿"))
                .isInstanceOf(JobException.class);
        assertThat(fixtures.wallets().findLedgerByUserId(USER)).isEmpty();
        assertThat(fixtures.idempotency().audits()).isEmpty();
    }

    /** 幂等键与原因是必填项。 */
    @Test
    void topUpRequiresAnIdempotencyKeyAndAReason() {
        Fixtures fixtures = fixtures();
        TopUpUserWallet topUp = fixtures.topUp();

        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 100, null, "客服补偿")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 100, " ", "客服补偿")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 100, "k".repeat(65), "客服补偿"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 100, "k1", null)).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 100, "k1", "r".repeat(201)))
                .isInstanceOf(JobException.class);
        assertThat(fixtures.wallets().findLedgerByUserId(USER)).isEmpty();
    }

    /** 目标用户没有个人租户时直接失败，不替他建租户，也不入账。 */
    @Test
    void topUpWithoutThePersonalTenantCredtisNothing() {
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        InMemoryAdminIdempotencyStore records = new InMemoryAdminIdempotencyStore();
        UserRepository users = mock(UserRepository.class);
        when(users.findById(any())).thenReturn(Optional.of(user(USER, "demo")));
        PersonalTenants noTenant = TestPersonalTenants.anyUser(null);

        TopUpUserWallet topUp = new TopUpUserWallet(users, noTenant, topUpWallet(wallets),
                new AdminIdempotency(records, mapper()), records);

        assertThatThrownBy(() -> topUp.topUp(ACTOR, USER, 100, "k1", "客服补偿"))
                .isInstanceOf(AuthException.class);
        assertThat(wallets.findLedgerByUserId(USER)).isEmpty();
        assertThat(records.audits()).isEmpty();
    }

    // ----- 死信重投 -----

    @Test
    void retryPutsADeadJobBackIntoTheQueue() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        UUID jobId = deadJob(jobs, admin, null);

        AdminJobView view = new RetryAdminJob(jobs, wallets, jobs, admin, assembler(jobs, admin)).retry(jobId);

        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.attempt()).isEqualTo(1);
        // max_attempts = GREATEST(max_attempts, attempt) + 1 = 3 + 1
        assertThat(view.maxAttempts()).isEqualTo(4);
        assertThat(jobs.allOutbox()).hasSize(1);
        assertThat(jobs.allOutbox().get(0).payloadJson()).contains(jobId.toString());
        assertThat(jobs.allOutbox().get(0).aggregateType()).isEqualTo("job");
    }

    @Test
    void retryUnbindsAHoldThatWasAlreadySettled() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        WalletHold hold = wallets.insert(TENANT, USER, null, 10);
        UUID jobId = deadJob(jobs, admin, hold.id());
        wallets.finalizeIfFrozen(hold.id(), WalletHoldStatus.RELEASED);

        AdminJobView view = new RetryAdminJob(jobs, wallets, jobs, admin, assembler(jobs, admin)).retry(jobId);

        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.holdId()).isNull();
    }

    @Test
    void retryKeepsAHoldThatIsStillFrozen() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        WalletHold hold = wallets.insert(TENANT, USER, null, 10);
        UUID jobId = deadJob(jobs, admin, hold.id());

        AdminJobView view = new RetryAdminJob(jobs, wallets, jobs, admin, assembler(jobs, admin)).retry(jobId);

        assertThat(view.holdId()).isEqualTo(hold.id());
    }

    @Test
    void retryKeepsTheAuditTrailOfTheOldAttempts() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        UUID jobId = deadJob(jobs, admin, null);
        jobs.start(jobId, 1);

        AdminJobView view = new RetryAdminJob(jobs, wallets, jobs, admin, assembler(jobs, admin)).retry(jobId);

        assertThat(view.steps()).hasSize(1);
        assertThat(view.steps().get(0).attempt()).isEqualTo(1);
    }

    @Test
    void retryRejectsAJobThatIsNotDeadOrFailed() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        UUID jobId = jobs.insertIfAbsent(TENANT, USER, "image.generate", "gpt-image-2", null, null, null, null, "{}", "k1", null).orElseThrow().id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.SUCCEEDED, null);
        admin.putJob(jobId, USER, "demo", "succeeded", "", 1, 3, null, null);

        RetryAdminJob retry = new RetryAdminJob(jobs, new InMemoryWalletStore(), jobs, admin, assembler(jobs, admin));

        assertThatThrownBy(() -> retry.retry(jobId)).isInstanceOf(JobException.class);
        assertThat(jobs.allOutbox()).isEmpty();
    }

    @Test
    void retryOnAMissingJobIsNotFound() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();

        RetryAdminJob retry = new RetryAdminJob(jobs, new InMemoryWalletStore(), jobs, admin, assembler(jobs, admin));

        assertThatThrownBy(() -> retry.retry(UUID.randomUUID()))
                .isInstanceOf(JobException.class)
                .hasMessage("JOB_NOT_FOUND");
    }

    // ----- 任务列表 -----

    @Test
    void jobListCarriesTheStepsAndTheUpstreamRawText() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        UUID jobId = deadJob(jobs, admin, null);
        jobs.start(jobId, 1);
        admin.putProviderTask(jobId, null, "req-1", "500", null);

        List<AdminJobView> views = new ListAdminJobs(admin, assembler(jobs, admin)).list("dead", null, 50, true);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).providerTasks()).hasSize(1);
        assertThat(views.get(0).providerTasks().get(0).rawStatus()).isEqualTo("500");
        assertThat(views.get(0).providerTasks().get(0).upstreamRequestId()).isEqualTo("req-1");
        assertThat(views.get(0).steps()).hasSize(1);
    }

    /** 只有 job.read、没有 job.provider_raw：裁掉原文，任务对象和数组本身照给。 */
    @Test
    void jobListWithoutTheRawPermissionHidesTheUpstreamText() throws Exception {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();
        UUID jobId = deadJob(jobs, admin, null);
        admin.putProviderTask(jobId, null, "req-1", "500", mapper().readTree("{\"error\":\"boom\"}"));

        AdminJobView view = new ListAdminJobs(admin, assembler(jobs, admin))
                .list("dead", null, 50, false)
                .get(0);

        assertThat(view.providerTasks()).hasSize(1);
        assertThat(view.providerTasks().get(0).rawStatus()).isNull();
        assertThat(view.providerTasks().get(0).rawBody()).isNull();
        assertThat(view.providerTasks().get(0).upstreamRequestId()).isEqualTo("req-1");
        assertThat(view.status()).isEqualTo("dead");
    }

    @Test
    void jobListRejectsAnIllegalStatus() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();

        assertThatThrownBy(() -> new ListAdminJobs(admin, assembler(jobs, admin)).list("nope", null, 50, true))
                .isInstanceOf(JobException.class);
    }

    // ----- 退款（幂等 + 审计，扣款语义仍走既有用例）-----

    @Test
    void theSameRefundKeyRefundsOnlyOnce() {
        RefundFixtures fixtures = refundFixtures();

        var first = fixtures.service().refund(ACTOR, fixtures.orderId(), "k1", "重复支付退款");
        var replay = fixtures.service().refund(ACTOR, fixtures.orderId(), "k1", "换个原因再来一次");

        assertThat(replay).isEqualTo(first);
        verify(fixtures.refund(), times(1)).refund(fixtures.orderId());
        assertThat(fixtures.records().audits()).hasSize(1);
        var audit = fixtures.records().audits().get(0);
        assertThat(audit.actorUserId()).isEqualTo(ACTOR);
        assertThat(audit.permissionCode()).isEqualTo("recharge.refund");
        assertThat(audit.targetType()).isEqualTo("recharge_order");
        assertThat(audit.targetId()).isEqualTo(fixtures.orderId().toString());
        assertThat(audit.tenantId()).isEqualTo(TENANT);
        assertThat(audit.summary()).isEqualTo("重复支付退款");
    }

    @Test
    void refundRequiresAKeyAndAReason() {
        RefundFixtures fixtures = refundFixtures();
        RefundAdminRechargeOrder service = fixtures.service();

        assertThatThrownBy(() -> service.refund(ACTOR, fixtures.orderId(), null, "重复支付退款"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> service.refund(ACTOR, fixtures.orderId(), "k1", " "))
                .isInstanceOf(JobException.class);
        verify(fixtures.refund(), never()).refund(any());
        assertThat(fixtures.records().audits()).isEmpty();
    }

    // ----- 动态文案热更新 -----

    @Test
    void i18nUpsertWritesTheTemplate() {
        InMemoryI18nMessageRepository repository = new InMemoryI18nMessageRepository();
        UpsertI18nMessage upsert = new UpsertI18nMessage(repository);

        upsert.upsert("WALLET_INSUFFICIENT", "zh", "余额不足，请联系管理员充值。");

        assertThat(repository.findByKeyAndLocale("WALLET_INSUFFICIENT", "zh").orElseThrow().template())
                .isEqualTo("余额不足，请联系管理员充值。");
    }

    @Test
    void i18nUpsertValidatesKeyLocaleAndTemplate() {
        UpsertI18nMessage upsert = new UpsertI18nMessage(new InMemoryI18nMessageRepository());

        assertThatThrownBy(() -> upsert.upsert(" ", "zh", "x")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> upsert.upsert("K", "fr", "x")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> upsert.upsert("K", "zh", " ")).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> upsert.upsert("K", "zh", "x".repeat(1001))).isInstanceOf(JobException.class);
    }

    // ----- 模型展示配置 -----

    @Test
    void modelConfigCanBeTakenDownAndBroughtBack() {
        InMemoryAdminStore admin = new InMemoryAdminStore();
        UpdateModelDisplayConfig update = new UpdateModelDisplayConfig(admin);

        update.update("gpt-image-2", "GPT Image 2", "image.generate", true, 10);
        assertThat(new ListEnabledModels(admin).list()).hasSize(1);

        update.update("gpt-image-2", "GPT Image 2", null, false, null);
        assertThat(new ListEnabledModels(admin).list()).isEmpty();
        // 没传的字段沿用旧值
        assertThat(admin.findByModel("gpt-image-2").orElseThrow().capability()).isEqualTo("image.generate");
        assertThat(admin.findByModel("gpt-image-2").orElseThrow().sortOrder()).isEqualTo(10);
    }

    @Test
    void modelConfigRejectsAnEmptyModelOrDisplayName() {
        UpdateModelDisplayConfig update = new UpdateModelDisplayConfig(new InMemoryAdminStore());

        assertThatThrownBy(() -> update.update(" ", "X", "image.generate", true, 1)).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> update.update("m", " ", "image.generate", true, 1)).isInstanceOf(JobException.class);
    }

    // ----- list 参数收敛 -----

    @Test
    void theLimitIsClampedToTheAllowedRange() {
        assertThat(AdminLimits.limit(0)).isEqualTo(1);
        assertThat(AdminLimits.limit(-10)).isEqualTo(1);
        assertThat(AdminLimits.limit(999)).isEqualTo(200);
        assertThat(AdminLimits.limit(30)).isEqualTo(30);
    }

    // ----- 测试辅助 -----

    private record RefundFixtures(RefundAdminRechargeOrder service, RefundRechargeOrder refund, UUID orderId,
            InMemoryAdminIdempotencyStore records) {
    }

    /** 退款只验「幂等 + 审计」，真正的扣款与渠道退款顺序由 {@code RefundRechargeOrderTest} 负责。 */
    private static RefundFixtures refundFixtures() {
        UUID orderId = UUID.randomUUID();
        RechargeOrder order = new RechargeOrder(orderId, TENANT, USER, "flr_1", "zpay", "t1", "p10", 1000, 100,
                RechargeOrderStatus.PAID, "127.0.0.1", null, null, null, "n", null, null, 0L,
                UUID.randomUUID(), Instant.now(), null, null, RechargeOrderKind.RECHARGE, null,
                Instant.now(), Instant.now());
        RechargeOrderRepository orders = mock(RechargeOrderRepository.class);
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        RefundRechargeOrder refund = mock(RefundRechargeOrder.class);
        when(refund.refund(orderId)).thenReturn(RechargeOrderView.ofStatusOnly(order));
        InMemoryAdminIdempotencyStore records = new InMemoryAdminIdempotencyStore();
        RefundAdminRechargeOrder service = new RefundAdminRechargeOrder(orders, refund,
                new AdminIdempotency(records, mapper()), records);
        return new RefundFixtures(service, refund, orderId, records);
    }

    /** 加额用例的那一套：真实用例 + 内存幂等表 / 审计表，不碰 Postgres。 */
    private record Fixtures(UserRepository users, TopUpUserWallet topUp, InMemoryWalletStore wallets,
            InMemoryAdminIdempotencyStore idempotency) {
    }

    private static Fixtures fixtures() {
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        InMemoryAdminIdempotencyStore records = new InMemoryAdminIdempotencyStore();
        UserRepository users = mock(UserRepository.class);
        when(users.findById(USER)).thenReturn(Optional.of(user(USER, "demo")));
        TopUpUserWallet topUp = new TopUpUserWallet(users, TestPersonalTenants.anyUser(), topUpWallet(wallets),
                new AdminIdempotency(records, mapper()), records);
        return new Fixtures(users, topUp, wallets, records);
    }

    private static TopUpWallet topUpWallet(InMemoryWalletStore wallets) {
        CreditGrantService grants = new CreditGrantService(new InMemoryCreditGrantStore(), wallets, wallets);
        return new TopUpWallet(
                new WalletService(wallets, wallets, wallets, grants),
                new BillingProperties(10, 0, 200, 50),
                grants);
    }

    /** 与生产一致的 jsonb 存取：Instant 走 ISO 文本，运维幂等键重放才能字节级还原响应。 */
    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static AdminJobViewAssembler assembler(InMemoryJobStore jobs, InMemoryAdminStore admin) {
        return new AdminJobViewAssembler(jobs, admin);
    }

    private static User user(UUID id, String username) {
        Instant now = Instant.now();
        return new User(id, username, null, null, "hash", "zh", "active", now, now);
    }

    /** 造一个 dead 任务：created → queued → claim → dead。 */
    private static UUID deadJob(InMemoryJobStore jobs, InMemoryAdminStore admin, UUID holdId) {
        Job job = jobs.insertIfAbsent(TENANT, USER, "image.generate", "gpt-image-2", null, null, null, null, "{}", "k1", holdId).orElseThrow();
        jobs.updateStatus(job.id(), JobStatus.CREATED, JobStatus.QUEUED, null);
        jobs.claim(job.id());
        jobs.updateStatus(job.id(), JobStatus.RUNNING, JobStatus.DEAD, "PROVIDER_UNAVAILABLE");
        admin.putJob(job.id(), USER, "demo", "dead", "a cat", 1, 3, holdId, null);
        // 模拟 JDBC 的读己之写：Admin 行实时投影 job 仓储里的状态。
        admin.useLiveJobs(id -> {
            Job current = jobs.findById(id).orElse(null);
            AdminJob row = admin.storedJob(id);
            if (current == null || row == null) {
                return null;
            }
            return new AdminJob(current.id(), current.userId(), row.username(), current.status().value(),
                    current.capability(), current.model(), row.prompt(), current.attempt(), current.maxAttempts(),
                    current.holdId(), row.holdStatus(), current.errorCode(), current.createdAt(), current.updatedAt());
        });
        return job.id();
    }
}
