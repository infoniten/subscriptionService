package com.example.subscription.service.client;

/**
 * Client for the Initialization Service. The Subscription Service only triggers a snapshot
 * initialization job; it never performs any data unloading itself (requirement 9, 21).
 */
public interface InitializationClient {

    void startInitialization(String subscriberName, String subscriptionId, String topic);
}
