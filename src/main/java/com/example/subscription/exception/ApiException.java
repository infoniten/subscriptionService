package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;

import java.util.Map;

/**
 * Base exception carrying a canonical {@link ErrorCode}. Mapped to the unified error response
 * by the global exception handler.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
