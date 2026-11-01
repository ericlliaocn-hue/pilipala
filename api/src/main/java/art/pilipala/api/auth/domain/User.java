package art.pilipala.api.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户聚合。列与 docs/engineering/data-model/README.md 的 users（WP17）一致。
 */
public record User(
        UUID id,
        String username,
        String phone,
        String email,
        String passwordHash,
        String locale,
        Instant createdAt,
        Instant updatedAt) {
}
