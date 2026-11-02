package art.pilipala.api.project.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.project.domain.CanvasProject;

/**
 * canvas_project 表的存取契约。SQL 只在 {@link MybatisCanvasProjectRepository}。
 */
public interface CanvasProjectRepository {

    /**
     * 新建一个云端项目。
     *
     * @param canvasDataJson 画布快照（JSON 文本）
     */
    CanvasProject insert(UUID tenantId, UUID userId, String name, String canvasDataJson);

    /** 只查当前租户的项目：他人租户的项目与不存在的项目一样返回空。 */
    Optional<CanvasProject> findByIdAndTenant(UUID projectId, UUID tenantId);

    /** 当前租户的项目列表，按最后保存时间倒序。 */
    List<CanvasProject> findByTenant(UUID tenantId, int limit);

    /**
     * 更新当前租户的项目：version + 1，updated_at 刷新。
     *
     * @param name         新名称；null 表示不改
     * @param canvasDataJson 新快照；null 表示不改
     * @return 更新后的项目；不存在或不属于本人时返回空
     */
    Optional<CanvasProject> update(UUID projectId, UUID tenantId, String name, String canvasDataJson);

    /**
     * 删除当前租户的项目。
     *
     * @return 真的删掉了才为 true
     */
    boolean delete(UUID projectId, UUID tenantId);
}
