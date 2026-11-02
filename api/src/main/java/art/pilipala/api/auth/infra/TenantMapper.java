package art.pilipala.api.auth.infra;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户、成员与租户侧 RBAC 的读写。
 *
 * <p>权限码的身份是 {@code (code, scope)}：{@code job.read} 在平台和租户各有一行，
 * 所以这里的每条权限查询都把 scope 写死在 SQL 里，调用方不会把两个范围混起来。
 * 每次调用都读库，不在登录态里缓存权限。
 */
@Mapper
public interface TenantMapper {

    /**
     * 该用户的当前个人租户：{@code active} 成员里、{@code type = personal} 且租户
     * {@code active} 的最早一条（{@code created_at}，再比成员 id）。
     *
     * <p>与管理端用户列表（{@code AdminUserMapper}）的取法一致。
     */
    @Select("SELECT t.id FROM tenant t JOIN tenant_membership m ON m.tenant_id = t.id "
            + "WHERE m.user_id = #{userId} AND m.status = 'active' "
            + "AND t.type = 'personal' AND t.status = 'active' "
            + "ORDER BY t.created_at, m.id LIMIT 1")
    UUID findPersonalTenantId(@Param("userId") UUID userId);

    /** 建个人租户，返回新租户 id。 */
    @Select("INSERT INTO tenant (type, name, status) VALUES ('personal', #{name}, 'active') RETURNING id")
    UUID insertPersonalTenant(@Param("name") String name);

    /**
     * 租户是否存在且 {@code active}。
     *
     * <p>当前租户规则只认 active 租户，但会话里的那个键可能指向后来被停用的租户，
     * 所以提交任务前还要再查一次。
     */
    @Select("SELECT count(*) FROM tenant WHERE id = #{tenantId} AND status = 'active'")
    int countActiveTenant(@Param("tenantId") UUID tenantId);

    @Insert("INSERT INTO tenant_membership (tenant_id, user_id, status) "
            + "VALUES (#{tenantId}, #{userId}, 'active')")
    int insertMembership(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 授予租户角色（幂等）。租户范围的分配必须带 {@code tenant_id}；
     * 平台角色不带 {@code tenant_id}，走 {@code PlatformRoleMapper}。
     */
    @Insert("INSERT INTO role_assignment (user_id, role_id, tenant_id) "
            + "SELECT #{userId}, r.id, #{tenantId} FROM role r WHERE r.code = #{roleCode} "
            + "ON CONFLICT DO NOTHING")
    int insertTenantRole(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            @Param("roleCode") String roleCode);

    /** 平台范围的权限码（去重，按码排序）。租户角色的 {@code job.read} 不会出现在这里。 */
    @Select("SELECT DISTINCT rp.permission_code FROM role_assignment ra "
            + "JOIN role r ON r.id = ra.role_id AND r.scope = 'platform' "
            + "JOIN role_permission rp ON rp.role_id = r.id AND rp.permission_scope = 'platform' "
            + "WHERE ra.user_id = #{userId} AND ra.tenant_id IS NULL "
            + "ORDER BY rp.permission_code")
    List<String> findPlatformPermissions(@Param("userId") UUID userId);

    /**
     * 该租户上的租户范围权限码（去重，按码排序）。
     * 成员或租户停用后为空；平台角色的 {@code job.read} 不会出现在这里。
     */
    @Select("SELECT DISTINCT rp.permission_code FROM tenant_membership m "
            + "JOIN tenant t ON t.id = m.tenant_id "
            + "JOIN role_assignment ra ON ra.user_id = m.user_id AND ra.tenant_id = m.tenant_id "
            + "JOIN role r ON r.id = ra.role_id AND r.scope = 'tenant' "
            + "JOIN role_permission rp ON rp.role_id = r.id AND rp.permission_scope = 'tenant' "
            + "WHERE m.user_id = #{userId} AND m.tenant_id = #{tenantId} "
            + "AND m.status = 'active' AND t.status = 'active' "
            + "ORDER BY rp.permission_code")
    List<String> findTenantPermissions(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    /** 该租户上的角色码（去重，按码排序）。 */
    @Select("SELECT DISTINCT r.code FROM tenant_membership m "
            + "JOIN tenant t ON t.id = m.tenant_id "
            + "JOIN role_assignment ra ON ra.user_id = m.user_id AND ra.tenant_id = m.tenant_id "
            + "JOIN role r ON r.id = ra.role_id AND r.scope = 'tenant' "
            + "WHERE m.user_id = #{userId} AND m.tenant_id = #{tenantId} "
            + "AND m.status = 'active' AND t.status = 'active' "
            + "ORDER BY r.code")
    List<String> findTenantRoleCodes(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
