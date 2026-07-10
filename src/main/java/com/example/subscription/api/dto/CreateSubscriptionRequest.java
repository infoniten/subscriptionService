package com.example.subscription.api.dto;

import java.util.List;

/**
 * Create subscription request body. Note: {@code subscriberName} is NEVER accepted from the body —
 * it is always taken from the URI (requirement 4). {@code engine} is kept as a raw String so that
 * an unknown value produces the specific UNSUPPORTED_ENGINE error rather than a generic parse error.
 */
public record CreateSubscriptionRequest(
        String topicPostfix,
        List<String> fields,
        String filter,
        String engine
) {
}
