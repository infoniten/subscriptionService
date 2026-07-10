package com.example.subscription.service.runtime;

import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.exception.RedisUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisConfigChangePublisher implements ConfigChangePublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SubscriptionProperties props;

    public RedisConfigChangePublisher(StringRedisTemplate redis,
                                      ObjectMapper objectMapper,
                                      SubscriptionProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public void publishConfigChanged(String subscriptionId) {
        String message = serialize(subscriptionId);
        try {
            redis.convertAndSend(props.getRedis().getChannel(), message);
        } catch (DataAccessException e) {
            // Publishing is part of the Redis write-path; if Redis is down the whole operation fails.
            throw new RedisUnavailableException("failed to publish config change", e);
        }
    }

    private String serialize(String subscriptionId) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("type", "CONFIG_CHANGED", "subscriptionId", subscriptionId));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize config change message", e);
        }
    }
}
