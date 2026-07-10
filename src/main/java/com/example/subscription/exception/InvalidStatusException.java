package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.SubscriptionStatus;

import java.util.Map;

/**
 * Raised when a lifecycle transition is not allowed from the current status.
 */
public class InvalidStatusException extends ApiException {

    public InvalidStatusException(String operation, SubscriptionStatus current) {
        super(ErrorCode.INVALID_STATUS,
                "Operation '" + operation + "' is not allowed in status " + current,
                Map.of("operation", operation, "currentStatus", current.name()));
    }
}
