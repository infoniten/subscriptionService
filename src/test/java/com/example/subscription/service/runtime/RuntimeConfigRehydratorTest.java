package com.example.subscription.service.runtime;

import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.domain.SubscriptionTarget;
import com.example.subscription.exception.RedisUnavailableException;
import com.example.subscription.repository.SubscriptionRepository;
import com.example.subscription.service.TopicNameResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeConfigRehydratorTest {

    private final SubscriptionRepository repository = mock(SubscriptionRepository.class);
    private final RuntimeConfigStore runtimeStore = mock(RuntimeConfigStore.class);
    private final TopicNameResolver topicNameResolver = mock(TopicNameResolver.class);
    private final RuntimeConfigRehydrator rehydrator =
            new RuntimeConfigRehydrator(repository, runtimeStore, topicNameResolver);

    private static Subscription sub(String id, SubscriptionStatus status) {
        return new Subscription(id, "risk", "prod", EngineType.EVENT_WITH_REMOVE, "portfolioId==1",
                List.of("Trade.id"), List.of(new SubscriptionTarget("FxSpotForwardTrade", true)), status);
    }

    @Test
    void writesEveryRuntimeSubscriptionToRedis() {
        when(topicNameResolver.resolve(any(), any())).thenReturn("subscription.risk.prod");
        when(repository.findByStatusIn(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAUSED)))
                .thenReturn(List.of(sub("s1", SubscriptionStatus.ACTIVE), sub("s2", SubscriptionStatus.PAUSED)));

        rehydrator.rehydrate();

        verify(runtimeStore, times(2)).put(any(RuntimeConfig.class));
    }

    @Test
    void redisFailureDoesNotCrashStartup() {
        when(topicNameResolver.resolve(any(), any())).thenReturn("subscription.risk.prod");
        when(repository.findByStatusIn(anyCollection()))
                .thenReturn(List.of(sub("s1", SubscriptionStatus.ACTIVE)));
        doThrow(new RedisUnavailableException("down", null)).when(runtimeStore).put(any());

        // Must swallow the error (log only), so ApplicationReadyEvent processing never fails the pod.
        assertThatCode(rehydrator::rehydrate).doesNotThrowAnyException();
    }
}
