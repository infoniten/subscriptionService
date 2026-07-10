package com.example.subscription.service;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.exception.QuotaExceededException;
import com.example.subscription.repository.SubscriptionRepository;
import com.example.subscription.service.runtime.RateCounterStore;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Enforces the configurable rate limits / quotas (requirement 18):
 * subscriptions per subscriber, topics per subscriber, subscription creations per hour,
 * initializations per hour, max fields and max filter length.
 */
@Service
public class QuotaService {

    private static final Duration HOUR = Duration.ofHours(1);

    private final SubscriptionRepository repository;
    private final RateCounterStore rateCounters;
    private final SubscriptionProperties props;

    public QuotaService(SubscriptionRepository repository,
                        RateCounterStore rateCounters,
                        SubscriptionProperties props) {
        this.repository = repository;
        this.rateCounters = rateCounters;
        this.props = props;
    }

    /**
     * Checks all create-time quotas. The per-hour creation counter is incremented last so that a
     * request rejected by a static limit does not consume the hourly budget.
     */
    public void checkAndReserveForCreate(String subscriberName, CreateSubscriptionRequest request) {
        SubscriptionProperties.RateLimits limits = props.getRateLimits();

        List<String> fields = request.fields() == null ? List.of() : request.fields();
        if (fields.size() > limits.getMaxFields()) {
            throw new QuotaExceededException("maxFields", limits.getMaxFields());
        }
        int filterLength = request.filter() == null ? 0 : request.filter().length();
        if (filterLength > limits.getMaxFilterLength()) {
            throw new QuotaExceededException("maxFilterLength", limits.getMaxFilterLength());
        }

        long active = repository.countBySubscriberNameAndStatusNot(
                subscriberName, SubscriptionStatus.DELETED);
        if (active >= limits.getMaxSubscriptionsPerSubscriber()) {
            throw new QuotaExceededException("maxSubscriptionsPerSubscriber",
                    limits.getMaxSubscriptionsPerSubscriber());
        }

        boolean newTopic = !repository.existsBySubscriberNameAndTopicPostfixAndStatusNot(
                subscriberName, request.topicPostfix(), SubscriptionStatus.DELETED);
        if (newTopic) {
            long topics = repository.countDistinctTopicsBySubscriber(
                    subscriberName, SubscriptionStatus.DELETED);
            if (topics >= limits.getMaxTopicsPerSubscriber()) {
                throw new QuotaExceededException("maxTopicsPerSubscriber",
                        limits.getMaxTopicsPerSubscriber());
            }
        }

        long perHour = rateCounters.incrementAndGet(
                hourKey(subscriberName, "sub-create"), HOUR);
        if (perHour > limits.getMaxSubscriptionCreationsPerHour()) {
            throw new QuotaExceededException("maxSubscriptionCreationsPerHour",
                    limits.getMaxSubscriptionCreationsPerHour());
        }
    }

    /**
     * Checks and reserves the per-hour initialization quota.
     */
    public void checkAndReserveForInitialization(String subscriberName) {
        long perHour = rateCounters.incrementAndGet(hourKey(subscriberName, "init"), HOUR);
        if (perHour > props.getRateLimits().getMaxInitializationsPerHour()) {
            throw new QuotaExceededException("maxInitializationsPerHour",
                    props.getRateLimits().getMaxInitializationsPerHour());
        }
    }

    private String hourKey(String subscriberName, String action) {
        long hourBucket = Instant.now().getEpochSecond() / 3600;
        return "quota:" + subscriberName + ":" + action + ":" + hourBucket;
    }
}
