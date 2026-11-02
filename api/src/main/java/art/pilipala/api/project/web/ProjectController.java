package art.pilipala.api.project.web;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import art.pilipala.api.auth.application.CurrentTenant;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.AuthSessionService;
import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.project.application.DeleteProjectView;
import art.pilipala.api.project.application.ProjectDetailView;
import art.pilipala.api.project.application.ProjectService;
import art.pilipala.api.project.application.ProjectSummaryView;
import art.pilipala.api.project.infra.ProjectException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端云端画布项目接口。
 *
 * <p>鉴权闸门由 WP3 的 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}，
 * 这里从会话取 user_id 与当前租户（禁止信任请求体里的 user id）。
 *
 * <p>失败一律走统一信封（HTTP 200 + ok=false + error_code + message）。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final AuthSessionService sessions;
    private final CurrentTenant currentTenant;
    private final ProjectService projects;

    public ProjectController(AuthSessionService sessions, CurrentTenant currentTenant, ProjectService projects) {
        this.sessions = sessions;
        this.currentTenant = currentTenant;
        this.projects = projects;
    }

    @PostMapping
    public ApiResponse<ProjectDetailView> create(@RequestBody CreateProjectRequest request) {
        UUID userId = currentUserId();
        return ApiResponse.ok(projects.create(userId, tenantOf(userId), request.name(), request.canvasData()));
    }

    @GetMapping
    public ApiResponse<List<ProjectSummaryView>> list() {
        return ApiResponse.ok(projects.list(tenantOf(currentUserId())));
    }

    @GetMapping("/{projectId}")
    public ApiResponse<ProjectDetailView> get(@PathVariable String projectId) {
        return ApiResponse.ok(projects.get(tenantOf(currentUserId()), projectIdOf(projectId)));
    }

    @PutMapping("/{projectId}")
    public ApiResponse<ProjectDetailView> update(@PathVariable String projectId,
                                                  @RequestBody UpdateProjectRequest request) {
        return ApiResponse.ok(projects.update(
                tenantOf(currentUserId()), projectIdOf(projectId), request.name(), request.canvasData()));
    }

    @DeleteMapping("/{projectId}")
    public ApiResponse<DeleteProjectView> delete(@PathVariable String projectId) {
        return ApiResponse.ok(projects.delete(tenantOf(currentUserId()), projectIdOf(projectId)));
    }

    /** 当前会话的当前租户；解析不出来是 FORBIDDEN。 */
    private UUID tenantOf(UUID userId) {
        return currentTenant.require(userId);
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }

    /** 路径上的 id 非法时按「不存在」处理，不泄露格式细节。 */
    private UUID projectIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ProjectException.projectNotFound();
        }
    }

    /**
     * 创建请求体。
     *
     * @param canvasData 画布快照；缺省存 {@code {}}
     */
    public record CreateProjectRequest(String name, JsonNode canvasData) {
    }

    /**
     * 更新请求体：两个字段至少给一个。
     */
    public record UpdateProjectRequest(String name, JsonNode canvasData) {
    }
}
