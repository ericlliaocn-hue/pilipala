package art.pilipala.api.admin.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import art.pilipala.api.admin.infra.AdminIdempotencyMapper;
import art.pilipala.api.admin.infra.AuditLogMapper;

/**
 * 内存版运维幂等表与审计表：Admin 加额 / 退款用例不需要 Postgres。
 *
 * <p>{@code claim} 的语义与 {@code ON CONFLICT DO NOTHING} 一致：同键第二次返回 0，
 * 已有记录里的响应能原样读回来。
 */
public class InMemoryAdminIdempotencyStore implements AdminIdempotencyMapper, AuditLogMapper {

    /** 一条审计留痕。 */
    public record Audit(UUID actorUserId, String permissionCode, UUID tenantId, String targetType, String targetId,
            String idempotencyKey, String summary) {
    }

    private final Map<String, String> responses = new LinkedHashMap<>();
    private final List<Audit> audits = new ArrayList<>();

    // ----- AdminIdempotencyMapper -----

    @Override
    public int claim(UUID actorUserId, String action, String idempotencyKey) {
        return responses.putIfAbsent(key(actorUserId, action, idempotencyKey), "{}") == null ? 1 : 0;
    }

    @Override
    public String findResponse(UUID actorUserId, String action, String idempotencyKey) {
        return responses.get(key(actorUserId, action, idempotencyKey));
    }

    @Override
    public int complete(UUID actorUserId, String action, String idempotencyKey, String responseJson) {
        return responses.put(key(actorUserId, action, idempotencyKey), responseJson) == null ? 0 : 1;
    }

    // ----- AuditLogMapper -----

    @Override
    public int insert(UUID actorUserId, String permissionCode, UUID tenantId, String targetType, String targetId,
            String idempotencyKey, String summary) {
        audits.add(new Audit(actorUserId, permissionCode, tenantId, targetType, targetId, idempotencyKey, summary));
        return 1;
    }

    // ----- 测试辅助 -----

    public List<Audit> audits() {
        return List.copyOf(audits);
    }

    public int recordCount() {
        return responses.size();
    }

    private static String key(UUID actorUserId, String action, String idempotencyKey) {
        return actorUserId + "/" + action + "/" + idempotencyKey;
    }
}
