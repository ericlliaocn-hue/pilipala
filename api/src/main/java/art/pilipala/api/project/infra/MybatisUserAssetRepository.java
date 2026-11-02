package art.pilipala.api.project.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.project.domain.UserAsset;

@Repository
public class MybatisUserAssetRepository implements UserAssetRepository {

    private final UserAssetMapper mapper;

    public MybatisUserAssetRepository(UserAssetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertGeneratedIfAbsent(UUID tenantId, UUID userId, UUID jobId, String objectKey, String mimeType) {
        mapper.insertGeneratedIfAbsent(tenantId, userId, jobId, objectKey, mimeType);
    }

    @Override
    public List<UserAsset> findByTenantAssets(UUID tenantId, int limit) {
        return mapper.findByTenantAssets(tenantId, limit);
    }

    @Override
    public boolean deleteAsset(UUID assetId, UUID tenantId) {
        return mapper.deleteAsset(assetId, tenantId) > 0;
    }
}
