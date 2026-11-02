package art.pilipala.api.admin.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 运维写操作的审计留痕（{@code audit_log}，只追加）。
 *
 * <p>{@code actor_user_id} 是点下按钮的登录者，不是被操作的用户；
 * {@code tenant_id} 允许为空（平台级操作没有租户）。
 */
@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO audit_log (actor_user_id, permission_code, tenant_id, target_type, target_id, "
            + "idempotency_key, summary) VALUES (#{actorUserId}, #{permissionCode}, #{tenantId,jdbcType=OTHER}, "
            + "#{targetType}, #{targetId}, #{idempotencyKey}, #{summary})")
    int insert(@Param("actorUserId") UUID actorUserId,
            @Param("permissionCode") String permissionCode,
            @Param("tenantId") UUID tenantId,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("summary") String summary);
}
