package com.example.subscription.api;

import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.domain.SubscriptionTarget;
import com.example.subscription.exception.InvalidStatusException;
import com.example.subscription.exception.QuotaExceededException;
import com.example.subscription.exception.RedisUnavailableException;
import com.example.subscription.exception.SubscriptionNotFoundException;
import com.example.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SubscriptionController.class, InternalSubscriptionController.class})
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SubscriptionService service;

    private Subscription sample(SubscriptionStatus status) {
        Subscription s = new Subscription("sub-1", "risk-service", "prod",
                EngineType.EVENT_WITH_REMOVE, "portfolioId==P1", List.of("dealId"),
                List.of(new SubscriptionTarget("FxSpotForwardTrade", true)), status);
        return s;
    }

    @Test
    void createReturns201WithBody() throws Exception {
        when(service.create(anyString(), any())).thenReturn(sample(SubscriptionStatus.ACTIVE));
        when(service.topicName(any())).thenReturn("subscription.risk-service.prod");

        mvc.perform(post("/api/v1/subscribers/risk-service/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicPostfix":"prod",
                                 "targets":[{"objectClass":"FxSpotForwardTrade","includeSubclasses":true}],
                                 "fields":["dealId"],
                                 "filter":"portfolioId==P1","engine":"EVENT_WITH_REMOVE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value("sub-1"))
                .andExpect(jsonPath("$.topic").value("subscription.risk-service.prod"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getUnknownReturns404WithErrorBody() throws Exception {
        when(service.get(anyString(), anyString()))
                .thenThrow(new SubscriptionNotFoundException("sub-x"));

        mvc.perform(get("/api/v1/subscribers/risk-service/subscriptions/sub-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));
    }

    @Test
    void createReturns503WhenRedisUnavailable() throws Exception {
        when(service.create(anyString(), any()))
                .thenThrow(new RedisUnavailableException("down", null));

        mvc.perform(post("/api/v1/subscribers/risk-service/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicPostfix":"prod","fields":["dealId"],"engine":"OBJECT_STREAM"}"""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REDIS_UNAVAILABLE"));
    }

    @Test
    void createReturns429WhenQuotaExceeded() throws Exception {
        when(service.create(anyString(), any()))
                .thenThrow(new QuotaExceededException("maxSubscriptionsPerSubscriber", 100));

        mvc.perform(post("/api/v1/subscribers/risk-service/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topicPostfix":"prod","fields":["dealId"],"engine":"OBJECT_STREAM"}"""))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.details.limit").value(100));
    }

    @Test
    void pauseReturns409OnInvalidStatus() throws Exception {
        when(service.pause(anyString(), anyString()))
                .thenThrow(new InvalidStatusException("pause", SubscriptionStatus.FAILED));

        mvc.perform(post("/api/v1/subscribers/risk-service/subscriptions/sub-1/pause"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void deleteReturnsDeletedSubscription() throws Exception {
        when(service.delete(anyString(), anyString())).thenReturn(sample(SubscriptionStatus.DELETED));
        when(service.topicName(any())).thenReturn("subscription.risk-service.prod");

        mvc.perform(delete("/api/v1/subscribers/risk-service/subscriptions/sub-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETED"));
    }

    @Test
    void initializationReturns202() throws Exception {
        mvc.perform(post("/api/v1/subscribers/risk-service/subscriptions/sub-1/initialization"))
                .andExpect(status().isAccepted());
    }

    @Test
    void internalFailReturnsFailedSubscription() throws Exception {
        when(service.fail(anyString(), anyString(), any())).thenReturn(sample(SubscriptionStatus.FAILED));
        when(service.topicName(any())).thenReturn("subscription.risk-service.prod");

        mvc.perform(post("/internal/subscriptions/sub-1/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"FILTER_SCHEMA_MISMATCH","message":"field missing"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }
}
