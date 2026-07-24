package com.example.subscription.service.runtime;

import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.repository.SubscriptionRepository;
import com.example.subscription.service.TopicNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rebuilds the Redis runtime projection ({@code sub:{id}} + {@code subs:runtime}) from PostgreSQL on
 * startup. PostgreSQL is the source of truth; Redis is only a cache the engines read. If Redis is ever
 * emptied (a Redis restart without persistence, eviction, {@code FLUSHALL}, or pointing at a fresh
 * instance) nothing else rebuilds it — so a restart of this service self-heals the cache.
 *
 * <p>Runs after the context is ready. Non-fatal: if Redis is unavailable the pod stays up (it can
 * still serve reads); the projection is simply retried on the next restart. Idempotent — writing the
 * same runtime config again is a no-op for consumers.
 */
@Component
public class RuntimeConfigRehydrator {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigRehydrator.class);

    private final SubscriptionRepository repository;
    private final RuntimeConfigStore runtimeStore;
    private final TopicNameResolver topicNameResolver;

    public RuntimeConfigRehydrator(SubscriptionRepository repository,
                                   RuntimeConfigStore runtimeStore,
                                   TopicNameResolver topicNameResolver) {
        this.repository = repository;
        this.runtimeStore = runtimeStore;
        this.topicNameResolver = topicNameResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rehydrate() {
        List<Subscription> runtime = repository.findByStatusIn(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED));
        log.info("Rehydrating {} runtime subscriptions (ACTIVE/PAUSED) from PostgreSQL into Redis...",
                runtime.size());
        int written = 0;
        try {
            for (Subscription s : runtime) {
                String topic = topicNameResolver.resolve(s.getSubscriberName(), s.getTopicPostfix());
                runtimeStore.put(RuntimeConfig.from(s, topic));
                written++;
            }
            log.info("Redis rehydration complete: {} runtime subscriptions written", written);
        } catch (Exception e) {
            // Redis unavailable (or a transient error) — do not crash the pod; the cache will be
            // rebuilt on the next restart. Reads still work; config-changing writes would 503 anyway.
            log.error("Redis rehydration aborted after {}/{} subscriptions (Redis unavailable?): {}",
                    written, runtime.size(), e.toString());
        }
    }
}
