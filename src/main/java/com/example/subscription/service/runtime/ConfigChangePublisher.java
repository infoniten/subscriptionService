package com.example.subscription.service.runtime;

/**
 * Publishes the CONFIG_CHANGED signal on the Redis Pub/Sub channel. The message is purely a
 * signal; the Engine re-reads Redis on its own after receiving it.
 */
public interface ConfigChangePublisher {

    void publishConfigChanged(String subscriptionId);
}
