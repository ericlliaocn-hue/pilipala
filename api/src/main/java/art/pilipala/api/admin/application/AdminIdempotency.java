package art.pilipala.api.admin.application;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.api.admin.infra.AdminIdempotencyMapper;
import art.pilipala.api.job.infra.JobException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 运维写操作的幂等执行：同一操作者、同一动作、同一键只执行一次。
 *
 * <p>占键顺序是「先查、再占、后干活」：命中已有记录时既不第二次入账，也不写第二条审计。
 * 占键用 {@code ON CONFLICT DO NOTHING}，抢输的一方重读第一条响应原样返回。
 *
 * <p>{@code response_json} 用全局的 snake_case {@link ObjectMapper} 存取，
 * 所以重放响应与首次响应字节级一致。
 */
@Component
public class AdminIdempotency {

    /** 幂等键长度上限。 */
    public static final int MAX_KEY_LENGTH = 64;
    /** 操作原因长度上限。 */
    public static final int MAX_REASON_LENGTH = 200;

    private final AdminIdempotencyMapper records;
    private final ObjectMapper mapper;

    public AdminIdempotency(AdminIdempotencyMapper records, ObjectMapper mapper) {
        this.records = records;
        this.mapper = mapper;
    }

    /** 幂等键：trim 后 1..64，否则 {@code VALIDATION_ERROR}。 */
    public static String requireKey(String raw) {
        return requireText(raw, MAX_KEY_LENGTH);
    }

    /** 操作原因：trim 后 1..200，否则 {@code VALIDATION_ERROR}。 */
    public static String requireReason(String raw) {
        return requireText(raw, MAX_REASON_LENGTH);
    }

    /**
     * 在这个键下执行一次写操作。
     *
     * @param action 动作名，与权限码一致（{@code wallet.topup} / {@code recharge.refund}）
     * @param work 真正的写操作；只有抢到键的那一次会执行
     * @param afterWork 首次执行成功后的一次性收尾（写审计）；重放时不调用
     */
    public <T> T execute(UUID actorUserId, String action, String key, Class<T> responseType,
            Supplier<T> work, Consumer<T> afterWork) {
        String stored = records.findResponse(actorUserId, action, key);
        if (stored != null) {
            return read(stored, responseType);
        }
        if (records.claim(actorUserId, action, key) == 0) {
            String raced = records.findResponse(actorUserId, action, key);
            if (raced == null) {
                throw JobException.idempotencyConflict();
            }
            return read(raced, responseType);
        }
        T result = work.get();
        afterWork.accept(result);
        records.complete(actorUserId, action, key, write(result));
        return result;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("admin_idempotency 响应解析失败", e);
        }
    }

    private String write(Object response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("admin_idempotency 响应序列化失败", e);
        }
    }

    private static String requireText(String raw, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw JobException.validation();
        }
        return value;
    }
}
