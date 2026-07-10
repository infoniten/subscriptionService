package com.example.subscription.service.runtime;

import com.example.subscription.domain.Subscription;

import java.util.List;

/**
 * Runtime configuration payload stored in Redis under {@code sub:{subscriptionId}} and read
 * directly by the Engine Service. Only ACTIVE and PAUSED subscriptions are ever present.
 */
public record RuntimeConfig(
        String subscriptionId,
        String subscriberName,
        String topic,
        String engine,
        List<String> fields,
        String filter,
        String status
) {

    public static RuntimeConfig from(Subscription s, String topic) {
        return new RuntimeConfig(
                s.getId(),
                s.getSubscriberName(),
                topic,
                s.getEngine().name(),
                List.copyOf(s.getFields()),
                s.getFilter(),
                s.getStatus().name()
        );
    }
}
