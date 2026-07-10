package com.example.subscription.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Unified error response body (requirement 19):
 * <pre>{ "code": "...", "message": "...", "details": {} }</pre>
 */
@Schema(name = "ErrorResponse", description = "Единый формат ошибки")
public record ErrorResponse(

        @Schema(description = "Машиночитаемый код ошибки",
                example = "REDIS_UNAVAILABLE",
                allowableValues = {"INVALID_FILTER", "INVALID_FIELDS", "INVALID_SUBSCRIBER_NAME",
                        "INVALID_TOPIC_POSTFIX", "UNSUPPORTED_ENGINE", "SUBSCRIPTION_NOT_FOUND",
                        "INVALID_STATUS", "QUOTA_EXCEEDED", "REDIS_UNAVAILABLE",
                        "INITIALIZATION_FAILED", "INVALID_REQUEST", "INTERNAL_ERROR"})
        String code,

        @Schema(description = "Человекочитаемое сообщение", example = "Redis unavailable")
        String message,

        @Schema(description = "Дополнительные детали ошибки", example = "{}")
        Map<String, Object> details
) {

    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details) {
        return new ErrorResponse(code.name(), message, details == null ? Map.of() : details);
    }
}
