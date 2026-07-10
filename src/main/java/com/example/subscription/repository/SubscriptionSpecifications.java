package com.example.subscription.repository;

import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds dynamic query predicates for the "list subscriptions" endpoint.
 */
public final class SubscriptionSpecifications {

    private SubscriptionSpecifications() {
    }

    public static Specification<Subscription> forListing(String subscriberName,
                                                         SubscriptionStatus status,
                                                         String topicPostfix,
                                                         EngineType engine) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("subscriberName"), subscriberName));

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                // Without an explicit status filter, DELETED subscriptions are hidden by default.
                predicates.add(cb.notEqual(root.get("status"), SubscriptionStatus.DELETED));
            }
            if (topicPostfix != null) {
                predicates.add(cb.equal(root.get("topicPostfix"), topicPostfix));
            }
            if (engine != null) {
                predicates.add(cb.equal(root.get("engine"), engine));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
