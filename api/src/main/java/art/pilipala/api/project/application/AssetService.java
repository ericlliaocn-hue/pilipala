package art.pilipala.api.project.application;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobOutputs;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.project.domain.UserAsset;
import art.pilipala.api.project.infra.ProjectException;
import art.pilipala.api.project.infra.UserAssetRepository;
import art.pilipala.api.storage.TosUrlSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 云端素材用例：把生成结果同步进素材表、按需现签读地址、删除素材。
 *
 * <p>素材按当前租户读写（{@code tenant_id}），插入时保留提交任务的操作者 {@code user_id}。
 *
 * <p>素材表只存 TOS 对象键；读地址一律在这里现签（ADR 0003）。
 * 预签名 URL **绝不** 落库——过期后它就是废的。
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    /** 每次同步扫多少条历史任务。 */
    public static final int SYNC_SCAN_LIMIT = 100;
    /** 列表最多返回多少条。 */
    public static final int MAX_LIST_SIZE = 100;

    private final UserAssetRepository assets;
    private final JobRepository jobs;
    private final TosUrlSigner tosUrlSigner;
    private final ObjectMapper mapper;

    public AssetService(UserAssetRepository assets, JobRepository jobs, TosUrlSigner tosUrlSigner, ObjectMapper mapper) {
        this.assets = assets;
        this.jobs = jobs;
        this.tosUrlSigner = tosUrlSigner;
        this.mapper = mapper;
    }

    /**
     * 当前租户的云端素材列表：先同步已成功任务的输出，再现签读地址返回。
     *
     * @param limit 最多返回多少条（1..100）
     */
    public List<UserAssetView> listUserAssets(UUID tenantId, int limit) {
        syncFromSucceededJobs(tenantId);
        return assets.findByTenantAssets(tenantId, clamp(limit)).stream().map(this::toView).toList();
    }

    /**
     * 把该租户已成功任务的输出幂等写进素材表，素材挂在该租户上。
     *
     * <p>重复调用不报错也不产生重复行（唯一约束 {@code (job_id, object_key)}）。
     */
    public void syncFromSucceededJobs(UUID tenantId) {
        for (Job job : jobs.findByTenantOrderByCreatedAtDesc(tenantId, SYNC_SCAN_LIMIT)) {
            if (job.status() != JobStatus.SUCCEEDED) {
                continue;
            }
            for (String objectKey : objectKeysOf(job.resultObjectKeysJson())) {
                // 视频产物归档成 video/mp4，图片产物仍是 image/png（WP12）。
                assets.insertGeneratedIfAbsent(tenantId, job.userId(), job.id(), objectKey,
                        JobOutputs.mimeTypeOf(job.capability(), objectKey));
            }
        }
    }

    public DeleteProjectView deleteAsset(UUID tenantId, UUID assetId) {
        if (!assets.deleteAsset(assetId, tenantId)) {
            throw ProjectException.assetNotFound();
        }
        return new DeleteProjectView(assetId.toString(), true);
    }

    private UserAssetView toView(UserAsset asset) {
        return new UserAssetView(
                asset.id().toString(),
                asset.projectId() == null ? null : asset.projectId().toString(),
                asset.jobId() == null ? null : asset.jobId().toString(),
                asset.source().value(),
                asset.objectKey(),
                signedUrlOf(asset.objectKey()),
                asset.mimeType(),
                asset.createdAt());
    }

    /** 非法对象键只让这一条没有读地址，不打挂整个列表。 */
    private String signedUrlOf(String objectKey) {
        try {
            return tosUrlSigner.signGetUrl(objectKey);
        } catch (IllegalArgumentException e) {
            log.warn("跳过非法 TOS 对象键 key={}", objectKey);
            return null;
        }
    }

    private List<String> objectKeysOf(String resultObjectKeysJson) {
        if (resultObjectKeysJson == null || resultObjectKeysJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode array = mapper.readTree(resultObjectKeysJson);
            if (!array.isArray()) {
                return List.of();
            }
            List<String> keys = new java.util.ArrayList<>();
            array.forEach(item -> {
                String key = item.asText();
                if (key != null && !key.isBlank()) {
                    keys.add(key);
                }
            });
            return keys;
        } catch (JsonProcessingException e) {
            log.warn("result_object_keys 解析失败");
            return List.of();
        }
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(MAX_LIST_SIZE, limit));
    }
}
