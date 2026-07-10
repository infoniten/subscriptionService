package com.example.subscription.service;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.exception.QuotaExceededException;
import com.example.subscription.repository.SubscriptionRepository;
import com.example.subscription.service.runtime.RateCounterStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuotaServiceTest {

    private SubscriptionRepository repository;
    private RateCounterStore rateCounters;
    private SubscriptionProperties props;
    private QuotaService quotaService;

    @BeforeEach
    void setUp() {
        repository = mock(SubscriptionRepository.class);
        rateCounters = mock(RateCounterStore.class);
        props = new SubscriptionProperties();
        quotaService = new QuotaService(repository, rateCounters, props);

        // permissive defaults
        when(repository.countBySubscriberNameAndStatusNot(anyString(), any())).thenReturn(0L);
        when(repository.countDistinctTopicsBySubscriber(anyString(), any())).thenReturn(0L);
        when(repository.existsBySubscriberNameAndTopicPostfixAndStatusNot(anyString(), anyString(), any()))
                .thenReturn(false);
        when(rateCounters.incrementAndGet(anyString(), any(Duration.class))).thenReturn(1L);
    }

    private CreateSubscriptionRequest request(int fields, int filterLen) {
        List<String> f = IntStream.range(0, fields).mapToObj(i -> "f" + i).collect(Collectors.toList());
        String filter = "x".repeat(filterLen);
        return new CreateSubscriptionRequest("prod", f, filter, "OBJECT_STREAM");
    }

    @Test
    void passesWithinAllLimits() {
        assertThatCode(() -> quotaService.checkAndReserveForCreate("s", request(3, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTooManyFields() {
        props.getRateLimits().setMaxFields(2);
        assertThatThrownBy(() -> quotaService.checkAndReserveForCreate("s", request(3, 10)))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void rejectsFilterTooLong() {
        props.getRateLimits().setMaxFilterLength(5);
        assertThatThrownBy(() -> quotaService.checkAndReserveForCreate("s", request(1, 10)))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void rejectsWhenSubscriptionCountReached() {
        props.getRateLimits().setMaxSubscriptionsPerSubscriber(5);
        when(repository.countBySubscriberNameAndStatusNot("s", SubscriptionStatus.DELETED)).thenReturn(5L);
        assertThatThrownBy(() -> quotaService.checkAndReserveForCreate("s", request(1, 10)))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void rejectsNewTopicWhenTopicCountReached() {
        props.getRateLimits().setMaxTopicsPerSubscriber(3);
        when(repository.existsBySubscriberNameAndTopicPostfixAndStatusNot("s", "prod", SubscriptionStatus.DELETED))
                .thenReturn(false);
        when(repository.countDistinctTopicsBySubscriber("s", SubscriptionStatus.DELETED)).thenReturn(3L);
        assertThatThrownBy(() -> quotaService.checkAndReserveForCreate("s", request(1, 10)))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void allowsExistingTopicEvenWhenTopicCountReached() {
        props.getRateLimits().setMaxTopicsPerSubscriber(3);
        when(repository.existsBySubscriberNameAndTopicPostfixAndStatusNot("s", "prod", SubscriptionStatus.DELETED))
                .thenReturn(true);
        when(repository.countDistinctTopicsBySubscriber("s", SubscriptionStatus.DELETED)).thenReturn(3L);
        assertThatCode(() -> quotaService.checkAndReserveForCreate("s", request(1, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenHourlyCreationLimitExceeded() {
        props.getRateLimits().setMaxSubscriptionCreationsPerHour(10);
        when(rateCounters.incrementAndGet(anyString(), any(Duration.class))).thenReturn(11L);
        assertThatThrownBy(() -> quotaService.checkAndReserveForCreate("s", request(1, 10)))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void rejectsWhenHourlyInitializationLimitExceeded() {
        props.getRateLimits().setMaxInitializationsPerHour(2);
        when(rateCounters.incrementAndGet(anyString(), any(Duration.class))).thenReturn(3L);
        assertThatThrownBy(() -> quotaService.checkAndReserveForInitialization("s"))
                .isInstanceOf(QuotaExceededException.class);
    }
}
