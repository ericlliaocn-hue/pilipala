package art.pilipala.api.admin.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 运维写操作的幂等表（{@code admin_idempotency}）。
 *
 * <p>锁键是 {@code (actor_user_id, action, idempotency_key)}：同一操作者、同一动作、同一键
 * 只执行一次，与目标用户或订单无关。{@code response_json} 存第一次的响应，重放时原样返回。
 */
@Mapper
public interface AdminIdempotencyMapper {

    /**
     * 占键：没有记录时插入一条占位响应并返回 1；
     * 已有同键记录（含并发中另一个事务刚提交的）时返回 0，不报错。
     */
    @Insert("INSERT INTO admin_idempotency (actor_user_id, action, idempotency_key, response_json) "
            + "VALUES (#{actorUserId}, #{action}, #{idempotencyKey}, '{}'::jsonb) "
            + "ON CONFLICT (actor_user_id, action, idempotency_key) DO NOTHING")
    int claim(@Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("idempotencyKey") String idempotencyKey);

    /** 已存的首次响应（JSON 文本）；没有记录时为空。 */
    @Select("SELECT response_json::text FROM admin_idempotency "
            + "WHERE actor_user_id = #{actorUserId} AND action = #{action} AND idempotency_key = #{idempotencyKey}")
    String findResponse(@Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("idempotencyKey") String idempotencyKey);

    /** 把占位响应换成本次真实执行后的响应。 */
    @Update("UPDATE admin_idempotency SET response_json = #{responseJson}::jsonb "
            + "WHERE actor_user_id = #{actorUserId} AND action = #{action} AND idempotency_key = #{idempotencyKey}")
    int complete(@Param("actorUserId") UUID actorUserId,
            @Param("action") String action,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("responseJson") String responseJson);
}
