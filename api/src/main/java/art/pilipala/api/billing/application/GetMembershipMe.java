package art.pilipala.api.billing.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.billing.domain.UserSubscription;
import art.pilipala.api.billing.infra.UserSubscriptionMapper;
import org.springframework.stereotype.Service;

@Service
public class GetMembershipMe {

    private final UserSubscriptionMapper subscriptions;

    public GetMembershipMe(UserSubscriptionMapper subscriptions) {
        this.subscriptions = subscriptions;
    }

    public Optional<MembershipMeView> get(UUID tenantId) {
        UserSubscription sub = subscriptions.findActiveByTenant(tenantId);
        if (sub == null) {
            return Optional.empty();
        }
        return Optional.of(new MembershipMeView(
                sub.planCode(),
                sub.cycle(),
                sub.variantCode(),
                sub.status(),
                sub.creditsPerMonth(),
                sub.currentPeriodStart(),
                sub.currentPeriodEnd(),
                sub.lastGrantAt()));
    }

    public record MembershipMeView(
            String plan_code,
            String cycle,
            String variant_code,
            String status,
            long credits_per_month,
            Instant current_period_start,
            Instant current_period_end,
            Instant last_grant_at) {
    }
}
