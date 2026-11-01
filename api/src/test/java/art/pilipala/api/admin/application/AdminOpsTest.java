package art.pilipala.api.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.domain.AdminJob;
import art.pilipala.api.admin.support.InMemoryAdminStore;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.billing.application.CreditGrantService;
import art.pilipala.api.billing.application.TopUpWallet;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.domain.WalletHold;
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
 * 运维后台用例测试：加额走账本、重投守状态与冻结单、文案热更新、模型上下架。
 */
class AdminOpsTest {

    private static final UUID USER = UUID.randomUUID();

    // ----- 加额（单账本红线）-----

    @Test
    void topUpCreditsTheWalletAndWritesALedgerEntry() {
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        UserRepository users = mock(UserRepository.class);
        when(users.findById(USER)).thenReturn(Optional.of(user(USER, "demo")));

        TopUpUserWallet topUp = new TopUpUserWallet(users, topUpWallet(wallets));
        var view = topUp.topUp(USER, 100);

        assertThat(view.available()).isEqualTo(100L);
        assertThat(view.frozen()).isZero();
        List<WalletLedgerType> types = wallets.findLedgerByUserId(USER).stream().map(entry -> entry.type()).toList();
        assertThat(types).containsExactly(WalletLedgerType.TOPUP);
        assertThat(wallets.findLedgerByUserId(USER).get(0).amount()).isEqualTo(100L);
    }

    @Test
    void topUpRejectsANonPositiveAmount() {
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        UserRepository users = mock(UserRepository.class);
        when(users.findById(any())).thenReturn(Optional.of(user(USER, "demo")));

        TopUpUserWallet topUp = new TopUpUserWallet(users, topUpWallet(wallets));

        assertThatThrownBy(() -> topUp.topUp(USER, 0)).isInstanceOf(JobException.class);
        assertThatThrownBy(() -> topUp.topUp(USER, -5)).isInstanceOf(JobException.class);
        assertThat(wallets.findLedgerByUserId(USER)).isEmpty();
    }

    @Test
    void topUpRejectsAnUnknownUser() {
        InMemoryWalletStore wallets = new InMemoryWalletStore();
        UserRepository users = mock(UserRepository.class);
        when(users.findById(USER)).thenReturn(Optional.empty());

        TopUpUserWallet topUp = new TopUpUserWallet(users, topUpWallet(wallets));

        assertThatThrownBy(() -> topUp.topUp(USER, 100)).isInstanceOf(JobException.class);
        assertThat(wallets.findLedgerByUserId(USER)).isEmpty();
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
        WalletHold hold = wallets.insert(USER, null, 10);
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
        WalletHold hold = wallets.insert(USER, null, 10);
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
        UUID jobId = jobs.insertIfAbsent(USER, "image.generate", "gpt-image-2", null, null, null, null, "{}", "k1", null).orElseThrow().id();
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

        List<AdminJobView> views = new ListAdminJobs(admin, assembler(jobs, admin)).list("dead", null, 50);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).providerTasks()).hasSize(1);
        assertThat(views.get(0).providerTasks().get(0).rawStatus()).isEqualTo("500");
        assertThat(views.get(0).providerTasks().get(0).upstreamRequestId()).isEqualTo("req-1");
        assertThat(views.get(0).steps()).hasSize(1);
    }

    @Test
    void jobListRejectsAnIllegalStatus() {
        InMemoryJobStore jobs = new InMemoryJobStore();
        InMemoryAdminStore admin = new InMemoryAdminStore();

        assertThatThrownBy(() -> new ListAdminJobs(admin, assembler(jobs, admin)).list("nope", null, 50))
                .isInstanceOf(JobException.class);
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

    private static TopUpWallet topUpWallet(InMemoryWalletStore wallets) {
        CreditGrantService grants = new CreditGrantService(new InMemoryCreditGrantStore(), wallets, wallets);
        return new TopUpWallet(
                new WalletService(wallets, wallets, wallets, grants),
                new BillingProperties(10, 0, 200, 50),
                grants);
    }

    private static AdminJobViewAssembler assembler(InMemoryJobStore jobs, InMemoryAdminStore admin) {
        return new AdminJobViewAssembler(jobs, admin);
    }

    private static User user(UUID id, String username) {
        Instant now = Instant.now();
        return new User(id, username, null, null, "hash", "zh", now, now);
    }

    /** 造一个 dead 任务：created → queued → claim → dead。 */
    private static UUID deadJob(InMemoryJobStore jobs, InMemoryAdminStore admin, UUID holdId) {
        Job job = jobs.insertIfAbsent(USER, "image.generate", "gpt-image-2", null, null, null, null, "{}", "k1", holdId).orElseThrow();
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
