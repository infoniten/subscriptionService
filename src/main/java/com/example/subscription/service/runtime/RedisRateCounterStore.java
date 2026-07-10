package com.example.subscription.service.runtime;

import com.example.subscription.exception.RedisUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisRateCounterStore implements RateCounterStore {

    private final StringRedisTemplate redis;

    public RedisRateCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long incrementAndGet(String key, Duration ttl) {
        try {
            Long value = redis.opsForValue().increment(key);
            if (value != null && value == 1L) {
                // First increment in this window: set the expiry so the counter resets.
                redis.expire(key, ttl);
            }
            return value == null ? 0L : value;
        } catch (DataAccessException e) {
            throw new RedisUnavailableException("failed to update rate counter", e);
        }
    }
}
