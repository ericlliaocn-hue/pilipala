package art.pilipala.api.project.support;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.project.domain.AssetSource;
import art.pilipala.api.project.domain.CanvasProject;
import art.pilipala.api.project.domain.UserAsset;
import art.pilipala.api.project.infra.CanvasProjectRepository;
import art.pilipala.api.project.infra.UserAssetRepository;

/**
 * 内存版项目 / 素材仓储：云端持久化用例不需要 Postgres。
 *
 * <p>行为对齐 {@code Jdbc*}Repository：只查当前租户、版本号自增、
 * (job_id, object_key) 幂等不重。归属租户单独记账，因为领域行只保留操作者。
 */
public class InMemoryProjectStore implements CanvasProjectRepository, UserAssetRepository {

    private final Map<UUID, CanvasProject> projects = new LinkedHashMap<>();
    private final Map<UUID, UUID> projectTenants = new LinkedHashMap<>();
    private final Map<UUID, UserAsset> assets = new LinkedHashMap<>();
    private final Map<UUID, UUID> assetTenants = new LinkedHashMap<>();

    @Override
    public CanvasProject insert(UUID tenantId, UUID userId, String name, String canvasDataJson) {
        Instant now = Instant.now();
        CanvasProject project = new CanvasProject(UUID.randomUUID(), userId, name, canvasDataJson, 1, now, now);
        projects.put(project.id(), project);
        projectTenants.put(project.id(), tenantId);
        return project;
    }

    @Override
    public Optional<CanvasProject> findByIdAndTenant(UUID projectId, UUID tenantId) {
        CanvasProject project = projects.get(projectId);
        if (project == null || !tenantId.equals(projectTenants.get(projectId))) {
            return Optional.empty();
        }
        return Optional.of(project);
    }

    @Override
    public List<CanvasProject> findByTenant(UUID tenantId, int limit) {
        return projects.values().stream()
                .filter(project -> tenantId.equals(projectTenants.get(project.id())))
                .sorted(Comparator.comparing(CanvasProject::updatedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<CanvasProject> update(UUID projectId, UUID tenantId, String name, String canvasDataJson) {
        CanvasProject project = projects.get(projectId);
        if (project == null || !tenantId.equals(projectTenants.get(projectId))) {
            return Optional.empty();
        }
        CanvasProject updated = new CanvasProject(project.id(), project.userId(),
                name == null ? project.name() : name,
                canvasDataJson == null ? project.canvasDataJson() : canvasDataJson,
                project.version() + 1, project.createdAt(), Instant.now());
        projects.put(projectId, updated);
        return Optional.of(updated);
    }

    @Override
    public boolean delete(UUID projectId, UUID tenantId) {
        if (!tenantId.equals(projectTenants.get(projectId))) {
            return false;
        }
        projects.remove(projectId);
        projectTenants.remove(projectId);
        return true;
    }

    @Override
    public void insertGeneratedIfAbsent(UUID tenantId, UUID userId, UUID jobId, String objectKey, String mimeType) {
        boolean exists = assets.values().stream()
                .anyMatch(asset -> asset.jobId() != null && asset.jobId().equals(jobId)
                        && asset.objectKey().equals(objectKey));
        if (exists) {
            return;
        }
        UserAsset asset = new UserAsset(UUID.randomUUID(), userId, null, jobId, AssetSource.GENERATED, objectKey,
                mimeType, Instant.now());
        assets.put(asset.id(), asset);
        assetTenants.put(asset.id(), tenantId);
    }

    @Override
    public List<UserAsset> findByTenantAssets(UUID tenantId, int limit) {
        return assets.values().stream()
                .filter(asset -> tenantId.equals(assetTenants.get(asset.id())))
                .sorted(Comparator.comparing(UserAsset::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public boolean deleteAsset(UUID assetId, UUID tenantId) {
        if (!tenantId.equals(assetTenants.get(assetId))) {
            return false;
        }
        assets.remove(assetId);
        assetTenants.remove(assetId);
        return true;
    }

    /** 素材行数，测试用来断言「幂等同步没有产生重复行」。 */
    public int assetCount() {
        return assets.size();
    }
}
