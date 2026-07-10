package com.example.subscription.service.runtime;

import java.time.Duration;

/**
 * Sliding hourly counters (Redis) backing the per-hour rate limits.
 */
public interface RateCounterStore {

    /**
     * Atomically increments the counter at {@code key}, setting its TTL on first creation,
     * and returns the new value.
     */
    long incrementAndGet(String key, Duration ttl);
}
