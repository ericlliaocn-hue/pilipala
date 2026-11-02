package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 会员订阅。归属租户（{@code tenantId}），{@code userId} 只表示下单的操作者。
 */
public record UserSubscription(
        long id,
        UUID userId,
        UUID tenantId,
        long planId,
        String planCode,
        long priceId,
        String cycle,
        String variantCode,
        String status,
        Instant startedAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        int seats,
        long creditsPerMonth,
        Instant lastGrantAt,
        UUID sourceOrderId,
        Instant createdAt,
        Instant updatedAt) {
}
