package art.pilipala.api.project.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.project.domain.CanvasProject;

@Repository
public class MybatisCanvasProjectRepository implements CanvasProjectRepository {

    private final CanvasProjectMapper mapper;

    public MybatisCanvasProjectRepository(CanvasProjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public CanvasProject insert(UUID tenantId, UUID userId, String name, String canvasDataJson) {
        return mapper.insert(tenantId, userId, name, canvasDataJson);
    }

    @Override
    public Optional<CanvasProject> findByIdAndTenant(UUID projectId, UUID tenantId) {
        return Optional.ofNullable(mapper.findByIdAndTenant(projectId, tenantId));
    }

    @Override
    public List<CanvasProject> findByTenant(UUID tenantId, int limit) {
        return mapper.findByTenant(tenantId, limit);
    }

    @Override
    public Optional<CanvasProject> update(UUID projectId, UUID tenantId, String name, String canvasDataJson) {
        return Optional.ofNullable(mapper.update(projectId, tenantId, name, canvasDataJson));
    }

    @Override
    public boolean delete(UUID projectId, UUID tenantId) {
        return mapper.delete(projectId, tenantId) > 0;
    }
}
