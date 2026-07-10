package com.example.subscription.service;

import com.example.subscription.config.SubscriptionProperties;
import org.springframework.stereotype.Component;

/**
 * Computes the Kafka topic name owned by a (subscriberName, topicPostfix) pair.
 * The Subscription Service only computes this name; the topic itself is created by the
 * Engine Service on first publish (requirement 6).
 */
@Component
public class TopicNameResolver {

    private final SubscriptionProperties props;

    public TopicNameResolver(SubscriptionProperties props) {
        this.props = props;
    }

    public String resolve(String subscriberName, String topicPostfix) {
        return props.getTopic().getPrefix() + "." + subscriberName + "." + topicPostfix;
    }
}
