package art.pilipala.api.admin.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import art.pilipala.api.admin.domain.AdminUser;

@Mapper
public interface AdminUserMapper {

    String COLS = "u.id AS user_id, u.username, u.locale, u.created_at, u.updated_at, "
            + "COALESCE(w.available, 0) AS available, COALESCE(w.frozen, 0) AS frozen";

    /** 钱包属于租户：取该用户最早加入的启用租户的钱包。 */
    String FROM = " FROM users u LEFT JOIN wallet w ON w.tenant_id = ("
            + "SELECT tm.tenant_id FROM tenant_membership tm "
            + "WHERE tm.user_id = u.id AND tm.status = 'active' ORDER BY tm.created_at, tm.id LIMIT 1) ";

    @Select("SELECT " + COLS + FROM + "ORDER BY u.created_at DESC LIMIT #{limit}")
    List<AdminUser> findUsersAll(@Param("limit") int limit);

    @Select("SELECT " + COLS + FROM + "WHERE u.username ILIKE #{pattern} ORDER BY u.created_at DESC LIMIT #{limit}")
    List<AdminUser> findUsersByQuery(@Param("pattern") String pattern, @Param("limit") int limit);
}
