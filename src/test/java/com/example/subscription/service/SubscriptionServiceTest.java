package com.example.subscription.service;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.api.dto.CreateSubscriptionRequest.TargetRequest;
import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.domain.SubscriptionTarget;
import com.example.subscription.exception.InvalidStatusException;
import com.example.subscription.exception.RedisUnavailableException;
import com.example.subscription.exception.SubscriptionNotFoundException;
import com.example.subscription.exception.ValidationException;
import com.example.subscription.repository.SubscriptionRepository;
import com.example.subscription.service.client.InitializationClient;
import com.example.subscription.service.runtime.ConfigChangePublisher;
import com.example.subscription.service.runtime.RuntimeConfig;
import com.example.subscription.service.runtime.RuntimeConfigStore;
import com.example.subscription.service.validation.StubSubscriptionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private SubscriptionRepository repository;
    private RuntimeConfigStore runtimeStore;
    private ConfigChangePublisher publisher;
    private InitializationClient initializationClient;
    private QuotaService quotaService;
    private SubscriptionService service;

    private final SubscriptionProperties props = new SubscriptionProperties();

    @BeforeEach
    void setUp() {
        repository = mock(SubscriptionRepository.class);
        runtimeStore = mock(RuntimeConfigStore.class);
        publisher = mock(ConfigChangePublisher.class);
        initializationClient = mock(InitializationClient.class);
        quotaService = mock(QuotaService.class);
        service = new SubscriptionService(
                repository,
                runtimeStore,
                publisher,
                initializationClient,
                new StubSubscriptionValidator(),
                new SubscriptionInputParser(),
                quotaService,
                new TopicNameResolver(props));

        // repository.save returns its argument
        when(repository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateSubscriptionRequest validRequest() {
        return new CreateSubscriptionRequest("prod",
                List.of(new TargetRequest("FxSpotForwardTrade", true)),
                List.of("dealId", "portfolioId"),
                "portfolioId==P1", "EVENT_WITH_REMOVE");
    }

    @Test
    void createPersistsWritesRuntimeAndPublishes() {
        Subscription created = service.create("risk-service", validRequest());

        assertThat(created.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(created.getId()).startsWith("sub-");
        assertThat(service.topicName(created)).isEqualTo("subscription.risk-service.prod");

        verify(repository).save(any(Subscription.class));
        ArgumentCaptor<RuntimeConfig> rc = ArgumentCaptor.forClass(RuntimeConfig.class);
        verify(runtimeStore).put(rc.capture());
        assertThat(rc.getValue().topic()).isEqualTo("subscription.risk-service.prod");
        assertThat(rc.getValue().status()).isEqualTo("ACTIVE");
        verify(publisher).publishConfigChanged(created.getId());
    }

    @Test
    void createRejectsUnsupportedEngine() {
        CreateSubscriptionRequest bad = new CreateSubscriptionRequest(
                "prod", List.of(new TargetRequest("Trade", true)), List.of("dealId"), null, "NOPE");
        assertThatThrownBy(() -> service.create("risk-service", bad))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createPropagatesRedisFailureSoTransactionRollsBack() {
        doThrow(new RedisUnavailableException("down", null))
                .when(runtimeStore).put(any());

        assertThatThrownBy(() -> service.create("risk-service", validRequest()))
                .isInstanceOf(RedisUnavailableException.class);
        // Postgres write happened in-memory but the surrounding @Transactional would roll it back;
        // crucially, no CONFIG_CHANGED is signalled for a failed write.
        verify(publisher, never()).publishConfigChanged(anyString());
    }

    @Test
    void getReturns404ForOtherSubscribersSubscription() {
        Subscription other = active("sub-1", "other-service");
        when(repository.findById("sub-1")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.get("risk-service", "sub-1"))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void pauseMovesActiveToPausedAndSignals() {
        Subscription sub = active("sub-1", "risk-service");
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        Subscription result = service.pause("risk-service", "sub-1");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        verify(runtimeStore).put(any());
        verify(publisher).publishConfigChanged("sub-1");
    }

    @Test
    void pauseIsIdempotentWhenAlreadyPaused() {
        Subscription sub = active("sub-1", "risk-service");
        sub.markPaused();
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        service.pause("risk-service", "sub-1");

        verify(runtimeStore, never()).put(any());
        verify(publisher, never()).publishConfigChanged(anyString());
    }

    @Test
    void pauseRejectedForFailedSubscription() {
        Subscription sub = active("sub-1", "risk-service");
        sub.markFailed("X", "y");
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.pause("risk-service", "sub-1"))
                .isInstanceOf(InvalidStatusException.class);
    }

    @Test
    void resumeWithInitializationTriggersInitializationService() {
        Subscription sub = active("sub-1", "risk-service");
        sub.markPaused();
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        service.resume("risk-service", "sub-1", true);

        verify(initializationClient).startInitialization(
                "risk-service", "sub-1", "subscription.risk-service.prod");
    }

    @Test
    void resumeWithoutInitializationDoesNotCallInitService() {
        Subscription sub = active("sub-1", "risk-service");
        sub.markPaused();
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        service.resume("risk-service", "sub-1", false);

        verify(initializationClient, never()).startInitialization(anyString(), anyString(), anyString());
    }

    @Test
    void deleteRemovesRuntimeAndKeepsTopic() {
        Subscription sub = active("sub-1", "risk-service");
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        Subscription result = service.delete("risk-service", "sub-1");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.DELETED);
        verify(runtimeStore).remove("sub-1");
        verify(publisher).publishConfigChanged("sub-1");
    }

    @Test
    void failMovesToFailedAndRemovesFromRuntime() {
        Subscription sub = active("sub-1", "risk-service");
        when(repository.findById("sub-1")).thenReturn(Optional.of(sub));

        Subscription result = service.fail("sub-1", "FILTER_SCHEMA_MISMATCH", "field missing");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("FILTER_SCHEMA_MISMATCH");
        verify(runtimeStore).remove("sub-1");
        verify(publisher).publishConfigChanged("sub-1");
    }

    private Subscription active(String id, String subscriber) {
        return new Subscription(id, subscriber, "prod", EngineType.EVENT_WITH_REMOVE,
                "portfolioId==P1", List.of("dealId"),
                List.of(new SubscriptionTarget("FxSpotForwardTrade", true)), SubscriptionStatus.ACTIVE);
    }
}
