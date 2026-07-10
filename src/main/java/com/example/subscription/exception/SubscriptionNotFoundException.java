package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;

/**
 * Raised when a subscription does not exist OR belongs to a different subscriber.
 * Both cases return 404 so that a subscriber cannot probe for other subscribers' ids.
 */
public class SubscriptionNotFoundException extends ApiException {

    public SubscriptionNotFoundException(String subscriptionId) {
        super(ErrorCode.SUBSCRIPTION_NOT_FOUND, "Subscription not found: " + subscriptionId);
    }
}
