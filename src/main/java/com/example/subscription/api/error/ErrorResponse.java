package com.example.subscription.api.error;

import java.util.Map;

/**
 * Unified error response body (requirement 19):
 * <pre>{ "code": "...", "message": "...", "details": {} }</pre>
 */
public record ErrorResponse(String code, String message, Map<String, Object> details) {

    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details) {
        return new ErrorResponse(code.name(), message, details == null ? Map.of() : details);
    }
}
