package com.example.subscription.exception;

import com.example.subscription.api.error.ErrorCode;

import java.util.Map;

/**
 * Raised when a configurable rate limit / quota is exceeded.
 */
public class QuotaExceededException extends ApiException {

    public QuotaExceededException(String quota, long limit) {
        super(ErrorCode.QUOTA_EXCEEDED,
                "Quota exceeded: " + quota,
                Map.of("quota", quota, "limit", limit));
    }
}
