package art.pilipala.api.project.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.project.domain.UserAsset;

@Mapper
public interface UserAssetMapper {

    String COLS = "id, user_id, project_id, job_id, source, object_key, mime_type, created_at";

    @Insert("INSERT INTO user_asset (tenant_id, user_id, job_id, source, object_key, mime_type) "
            + "VALUES (#{tenantId}, #{userId}, #{jobId}, 'generated', #{objectKey}, #{mimeType}) "
            + "ON CONFLICT (job_id, object_key) DO NOTHING")
    void insertGeneratedIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("jobId") UUID jobId,
            @Param("objectKey") String objectKey,
            @Param("mimeType") String mimeType);

    @Select("SELECT " + COLS + " FROM user_asset WHERE tenant_id = #{tenantId} ORDER BY created_at DESC LIMIT #{limit}")
    List<UserAsset> findByTenantAssets(@Param("tenantId") UUID tenantId, @Param("limit") int limit);

    @Delete("DELETE FROM user_asset WHERE id = #{assetId} AND tenant_id = #{tenantId}")
    int deleteAsset(@Param("assetId") UUID assetId, @Param("tenantId") UUID tenantId);
}
