package art.pilipala.api.auth.infra;

import java.util.UUID;

import org.springframework.stereotype.Repository;

/**
 * 登录日志的写入口。
 *
 * <p>只有登录会写；注册与重置密码不写。没有 {@code userId} 时表示这次登录认不出账号。
 */
@Repository
public class LoginLog {

    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final LoginLogMapper mapper;

    public LoginLog(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param userId   解析出的用户；认不出时为空
     * @param tenantId 该用户的当前个人租户；没有个人租户时为空
     * @param success  是否登录成功
     * @param ip       客户端 ip；取不到时写空
     */
    public void record(UUID userId, UUID tenantId, boolean success, String ip) {
        String trimmed = ip == null || ip.isBlank() ? null : ip.trim();
        mapper.insert(userId, tenantId, success ? SUCCESS : FAILURE, trimmed);
    }
}
