package art.pilipala.api.project.application;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import art.pilipala.api.project.domain.CanvasProject;
import art.pilipala.api.project.infra.CanvasProjectRepository;
import art.pilipala.api.project.infra.ProjectException;
import org.springframework.stereotype.Service;

/**
 * 云端画布项目用例：create / list / get / update / delete。
 *
 * <p>只按当前租户读写（严禁信任请求体里的 user id），插入时保留操作者 {@code user_id}。
 * 项目不存在与「属于别的租户」返回同一个码，不泄露存在性。
 */
@Service
public class ProjectService {

    /** 项目名长度上限。 */
    public static final int MAX_NAME_LENGTH = 128;
    /** 画布快照序列化后的字符数上限：防止一个人把 DB 当大文件仓库。 */
    public static final int MAX_CANVAS_DATA_CHARS = 2_000_000;
    /** 列表最多返回多少条。 */
    public static final int MAX_LIST_SIZE = 100;

    private final CanvasProjectRepository projects;
    private final ObjectMapper mapper;

    public ProjectService(CanvasProjectRepository projects, ObjectMapper mapper) {
        this.projects = projects;
        this.mapper = mapper;
    }

    public ProjectDetailView create(UUID userId, UUID tenantId, String name, JsonNode canvasData) {
        CanvasProject project = projects.insert(tenantId, userId, requireName(name), requireCanvasData(canvasData));
        return toDetail(project);
    }

    public List<ProjectSummaryView> list(UUID tenantId) {
        return projects.findByTenant(tenantId, MAX_LIST_SIZE).stream().map(this::toSummary).toList();
    }

    public ProjectDetailView get(UUID tenantId, UUID projectId) {
        return toDetail(findOwned(tenantId, projectId));
    }

    public ProjectDetailView update(UUID tenantId, UUID projectId, String name, JsonNode canvasData) {
        if (name == null && canvasData == null) {
            throw ProjectException.validation();
        }
        String cleanName = name == null ? null : requireName(name);
        String json = canvasData == null ? null : requireCanvasData(canvasData);
        CanvasProject project = projects.update(projectId, tenantId, cleanName, json)
                .orElseThrow(ProjectException::projectNotFound);
        return toDetail(project);
    }

    public DeleteProjectView delete(UUID tenantId, UUID projectId) {
        if (!projects.delete(projectId, tenantId)) {
            throw ProjectException.projectNotFound();
        }
        return new DeleteProjectView(projectId.toString(), true);
    }

    private CanvasProject findOwned(UUID tenantId, UUID projectId) {
        return projects.findByIdAndTenant(projectId, tenantId).orElseThrow(ProjectException::projectNotFound);
    }

    private ProjectSummaryView toSummary(CanvasProject project) {
        return new ProjectSummaryView(project.id().toString(), project.name(), project.version(),
                project.createdAt(), project.updatedAt());
    }

    private ProjectDetailView toDetail(CanvasProject project) {
        return new ProjectDetailView(project.id().toString(), project.name(), readCanvasData(project.canvasDataJson()),
                project.version(), project.createdAt(), project.updatedAt());
    }

    private JsonNode readCanvasData(String json) {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    /** 名称：trim 后 1..128 字符。 */
    private String requireName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > MAX_NAME_LENGTH) {
            throw ProjectException.validation();
        }
        return clean;
    }

    /** 画布快照：必须是 JSON Object，且序列化后不超过上限。 */
    private String requireCanvasData(JsonNode canvasData) {
        if (canvasData == null || canvasData.isNull()) {
            return "{}";
        }
        if (!canvasData.isObject()) {
            throw ProjectException.validation();
        }
        ObjectNode copy = mapper.createObjectNode();
        copy.setAll((ObjectNode) canvasData);
        String json;
        try {
            json = mapper.writeValueAsString(copy);
        } catch (JsonProcessingException e) {
            throw ProjectException.validation();
        }
        if (json.length() > MAX_CANVAS_DATA_CHARS) {
            throw ProjectException.validation();
        }
        return json;
    }
}
