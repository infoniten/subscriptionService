package com.example.subscription.api.dto;

import com.example.subscription.domain.Subscription;

import java.time.Instant;
import java.util.List;

/**
 * Subscription representation returned by the public API.
 */
public record SubscriptionResponse(
        String subscriptionId,
        String subscriberName,
        String topicPostfix,
        String topic,
        List<String> fields,
        String filter,
        String engine,
        String status,
        String failureReason,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt
) {

    public static SubscriptionResponse from(Subscription s, String topic) {
        return new SubscriptionResponse(
                s.getId(),
                s.getSubscriberName(),
                s.getTopicPostfix(),
                topic,
                List.copyOf(s.getFields()),
                s.getFilter(),
                s.getEngine().name(),
                s.getStatus().name(),
                s.getFailureReason(),
                s.getFailureMessage(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
