package com.example.subscription.service.runtime;

import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.exception.RedisUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRuntimeConfigStore implements RuntimeConfigStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SubscriptionProperties props;

    public RedisRuntimeConfigStore(StringRedisTemplate redis,
                                   ObjectMapper objectMapper,
                                   SubscriptionProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public void put(RuntimeConfig config) {
        String json = serialize(config);
        try {
            redis.opsForValue().set(configKey(config.subscriptionId()), json);
            redis.opsForSet().add(props.getRedis().getRuntimeSetKey(), config.subscriptionId());
        } catch (DataAccessException e) {
            throw new RedisUnavailableException("failed to write runtime config", e);
        }
    }

    @Override
    public void remove(String subscriptionId) {
        try {
            redis.delete(configKey(subscriptionId));
            redis.opsForSet().remove(props.getRedis().getRuntimeSetKey(), subscriptionId);
        } catch (DataAccessException e) {
            throw new RedisUnavailableException("failed to remove runtime config", e);
        }
    }

    private String configKey(String subscriptionId) {
        return props.getRedis().getConfigKeyPrefix() + subscriptionId;
    }

    private String serialize(RuntimeConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            // Serialization of a well-formed record cannot realistically fail; treat as internal.
            throw new IllegalStateException("failed to serialize runtime config", e);
        }
    }
}
