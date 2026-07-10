package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;

/**
 * Raised when a Redis write on the mandatory write-path fails. Because Redis is a required part
 * of every configuration-changing operation, this rolls back the surrounding transaction and
 * results in HTTP 503 (requirements 1, 16).
 */
public class RedisUnavailableException extends ApiException {

    public RedisUnavailableException(String message, Throwable cause) {
        super(ErrorCode.REDIS_UNAVAILABLE, "Redis unavailable: " + message);
        initCause(cause);
    }
}
