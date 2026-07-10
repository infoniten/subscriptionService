package com.example.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Create subscription request body. Note: {@code subscriberName} is NEVER accepted from the body —
 * it is always taken from the URI (requirement 4). {@code engine} is kept as a raw String so that
 * an unknown value produces the specific UNSUPPORTED_ENGINE error rather than a generic parse error.
 */
@Schema(name = "CreateSubscriptionRequest", description = "Конфигурация создаваемой подписки (immutable)")
public record CreateSubscriptionRequest(

        @Schema(description = "Постфикс топика; топик принадлежит паре subscriberName + topicPostfix",
                example = "prod", requiredMode = Schema.RequiredMode.REQUIRED)
        String topicPostfix,

        @Schema(description = "Список возвращаемых полей объекта (не может быть пустым)",
                example = "[\"dealId\", \"portfolioId\", \"status\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> fields,

        @Schema(description = "RSQL-фильтр. Компиляция и валидация выполняются Engine Service",
                example = "portfolioId==P1", nullable = true)
        String filter,

        @Schema(description = "Режим работы Engine",
                example = "EVENT_WITH_REMOVE",
                allowableValues = {"OBJECT_STREAM", "OBJECT_WITH_PREVIOUS", "EVENT_WITH_REMOVE"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String engine
) {
}
