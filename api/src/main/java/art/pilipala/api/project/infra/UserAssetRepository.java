package art.pilipala.api.project.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.project.domain.UserAsset;

/**
 * user_asset 表的存取契约。SQL 只在 {@link MybatisUserAssetRepository}。
 */
public interface UserAssetRepository {

    /**
     * 把一条生成结果幂等写进素材表：同一 (job_id, object_key) 只会有一行。
     */
    void insertGeneratedIfAbsent(UUID tenantId, UUID userId, UUID jobId, String objectKey, String mimeType);

    /** 当前租户的素材列表，按创建时间倒序。 */
    List<UserAsset> findByTenantAssets(UUID tenantId, int limit);

    /**
     * 删除当前租户的素材。
     *
     * @return 真的删掉了才为 true
     */
    boolean deleteAsset(UUID assetId, UUID tenantId);
}
