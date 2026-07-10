package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;

import java.util.Map;

/**
 * Raised by the validation component (or basic format validation) for a malformed subscription.
 */
public class ValidationException extends ApiException {

    public ValidationException(ErrorCode code, String message) {
        super(code, message);
    }

    public ValidationException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
