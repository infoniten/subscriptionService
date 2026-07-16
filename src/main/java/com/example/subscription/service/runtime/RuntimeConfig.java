package com.example.subscription.service.runtime;

import com.example.subscription.domain.Subscription;

import java.util.List;

/**
 * Runtime configuration payload stored in Redis under {@code sub:{subscriptionId}} and read
 * directly by the Engine Services. Only ACTIVE and PAUSED subscriptions are ever present.
 */
public record RuntimeConfig(
        String subscriptionId,
        String subscriberName,
        String topic,
        String engine,
        List<Target> targets,
        List<String> fields,
        String filter,
        String status
) {

    /** A class target: objectClass + whether subclasses are included (polymorphic vs exact). */
    public record Target(String objectClass, boolean includeSubclasses) {
    }

    public static RuntimeConfig from(Subscription s, String topic) {
        return new RuntimeConfig(
                s.getId(),
                s.getSubscriberName(),
                topic,
                s.getEngine().name(),
                s.getTargets().stream()
                        .map(t -> new Target(t.getObjectClass(), t.isIncludeSubclasses()))
                        .toList(),
                List.copyOf(s.getFields()),
                s.getFilter(),
                s.getStatus().name()
        );
    }
}
