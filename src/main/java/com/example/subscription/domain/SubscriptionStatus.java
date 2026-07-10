package com.example.subscription.domain;

/**
 * Subscription lifecycle status.
 *
 * <p>Only {@link #ACTIVE} and {@link #PAUSED} subscriptions are part of the Redis runtime
 * configuration; {@link #FAILED} and {@link #DELETED} are removed from the runtime set.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    FAILED,
    DELETED;

    /** Whether a subscription in this status participates in the Redis runtime configuration. */
    public boolean isRuntime() {
        return this == ACTIVE || this == PAUSED;
    }
}
