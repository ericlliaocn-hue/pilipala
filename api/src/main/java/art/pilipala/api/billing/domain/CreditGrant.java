package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 积分批次（带到期），与 {@code wallet.available} 同步维护。
 *
 * <p>批次归属租户（{@code tenantId}），{@code userId} 只表示操作者。
 */
public record CreditGrant(
        long id,
        UUID userId,
        UUID walletId,
        UUID tenantId,
        String sourceType,
        String sourceId,
        String grantType,
        long creditsTotal,
        long creditsRemaining,
        Instant expiresAt,
        Long productId,
        Instant createdAt) {
}
