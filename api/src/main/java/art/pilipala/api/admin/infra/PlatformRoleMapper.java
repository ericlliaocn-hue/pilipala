package art.pilipala.api.admin.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 平台角色分配查询。
 *
 * <p>{@code users.role} 已被 RBAC 取代：运维身份 = 该用户持有平台范围的角色分配。
 * 权限码级别的判定由 admin 鉴权服务按请求读取，这里只回答「是不是平台员工」。
 */
@Mapper
public interface PlatformRoleMapper {

    /** 该用户是否有平台范围的角色分配。 */
    @Select("SELECT count(*) FROM role_assignment ra JOIN role r ON r.id = ra.role_id "
            + "WHERE ra.user_id = #{userId} AND ra.tenant_id IS NULL AND r.scope = 'platform'")
    int countPlatformRoles(@Param("userId") UUID userId);

    /** 系统里是否已经有平台所有者。 */
    @Select("SELECT count(*) FROM role_assignment ra JOIN role r ON r.id = ra.role_id "
            + "WHERE r.code = 'platform_owner'")
    int countPlatformOwners();

    /** 授予平台所有者（幂等）。 */
    @Insert("INSERT INTO role_assignment (user_id, role_id) "
            + "SELECT #{userId}, r.id FROM role r WHERE r.code = 'platform_owner' ON CONFLICT DO NOTHING")
    int insertPlatformOwner(@Param("userId") UUID userId);
}
