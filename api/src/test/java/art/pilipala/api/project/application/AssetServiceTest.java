package art.pilipala.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.common.ErrorCodes;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.support.InMemoryJobStore;
import art.pilipala.api.project.infra.ProjectException;
import art.pilipala.api.project.support.InMemoryProjectStore;
import art.pilipala.api.storage.TosProperties;
import art.pilipala.api.storage.TosUrlSigner;
import org.junit.jupiter.api.Test;

/**
 * 云端素材用例：自动同步生成结果、现签读地址、删除与跨租户隔离。
 */
class AssetServiceTest {

    private final InMemoryProjectStore store = new InMemoryProjectStore();
    private final InMemoryJobStore jobs = new InMemoryJobStore();
    private final ObjectMapper mapper = new ObjectMapper();
    private final TosUrlSigner signer = new TosUrlSigner(new TosProperties("stub", "http://127.0.0.1:18080", "cn-beijing",
            "pilipala-dev", "pilipala-local-ak", "pilipala-local-sk", 900, 15000, "/tmp/pilipala-tos"));
    private final AssetService service = new AssetService(store, jobs, signer, mapper);

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID ownerTenant = UUID.randomUUID();
    private final UUID otherTenant = UUID.randomUUID();

    @Test
    void succeededJobsAreSyncedIntoTheAssetTable() {
        UUID jobId = succeededJob(owner, ownerTenant, "anytoken/img/" + owner + "/1.png");

        List<UserAssetView> assets = service.listUserAssets(ownerTenant, 100);

        assertThat(assets).hasSize(1);
        assertThat(assets.get(0).jobId()).isEqualTo(jobId.toString());
        assertThat(assets.get(0).source()).isEqualTo("generated");
        assertThat(assets.get(0).objectKey()).isEqualTo("anytoken/img/" + owner + "/1.png");
        assertThat(assets.get(0).signedUrl()).startsWith("http://127.0.0.1:18080/pilipala-dev/anytoken/img/");
    }

    @Test
    void syncingTwiceDoesNotDuplicateRows() {
        succeededJob(owner, ownerTenant, "anytoken/img/x/1.png");

        service.listUserAssets(ownerTenant, 100);
        service.listUserAssets(ownerTenant, 100);

        assertThat(store.assetCount()).isEqualTo(1);
    }

    @Test
    void anotherTenantsJobsAreNotSynced() {
        succeededJob(other, otherTenant, "anytoken/img/other/1.png");

        assertThat(service.listUserAssets(ownerTenant, 100)).isEmpty();
    }

    @Test
    void failedJobsAreNotSynced() {
        UUID jobId = jobs.insertIfAbsent(ownerTenant, owner, "image.generate", "demo", null, null, null, null,
                "{}", "k1", null).orElseThrow().id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.DEAD, "JOB_FAILED");

        assertThat(service.listUserAssets(ownerTenant, 100)).isEmpty();
    }

    @Test
    void deleteOfAnotherTenantAssetIsNotFound() {
        succeededJob(owner, ownerTenant, "anytoken/img/x/1.png");
        UserAssetView asset = service.listUserAssets(ownerTenant, 100).get(0);

        assertThatThrownBy(() -> service.deleteAsset(otherTenant, UUID.fromString(asset.id())))
                .isInstanceOf(ProjectException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCodes.ASSET_NOT_FOUND);
    }

    @Test
    void deleteRemovesTheAssetRow() {
        succeededJob(owner, ownerTenant, "anytoken/img/x/1.png");
        UserAssetView asset = service.listUserAssets(ownerTenant, 100).get(0);

        DeleteProjectView deleted = service.deleteAsset(ownerTenant, UUID.fromString(asset.id()));

        assertThat(deleted.deleted()).isTrue();
        assertThat(store.assetCount()).isEqualTo(0);
    }

    /**
     * WP10 的自动同步语义：素材行由「已成功的任务」驱动，
     * 所以删掉一条 generated 素材后，下一次列表会按任务历史重新同步回来。
     */
    @Test
    void aDeletedGeneratedAssetIsSyncedBackFromTheSucceededJob() {
        succeededJob(owner, ownerTenant, "anytoken/img/x/1.png");
        UserAssetView asset = service.listUserAssets(ownerTenant, 100).get(0);
        service.deleteAsset(ownerTenant, UUID.fromString(asset.id()));

        assertThat(service.listUserAssets(ownerTenant, 100)).hasSize(1);
    }

    /** 建一条成功且带结果对象键的 job。 */
    private UUID succeededJob(UUID userId, UUID tenantId, String objectKey) {
        UUID jobId = jobs.insertIfAbsent(tenantId, userId, "image.generate", "demo", null, null, null, null,
                        "{\"prompt\":\"hi\"}", "k-" + UUID.randomUUID(), null)
                .orElseThrow()
                .id();
        jobs.updateStatus(jobId, JobStatus.CREATED, JobStatus.RUNNING, null);
        jobs.updateResult(jobId, "[\"" + objectKey + "\"]");
        jobs.updateStatus(jobId, JobStatus.RUNNING, JobStatus.SUCCEEDED, null);
        return jobId;
    }
}
