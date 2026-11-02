package art.pilipala.api.job.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.billing.application.CreditGrantService;
import art.pilipala.api.billing.application.JobCostPolicy;
import art.pilipala.api.billing.application.WalletService;
import art.pilipala.api.billing.infra.BillingProperties;
import art.pilipala.api.billing.support.InMemoryCreditGrantStore;
import art.pilipala.api.billing.support.InMemoryWalletStore;
import art.pilipala.api.i18n.InMemoryI18nMessageRepository;
import art.pilipala.api.i18n.application.I18nMessageService;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.support.InMemoryJobStore;
import art.pilipala.api.storage.TosProperties;
import art.pilipala.api.storage.TosUrlSigner;
import org.junit.jupiter.api.Test;

/**
 * 生图历史用例：按创建时间倒序、提示词与标价、成功任务现签读地址、错误文案本地化。
 *
 * <p>历史按当前租户列：同一租户里换设备也看得见，别的租户的任务看不见。
 */
class ListUserJobsTest {

    private final InMemoryJobStore jobs = new InMemoryJobStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private final InMemoryWalletStore wallets = new InMemoryWalletStore();
    private final ListUserJobs service = new ListUserJobs(
            jobs,
            mapper,
            new HoldSettlement(walletService(wallets)),
            new JobCostPolicy(new BillingProperties(10, 0, 200, 50)),
            new TosUrlSigner(new TosProperties("stub", "http://127.0.0.1:18080", "cn-beijing", "pilipala-dev",
                    "pilipala-local-ak", "pilipala-local-sk", 900, 15000, "/tmp/pilipala-tos")),
            new I18nMessageService(new InMemoryI18nMessageRepository()));

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID ownerTenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();

    @Test
    void onlyTheCurrentTenantsJobsAreListed() {
        succeeded(owner, ownerTenant, "k1", "{\"prompt\":\"a garden\"}", "anytoken/img/a/1.png");
        succeeded(other, otherTenant, "k2", "{\"prompt\":\"别人的\"}", "anytoken/img/b/1.png");

        List<JobHistoryItemView> items = service.list(ownerTenant, 50, "zh");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).prompt()).isEqualTo("a garden");
        assertThat(items.get(0).status()).isEqualTo("succeeded");
    }

    @Test
    void aSucceededJobCarriesSignedResultUrls() {
        succeeded(owner, ownerTenant, "k1", "{\"prompt\":\"a garden\"}", "anytoken/img/a/1.png");

        JobHistoryItemView item = service.list(ownerTenant, 50, "zh").get(0);

        assertThat(item.resultUrls()).hasSize(1);
        assertThat(item.resultUrls().get(0)).startsWith("http://127.0.0.1:18080/pilipala-dev/anytoken/img/a/1.png?");
        assertThat(item.costCredits()).isEqualTo(10);
        assertThat(item.finishedAt()).isNotNull();
    }

    @Test
    void aFailedJobCarriesTheFriendlyMessageAndNoResultUrls() {
        UUID jobId = jobs.insertIfAbsent(ownerTenant, owner, "image.generate", "demo", null, null, null, null,
                "{\"prompt\":\"x\"}", "k1", null).orElseThrow().id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.DEAD, "PROVIDER_UNAVAILABLE");

        JobHistoryItemView item = service.list(ownerTenant, 50, "zh").get(0);

        assertThat(item.status()).isEqualTo("dead");
        assertThat(item.resultUrls()).isEmpty();
        assertThat(item.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(item.message()).isEqualTo("生成服务暂时不可用，请稍后重试。");
        assertThat(item.finishedAt()).isNotNull();
    }

    @Test
    void anUnfinishedJobHasNoFinishedAt() {
        jobs.insertIfAbsent(ownerTenant, owner, "image.generate", "demo", null, null, null, null,
                "{\"prompt\":\"x\"}", "k1", null);

        JobHistoryItemView item = service.list(ownerTenant, 50, "zh").get(0);

        assertThat(item.finishedAt()).isNull();
        assertThat(item.errorCode()).isNull();
    }

    @Test
    void theLimitIsClampedToOneHundred() {
        succeeded(owner, ownerTenant, "k1", "{\"prompt\":\"x\"}", "anytoken/img/a/1.png");

        assertThat(service.list(ownerTenant, 0, "zh")).hasSize(1);
        assertThat(service.list(ownerTenant, 5000, "zh")).hasSize(1);
    }

    private static WalletService walletService(InMemoryWalletStore wallets) {
        CreditGrantService grants = new CreditGrantService(new InMemoryCreditGrantStore(), wallets, wallets);
        return new WalletService(wallets, wallets, wallets, grants);
    }

    private void succeeded(UUID userId, UUID tenantId, String idempotencyKey, String paramsJson, String objectKey) {
        UUID jobId = jobs.insertIfAbsent(tenantId, userId, "image.generate", "demo", null, null, null, null,
                paramsJson, idempotencyKey, null).orElseThrow().id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.RUNNING, null);
        jobs.updateResult(jobId, "[\"" + objectKey + "\"]");
        jobs.updateStatus(jobId, JobStatus.RUNNING, JobStatus.SUCCEEDED, null);
    }

    /** 同一租户里换设备（不同操作者）也能看见同一批任务。 */
    @Test
    void anotherDeviceInTheSameTenantSeesTheSameHistory() {
        UUID secondDevice = UUID.randomUUID();
        jobs.insertIfAbsent(ownerTenant, secondDevice, "image.generate", "demo", null, null, null, null,
                "{\"prompt\":\"shared\"}", "k3", null);
        succeeded(owner, ownerTenant, "k1", "{\"prompt\":\"a garden\"}", "anytoken/img/a/1.png");

        assertThat(service.list(ownerTenant, 50, "zh")).hasSize(2);
    }
}
