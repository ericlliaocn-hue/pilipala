package art.pilipala.api.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.auth.application.AccessCheck;
import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.TenantMapper;
import art.pilipala.api.auth.infra.UserRepository;
import art.pilipala.api.auth.support.TestCurrentTenants;
import art.pilipala.api.auth.support.TestUsers;
import art.pilipala.api.billing.application.CreditGrantService;
import art.pilipala.api.billing.application.JobCostPolicy;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.domain.WalletHold;
import art.pilipala.api.billing.domain.WalletHoldStatus;
import art.pilipala.api.billing.domain.WalletLedgerEntry;
import art.pilipala.api.billing.domain.WalletLedgerType;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.billing.support.LedgerAssertions;
import art.pilipala.api.i18n.InMemoryI18nMessageRepository;
import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.domain.ModelProduct;
import art.pilipala.api.job.domain.ModelProductGroup;
import art.pilipala.api.storage.TosProperties;
import art.pilipala.api.storage.TosUrlSigner;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobMessageTransport;
import art.pilipala.api.job.infra.FakeJobTransport;
import art.pilipala.api.job.infra.JobRequestHasher;
import art.pilipala.api.job.support.FakeJobLease;
import art.pilipala.api.job.support.InMemoryJobStore;
import art.pilipala.api.job.support.InMemoryModelCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Job 域的状态机与幂等：成功、重试回 queued、取消、dead。
 *
 * <p>用内存仓储 + FakeTransport 跑，不依赖 Postgres / Redis / RabbitMQ；
 * 仓储语义与 JDBC 实现一致（见 {@link InMemoryJobStore}）。
 *
 * <p>WP6 起任务与钱包联动：提交先冻结，succeeded 确认实扣，
 * cancelled / dead 解冻退回，重试期间冻结保持不动。
 *
 * <p>步骤 3 起任务与冻结单都归属提交者的当前租户，{@code user_id} 只是操作者；
 * 冻结前还要过用户状态、租户状态与租户上的 {@code job.submit} 三道闸。
 */
class JobLifecycleTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final String WORKER = "worker-1";
    private static final long COST = 10;

    private final InMemoryJobStore store = new InMemoryJobStore();
    private final InMemoryWalletStore wallets = new InMemoryWalletStore(store::statusOf);
    private final InMemoryModelCatalogRepository catalogRepository = new InMemoryModelCatalogRepository();
    private final ModelCatalog modelCatalog = new ModelCatalog(catalogRepository);
    private final FakeJobTransport transport = new FakeJobTransport();
    private final FakeJobLease lease = new FakeJobLease();
    private final ObjectMapper mapper = new ObjectMapper();
    private final BillingProperties billing = new BillingProperties(COST, 0, 200, 50);

    private final CreditGrantService grantService = new CreditGrantService(
            new InMemoryCreditGrantStore(), wallets, wallets);
    private final WalletService walletService = new WalletService(wallets, wallets, wallets, grantService);
    private final HoldSettlement holdSettlement = new HoldSettlement(walletService);

    private final InMemoryI18nMessageRepository i18nRepository = new InMemoryI18nMessageRepository();
    private final I18nMessageService i18n = new I18nMessageService(i18nRepository);

    private final CurrentTenant currentTenant = TestCurrentTenants.forTenants(
            Map.of(USER, TENANT, OTHER_USER, OTHER_TENANT));
    private final TenantMapper permissionMapper = mock(TenantMapper.class);
    private final AccessCheck access = new AccessCheck(permissionMapper);
    private final UserRepository users = TestUsers.active(USER, OTHER_USER);

    private final SubmitJob submitJob = new SubmitJob(store, store, store, new JobRequestHasher(), walletService,
            new JobCostPolicy(billing), modelCatalog, currentTenant, access, users);
    private final GetJob getJob = new GetJob(store, holdSettlement, i18n);
    private final TosUrlSigner tosUrlSigner = new TosUrlSigner(new TosProperties("stub", "http://127.0.0.1:18080",
            "cn-beijing", "pilipala-dev", "pilipala-local-ak", "pilipala-local-sk", 900, 15000, "/tmp/pilipala-tos"));
    private final GetJobResult getJobResult = new GetJobResult(store, mapper, holdSettlement, tosUrlSigner, i18n);
    private final CancelJob cancelJob = new CancelJob(store, holdSettlement, i18n);
    private final ClaimJob claimJob = new ClaimJob(store, store, lease, holdSettlement);
    private final RecordAttemptResult recordAttemptResult =
            new RecordAttemptResult(store, store, store, lease, mapper, holdSettlement);
    private final OutboxDispatchTick dispatchTick = new OutboxDispatchTick(store, transport, 50, 10);

    @BeforeEach
    void topUpTheTestTenant() {
        walletService.credit(TENANT, USER, 100);
        when(permissionMapper.findTenantPermissions(any(), any()))
                .thenReturn(List.of("job.submit"));
    }

    @Test
    void submitCreatesQueuedJobWithOutboxAndIdempotencyRecord() throws Exception {
        SubmitJobView view = submitView("k1");

        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.holdId()).isNotBlank();
        assertThat(store.allJobs()).hasSize(1);
        assertThat(store.allOutbox()).hasSize(1);
        assertThat(store.findByUserAndKey(USER, "k1")).isPresent();
        assertThat(store.findById(UUID.fromString(view.jobId())).orElseThrow().tenantId()).isEqualTo(TENANT);

        JsonNode payload = mapper.readTree(store.allOutbox().get(0).payloadJson());
        assertThat(payload.get("job_id").asText()).isEqualTo(view.jobId());
        assertThat(payload.has("event_id")).isTrue();
        assertThat(payload.has("params")).isFalse();
        // 队列消息里没有钱包金额、hold_id，也没有租户与用户 id（ADR 0003）。
        assertThat(payload.has("hold_id")).isFalse();
        assertThat(payload.has("tenant_id")).isFalse();
        assertThat(payload.has("user_id")).isFalse();
    }

    @Test
    void submitFreezesTheTenantsWallet() {
        SubmitJobView view = submitView("k1");

        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(wallets.walletOf(TENANT).frozen()).isEqualTo(COST);

        WalletHold hold = wallets.allHolds().get(0);
        assertThat(hold.id().toString()).isEqualTo(view.holdId());
        assertThat(hold.tenantId()).isEqualTo(TENANT);
        assertThat(hold.userId()).isEqualTo(USER);
        assertThat(hold.status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(hold.jobId().toString()).isEqualTo(view.jobId());

        WalletLedgerEntry freeze = wallets.findLedgerByUserId(USER).get(1);
        assertThat(freeze.type()).isEqualTo(WalletLedgerType.FREEZE);
        assertThat(freeze.amount()).isEqualTo(COST);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    /** 请求里带别人的租户：FORBIDDEN，job、冻结单、outbox 一行都不写。 */
    @Test
    void submitWithAnotherTenantIsForbiddenAndWritesNothing() {
        UUID someoneElsesTenant = UUID.randomUUID();

        assertThatThrownBy(() -> submitJob.submit(USER, someoneElsesTenant.toString(), "k1",
                "image.generate", "demo", params("hi"), null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).errorCode()).isEqualTo("FORBIDDEN"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(store.allOutbox()).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(100);
        assertThat(wallets.walletOf(TENANT).frozen()).isZero();
    }

    /** 该租户上没有 job.submit：FORBIDDEN，且不冻结。 */
    @Test
    void submitWithoutTheTenantPermissionIsForbidden() {
        when(permissionMapper.findTenantPermissions(USER, TENANT)).thenReturn(List.of("job.read"));
        int freezeLedger = wallets.allLedger().size();

        assertThatThrownBy(() -> submit("k1"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).errorCode()).isEqualTo("FORBIDDEN"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
        assertThat(wallets.allLedger()).hasSize(freezeLedger);
    }

    /** 账号被停用：ACCOUNT_DISABLED，冻结根本不会被调用。 */
    @Test
    void submitWithADisabledUserIsRejectedBeforeFreezing() {
        walletService.freeze(TENANT, USER, 1);
        UserRepository disabled = TestUsers.of(Map.of(USER, TestUsers.user(USER, User.STATUS_DISABLED),
                OTHER_USER, TestUsers.user(OTHER_USER, User.STATUS_ACTIVE)));
        SubmitJob submitForDisabled = new SubmitJob(store, store, store, new JobRequestHasher(), walletService,
                new JobCostPolicy(billing), modelCatalog, currentTenant, access, disabled);

        assertThatThrownBy(() -> submitForDisabled.submit(USER, null, "k-disabled",
                "image.generate", "demo", params("hi"), null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).errorCode()).isEqualTo("ACCOUNT_DISABLED"));

        assertThat(store.allJobs()).isEmpty();
        // 冻结单与流水还是提交前的那一张 / 那两条。
        assertThat(wallets.allHolds()).hasSize(1);
        assertThat(wallets.allLedger()).hasSize(2);
    }

    @Test
    void submitIsRejectedWhenTheBalanceIsNotEnough() {
        assertThatThrownBy(() -> submitJob.submit(OTHER_USER, null, "k1", "image.generate", "demo", params("hi"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("WALLET_INSUFFICIENT"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(store.allOutbox()).isEmpty();
        assertThat(store.findByUserAndKey(OTHER_USER, "k1")).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
        assertThat(wallets.findLedgerByUserId(OTHER_USER)).isEmpty();
        assertThat(wallets.walletOf(OTHER_TENANT).available()).isZero();
        assertThat(wallets.walletOf(OTHER_TENANT).frozen()).isZero();
    }

    @Test
    void sameKeySameBodyReplaysTheSameJobWithoutRepublishing() {
        SubmitJobView first = submitView("k1");
        SubmitJobView second = submitView("k1");

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.holdId()).isEqualTo(first.holdId());
        assertThat(store.allJobs()).hasSize(1);
        assertThat(store.allOutbox()).hasSize(1);
        // 重放不二次冻结。
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(wallets.walletOf(TENANT).frozen()).isEqualTo(COST);
        assertThat(wallets.allHolds()).hasSize(1);
        assertThat(wallets.findLedgerByUserId(USER)).hasSize(2);
    }

    @Test
    void sameKeyDifferentBodyIsAConflict() {
        submit("k1");

        assertThatThrownBy(() -> submitJob.submit(USER, null, "k1", "image.generate", "demo", params("别的"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(store.allJobs()).hasSize(1);
    }

    @Test
    void anotherTenantsJobIsNotFound() {
        UUID jobId = submit("k1");

        assertThatThrownBy(() -> getJob.get(OTHER_TENANT, jobId, "zh"))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("JOB_NOT_FOUND"));
        assertThatThrownBy(() -> getJobResult.result(OTHER_TENANT, jobId, "zh"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> cancelJob.cancel(OTHER_TENANT, jobId, "zh"))
                .isInstanceOf(JobException.class);
    }

    @Test
    void claimThenSucceedMakesTheResultVisibleAndConfirmsTheHold() {
        UUID jobId = submit("k1");

        ClaimResult claim = claimJob.claim(jobId, WORKER);
        assertThat(claim.claimed()).isTrue();
        assertThat(claim.status()).isEqualTo("running");
        assertThat(lease.heldCount()).isEqualTo(1);

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png")));
        assertThat(record.status()).isEqualTo("succeeded");

        JobView view = getJob.get(TENANT, jobId, "zh");
        assertThat(view.status()).isEqualTo("succeeded");
        assertThat(view.attempt()).isEqualTo(1);
        assertThat(view.errorCode()).isNull();

        JobResultView result = getJobResult.result(TENANT, jobId, "zh");
        assertThat(result.outputs()).hasSize(1);
        assertThat(result.outputs().get(0).objectKey()).isEqualTo("img/a.png");
        assertThat(result.outputs().get(0).url()).isNotNull();
        assertThat(result.outputs().get(0).url()).contains("X-Tos-Algorithm=TOS4-HMAC-SHA256");
        assertThat(result.outputs().get(0).url()).contains("X-Tos-Signature=");
        assertThat(lease.heldCount()).isZero();

        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(wallets.walletOf(TENANT).frozen()).isZero();
        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.CONFIRMED);
        assertThat(wallets.findLedgerByUserId(USER).get(2).type()).isEqualTo(WalletLedgerType.CONFIRM);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    @Test
    void failedAttemptGoesBackToQueuedAndIsPublishedAgain() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(record.status()).isEqualTo("queued");
        JobView view = getJob.get(TENANT, jobId, "zh");
        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.attempt()).isEqualTo(1);
        assertThat(view.errorCode()).isEqualTo("JOB_FAILED");

        assertThat(store.allOutbox()).hasSize(2);
        assertThat(dispatchTick.tick()).isEqualTo(2);
        assertThat(transport.sent()).hasSize(2);
        assertThat(store.allOutbox()).allMatch(message -> "sent".equals(message.status()));
    }

    /** WP9：改了仓储里的模板后，任务查询返回的文案随之动态变化。 */
    @Test
    void theMessageFollowsTheTemplateInTheRepository() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);
        recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(getJob.get(TENANT, jobId, "zh").message()).isEqualTo("任务执行失败。");

        i18nRepository.upsert("JOB_FAILED", "zh", "生成失败了，请稍后再试。");

        assertThat(getJob.get(TENANT, jobId, "zh").message()).isEqualTo("生成失败了，请稍后再试。");
        assertThat(getJobResult.result(TENANT, jobId, "zh").message()).isEqualTo("生成失败了，请稍后再试。");
    }

    @Test
    void retryKeepsTheHoldFrozen() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.FROZEN);
        assertThat(wallets.walletOf(TENANT).frozen()).isEqualTo(COST);
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(90);
        assertThat(wallets.findLedgerByUserId(USER)).hasSize(2);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    @Test
    void attemptsExhaustedBecomeDeadAndReleaseTheHold() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("queued");
        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("queued");
        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("dead");

        JobView view = getJob.get(TENANT, jobId, "zh");
        assertThat(view.status()).isEqualTo("dead");
        assertThat(view.attempt()).isEqualTo(3);
        assertThat(view.maxAttempts()).isEqualTo(3);
        assertThat(view.errorCode()).isEqualTo("JOB_FAILED");

        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(TENANT).frozen()).isZero();
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(100);
        assertThat(wallets.findLedgerByUserId(USER).get(2).type()).isEqualTo(WalletLedgerType.RELEASE);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    @Test
    void cancellingAQueuedJobIsImmediateAndIdempotent() {
        UUID jobId = submit("k1");

        JobView cancelled = cancelJob.cancel(TENANT, jobId, "zh");
        assertThat(cancelled.status()).isEqualTo("cancelled");
        assertThat(cancelled.errorCode()).isEqualTo("JOB_CANCELLED");
        assertThat(cancelled.message()).isEqualTo("任务已取消。");

        JobView again = cancelJob.cancel(TENANT, jobId, "zh");
        assertThat(again.status()).isEqualTo("cancelled");
        assertThat(again.errorCode()).isEqualTo("JOB_CANCELLED");

        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(100);
        assertThat(wallets.walletOf(TENANT).frozen()).isZero();
        // 重放不写第二条 release 流水。
        assertThat(wallets.findLedgerByUserId(USER)).hasSize(3);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    @Test
    void cancellingARunningJobKeepsTheIntentUntilTheAttemptReports() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);

        JobView requested = cancelJob.cancel(TENANT, jobId, "zh");
        assertThat(requested.status()).isEqualTo("running");

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));
        assertThat(record.status()).isEqualTo("cancelled");
        assertThat(getJob.get(TENANT, jobId, "zh").errorCode()).isEqualTo("JOB_CANCELLED");
        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(100);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    /** WP5 收口项：运行期间用户取消，即使这次 attempt 成功也收敛为 cancelled。 */
    @Test
    void cancellingARunningJobOverridesASuccessfulAttempt() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);

        cancelJob.cancel(TENANT, jobId, "zh");

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png")));
        assertThat(record.status()).isEqualTo("cancelled");

        JobView view = getJob.get(TENANT, jobId, "zh");
        assertThat(view.status()).isEqualTo("cancelled");
        assertThat(view.errorCode()).isEqualTo("JOB_CANCELLED");
        // 取消的 attempt 不写结果
        assertThat(getJobResult.result(TENANT, jobId, "zh").outputs()).isEmpty();
        // 取消走的是解冻，不是实扣。
        assertThat(wallets.allHolds().get(0).status()).isEqualTo(WalletHoldStatus.RELEASED);
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(100);
        LedgerAssertions.assertInvariants(wallets, TENANT);
    }

    @Test
    void illegalTransitionIsRejected() {
        UUID jobId = submit("k1");

        assertThatThrownBy(() -> recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png"))))
                .isInstanceOf(JobException.class);
        assertThat(getJob.get(TENANT, jobId, "zh").status()).isEqualTo("queued");
    }

    @Test
    void outboxDeliveryFailureKeepsPendingUntilTheLimitIsReached() {
        UUID jobId = submit("k1");
        JobMessageTransport broken = message -> {
            throw new IllegalStateException("broker down");
        };
        OutboxDispatchTick failing = new OutboxDispatchTick(store, broken, 50, 3);

        assertThat(failing.tick()).isZero();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("pending");
        failing.tick();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("pending");
        failing.tick();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("failed");
        assertThat(jobId).isNotNull();
    }

    @Test
    void productSubmitUsesProductPriceAndGatewayGroup() {
        ModelProduct product = product("video-stable", 30, true);
        catalogRepository.product(product)
                .group(new ModelProductGroup(UUID.randomUUID(), product.id(), "default", "sd-2-c4",
                        10, 900, new BigDecimal("2.0"), true));

        SubmitJobView view = submitJob.submit(USER, null, "video-k", "video.generate", product.code(), params("hi"),
                null);

        Job job = store.findById(UUID.fromString(view.jobId())).orElseThrow();
        assertThat(job.tenantId()).isEqualTo(TENANT);
        assertThat(job.productId()).isEqualTo(product.id());
        assertThat(job.targetId()).isNull();
        assertThat(job.gatewayGroup()).isEqualTo("default");
        assertThat(job.gatewayModel()).isEqualTo("sd-2-c4");
        assertThat(job.gatewayTimeoutSeconds()).isEqualTo(900);
        assertThat(job.quotedPrice()).isEqualTo(30);
        assertThat(job.pricingVersion()).isEqualTo("wp21-v1");
        assertThat(job.model()).isEqualTo("video-stable");
        assertThat(wallets.walletOf(TENANT).available()).isEqualTo(70);
        assertThat(wallets.walletOf(TENANT).frozen()).isEqualTo(30);
        assertThat(wallets.allHolds()).hasSize(1);
    }

    @Test
    void disabledProductDoesNotFallBackToLegacyPricing() {
        ModelProduct product = product("video-disabled", 30, false);
        catalogRepository.product(product);

        assertThatThrownBy(() -> submitJob.submit(USER, null, "video-k", "video.generate", product.code(),
                params("hi"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
    }

    @Test
    void productWithoutUsableGroupIsNoAvailableChannel() {
        ModelProduct product = product("video-no-group", 30, true);
        catalogRepository.product(product);

        assertThatThrownBy(() -> submitJob.submit(USER, null, "video-k", "video.generate", product.code(),
                params("hi"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("NO_AVAILABLE_CHANNEL"));

        assertThat(store.allJobs()).isEmpty();
        assertThat(wallets.allHolds()).isEmpty();
    }

    @Test
    void productSubmitWithoutPromptIsValidationError() {
        ModelProduct product = product("video-no-prompt", 30, true);
        catalogRepository.product(product);

        assertThatThrownBy(() -> submitJob.submit(USER, null, "video-k", "video.generate", product.code(),
                mapper.createObjectNode(), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("VALIDATION_ERROR"));
        assertThat(store.allJobs()).isEmpty();
    }

    private UUID submit(String key) {
        return UUID.fromString(submitJob.submit(USER, null, key, "image.generate", "demo", params("hi"), null).jobId());
    }

    private SubmitJobView submitView(String key) {
        return submitJob.submit(USER, null, key, "image.generate", "demo", params("hi"), null);
    }

    private JsonNode params(String prompt) {
        return mapper.createObjectNode().put("prompt", prompt);
    }

    private ModelProduct product(String code, long price, boolean enabled) {
        return new ModelProduct(UUID.randomUUID(), code, "video.generate", "Video", "test", price, "{}", enabled, 10);
    }
}
