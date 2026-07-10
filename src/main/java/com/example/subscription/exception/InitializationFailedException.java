package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;

/**
 * Raised when the call to the Initialization Service fails.
 */
public class InitializationFailedException extends ApiException {

    public InitializationFailedException(String message, Throwable cause) {
        super(ErrorCode.INITIALIZATION_FAILED, "Initialization failed: " + message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
