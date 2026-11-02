package art.pilipala.api.project.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.project.domain.CanvasProject;

@Mapper
public interface CanvasProjectMapper {

    String COLS = "id, user_id, name, canvas_data AS canvas_data_json, version, created_at, updated_at";

    @Select("INSERT INTO canvas_project (tenant_id, user_id, name, canvas_data) VALUES "
            + "(#{tenantId}, #{userId}, #{name}, #{canvasDataJson,jdbcType=VARCHAR}::jsonb) RETURNING " + COLS)
    CanvasProject insert(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId, @Param("name") String name, @Param("canvasDataJson") String canvasDataJson);

    @Select("SELECT " + COLS + " FROM canvas_project WHERE id = #{projectId} AND tenant_id = #{tenantId}")
    CanvasProject findByIdAndTenant(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);

    @Select("SELECT " + COLS + " FROM canvas_project WHERE tenant_id = #{tenantId} ORDER BY updated_at DESC LIMIT #{limit}")
    List<CanvasProject> findByTenant(@Param("tenantId") UUID tenantId, @Param("limit") int limit);

    @Select("UPDATE canvas_project SET name = COALESCE(#{name}, name), "
            + "canvas_data = COALESCE(#{canvasDataJson,jdbcType=VARCHAR}::jsonb, canvas_data), "
            + "version = version + 1, updated_at = now() WHERE id = #{projectId} AND tenant_id = #{tenantId} RETURNING " + COLS)
    CanvasProject update(
            @Param("projectId") UUID projectId,
            @Param("tenantId") UUID tenantId,
            @Param("name") String name,
            @Param("canvasDataJson") String canvasDataJson);

    @Delete("DELETE FROM canvas_project WHERE id = #{projectId} AND tenant_id = #{tenantId}")
    int delete(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);
}
