package art.pilipala.api.auth.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import art.pilipala.api.auth.infra.UserMapper;
import art.pilipala.api.auth.infra.UserRepository;

/**
 * 单测用的用户表：按 id 返回固定状态的行。
 *
 * <p>{@code SubmitJob} 在冻结前要读 {@code users.status}，所以任务用例需要一个
 * 可控的 {@link UserRepository}，而不是真库。
 */
public final class TestUsers {

    private TestUsers() {
    }

    /** 给定 id 的用户都是 {@code active}。 */
    public static UserRepository active(UUID... userIds) {
        Map<UUID, User> rows = new LinkedHashMap<>();
        Arrays.stream(userIds).forEach(id -> rows.put(id, user(id, User.STATUS_ACTIVE)));
        return of(rows);
    }

    public static UserRepository of(Map<UUID, User> rows) {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.findById(any())).thenAnswer(call -> rows.get(call.<UUID>getArgument(0)));
        return new UserRepository(mapper);
    }

    public static User user(UUID id, String status) {
        Instant now = Instant.now();
        return new User(id, "user-" + id, null, null, "hash", "zh", status, now, now);
    }
}
