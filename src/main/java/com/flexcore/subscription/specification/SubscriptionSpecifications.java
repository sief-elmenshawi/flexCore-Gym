package com.flexcore.subscription.specification;

import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.enums.SubscriptionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public final class SubscriptionSpecifications {

    private SubscriptionSpecifications() {
    }

    public static Specification<Subscription> hasUser(Long userId) {
        return (root, query, cb) -> userId == null
                ? cb.conjunction()
                : cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Subscription> hasStatus(SubscriptionStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<Subscription> endsBefore(LocalDateTime dateTime) {
        return (root, query, cb) -> dateTime == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("endDate"), dateTime);
    }

    /**
     * Restricts the result to non-soft-deleted subscriptions. Every read path that exposes
     * historical lists (e.g. search, admin listing, expiring-soon) must compose this so
     * soft-deleted rows never surface.
     */
    public static Specification<Subscription> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }
}
