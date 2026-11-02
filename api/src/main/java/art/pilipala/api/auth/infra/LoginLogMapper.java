package art.pilipala.api.auth.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 登录日志：只追加，不更新不删除。
 *
 * <p>不存验证码、密码、令牌；认不出用户时 {@code user_id} 为空。
 */
@Mapper
public interface LoginLogMapper {

    @Insert("INSERT INTO login_log (user_id, tenant_id, result, ip) VALUES ("
            + "#{userId,jdbcType=OTHER}, #{tenantId,jdbcType=OTHER}, #{result}, #{ip,jdbcType=VARCHAR})")
    int insert(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            @Param("result") String result,
            @Param("ip") String ip);
}
