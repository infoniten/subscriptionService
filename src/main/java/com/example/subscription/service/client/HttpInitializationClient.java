package com.example.subscription.service.client;

import com.example.subscription.config.SubscriptionProperties;
import com.example.subscription.exception.InitializationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class HttpInitializationClient implements InitializationClient {

    private static final Logger log = LoggerFactory.getLogger(HttpInitializationClient.class);

    private final RestClient restClient;

    public HttpInitializationClient(RestClient.Builder builder, SubscriptionProperties props) {
        this.restClient = builder.baseUrl(props.getInitialization().getBaseUrl()).build();
    }

    @Override
    public void startInitialization(String subscriberName, String subscriptionId, String topic) {
        try {
            restClient.post()
                    .uri("/internal/initialization")
                    .body(Map.of(
                            "subscriberName", subscriberName,
                            "subscriptionId", subscriptionId,
                            "topic", topic))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Initialization job requested for subscription {}", subscriptionId);
        } catch (RestClientException e) {
            throw new InitializationFailedException(
                    "could not reach Initialization Service for subscription " + subscriptionId, e);
        }
    }
}
