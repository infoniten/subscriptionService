package com.example.subscription.api.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical error codes returned in the unified error response ({@code code} field).
 */
public enum ErrorCode {
    INVALID_FILTER(HttpStatus.BAD_REQUEST),
    INVALID_FIELDS(HttpStatus.BAD_REQUEST),
    INVALID_TARGETS(HttpStatus.BAD_REQUEST),
    INVALID_SUBSCRIBER_NAME(HttpStatus.BAD_REQUEST),
    INVALID_TOPIC_POSTFIX(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_ENGINE(HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_STATUS(HttpStatus.CONFLICT),
    QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    REDIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    METAMODEL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INITIALIZATION_FAILED(HttpStatus.BAD_GATEWAY),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
