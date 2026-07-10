package com.example.subscription.service;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.exception.InvalidStatusException;
import com.example.subscription.exception.SubscriptionNotFoundException;
import com.example.subscription.repository.SubscriptionRepository;
import com.example.subscription.repository.SubscriptionSpecifications;
import com.example.subscription.service.client.InitializationClient;
import com.example.subscription.service.runtime.ConfigChangePublisher;
import com.example.subscription.service.runtime.RuntimeConfig;
import com.example.subscription.service.runtime.RuntimeConfigStore;
import com.example.subscription.service.validation.SubscriptionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the subscription lifecycle across PostgreSQL (source of truth), Redis runtime store
 * and the Redis Pub/Sub change signal.
 *
 * <p>Every configuration-changing operation runs in a single transaction that also performs the
 * Redis writes. Because Redis is a mandatory part of the write-path, a Redis failure throws
 * {@link com.example.subscription.exception.RedisUnavailableException}, which rolls back the
 * PostgreSQL change and yields HTTP 503 — so the two stores never diverge on a failed write
 * (requirements 9, 16).
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository repository;
    private final RuntimeConfigStore runtimeStore;
    private final ConfigChangePublisher publisher;
    private final InitializationClient initializationClient;
    private final SubscriptionValidator validator;
    private final SubscriptionInputParser inputParser;
    private final QuotaService quotaService;
    private final TopicNameResolver topicNameResolver;

    public SubscriptionService(SubscriptionRepository repository,
                               RuntimeConfigStore runtimeStore,
                               ConfigChangePublisher publisher,
                               InitializationClient initializationClient,
                               SubscriptionValidator validator,
                               SubscriptionInputParser inputParser,
                               QuotaService quotaService,
                               TopicNameResolver topicNameResolver) {
        this.repository = repository;
        this.runtimeStore = runtimeStore;
        this.publisher = publisher;
        this.initializationClient = initializationClient;
        this.validator = validator;
        this.inputParser = inputParser;
        this.quotaService = quotaService;
        this.topicNameResolver = topicNameResolver;
    }

    /**
     * POST /subscriptions flow: validate -> quota -> persist (PostgreSQL) -> runtime (Redis)
     * -> publish CONFIG_CHANGED. A Redis failure rolls the whole transaction back (503).
     */
    @Transactional
    public Subscription create(String subscriberName, CreateSubscriptionRequest request) {
        inputParser.validateSubscriberName(subscriberName);
        EngineType engine = inputParser.parseAndValidate(request);

        // Semantic validation (RSQL/fields) — currently a stub that accepts everything.
        validator.validate(subscriberName, engine, request.filter(), request.fields());

        quotaService.checkAndReserveForCreate(subscriberName, request);

        Subscription subscription = new Subscription(
                newId(),
                subscriberName,
                request.topicPostfix(),
                engine,
                request.filter(),
                request.fields(),
                SubscriptionStatus.ACTIVE);
        subscription = repository.save(subscription);

        writeRuntimeAndSignal(subscription);
        log.info("Created subscription {} for subscriber {}", subscription.getId(), subscriberName);
        return subscription;
    }

    @Transactional(readOnly = true)
    public Subscription get(String subscriberName, String subscriptionId) {
        return findOwned(subscriberName, subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> list(String subscriberName,
                                   SubscriptionStatus status,
                                   String topicPostfix,
                                   EngineType engine) {
        return repository.findAll(
                SubscriptionSpecifications.forListing(subscriberName, status, topicPostfix, engine));
    }

    /**
     * ACTIVE -> PAUSED. Idempotent: pausing an already-paused subscription is a no-op.
     */
    @Transactional
    public Subscription pause(String subscriberName, String subscriptionId) {
        Subscription subscription = findOwned(subscriberName, subscriptionId);
        if (subscription.getStatus() == SubscriptionStatus.PAUSED) {
            return subscription;
        }
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new InvalidStatusException("pause", subscription.getStatus());
        }
        subscription.markPaused();
        writeRuntimeAndSignal(subscription);
        log.info("Paused subscription {}", subscriptionId);
        return subscription;
    }

    /**
     * PAUSED -> ACTIVE. Idempotent when already ACTIVE. When runInitialization is requested, the
     * Initialization Service is invoked after re-activation.
     */
    @Transactional
    public Subscription resume(String subscriberName, String subscriptionId, boolean runInitialization) {
        Subscription subscription = findOwned(subscriberName, subscriptionId);
        boolean alreadyActive = subscription.getStatus() == SubscriptionStatus.ACTIVE;
        if (!alreadyActive && subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw new InvalidStatusException("resume", subscription.getStatus());
        }
        if (!alreadyActive) {
            subscription.markActive();
            writeRuntimeAndSignal(subscription);
            log.info("Resumed subscription {}", subscriptionId);
        }
        if (runInitialization) {
            triggerInitialization(subscription);
        }
        return subscription;
    }

    /**
     * ACTIVE/PAUSED/FAILED -> DELETED. Removes runtime config. The Kafka topic is NOT deleted.
     * Idempotent when already DELETED.
     */
    @Transactional
    public Subscription delete(String subscriberName, String subscriptionId) {
        Subscription subscription = findOwned(subscriberName, subscriptionId);
        if (subscription.getStatus() == SubscriptionStatus.DELETED) {
            return subscription;
        }
        subscription.markDeleted();
        repository.save(subscription);
        runtimeStore.remove(subscription.getId());
        publisher.publishConfigChanged(subscription.getId());
        log.info("Deleted subscription {}", subscriptionId);
        return subscription;
    }

    /**
     * Standalone initialization trigger (POST .../initialization).
     */
    @Transactional(readOnly = true)
    public void initialize(String subscriberName, String subscriptionId) {
        Subscription subscription = findOwned(subscriberName, subscriptionId);
        if (subscription.getStatus() == SubscriptionStatus.DELETED) {
            throw new InvalidStatusException("initialization", subscription.getStatus());
        }
        triggerInitialization(subscription);
    }

    /**
     * Internal fail transition invoked by the Engine Service when a filter no longer compiles
     * after a model change. Any -> FAILED, removed from runtime, CONFIG_CHANGED published.
     */
    @Transactional
    public Subscription fail(String subscriptionId, String reason, String message) {
        Subscription subscription = repository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        if (subscription.getStatus() == SubscriptionStatus.DELETED) {
            throw new InvalidStatusException("fail", subscription.getStatus());
        }
        subscription.markFailed(reason, message);
        repository.save(subscription);
        runtimeStore.remove(subscription.getId());
        publisher.publishConfigChanged(subscription.getId());
        log.warn("Subscription {} moved to FAILED: {} ({})", subscriptionId, reason, message);
        return subscription;
    }

    public String topicName(Subscription subscription) {
        return topicNameResolver.resolve(subscription.getSubscriberName(), subscription.getTopicPostfix());
    }

    private void triggerInitialization(Subscription subscription) {
        quotaService.checkAndReserveForInitialization(subscription.getSubscriberName());
        initializationClient.startInitialization(
                subscription.getSubscriberName(), subscription.getId(), topicName(subscription));
    }

    /** Writes the runtime config for an ACTIVE/PAUSED subscription and publishes the change signal. */
    private void writeRuntimeAndSignal(Subscription subscription) {
        runtimeStore.put(RuntimeConfig.from(subscription, topicName(subscription)));
        publisher.publishConfigChanged(subscription.getId());
    }

    private Subscription findOwned(String subscriberName, String subscriptionId) {
        Subscription subscription = repository.findById(subscriptionId)
                .orElseThrow(() -> new SubscriptionNotFoundException(subscriptionId));
        // Accessing another subscriber's subscription must look identical to "not found".
        if (!subscription.getSubscriberName().equals(subscriberName)) {
            throw new SubscriptionNotFoundException(subscriptionId);
        }
        return subscription;
    }

    private String newId() {
        return "sub-" + UUID.randomUUID();
    }
}
