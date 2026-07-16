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

        @Schema(description = "Классы объектов, на которые оформлена подписка (мультикласс). "
                + "Не может быть пустым.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TargetRequest> targets,

        @Schema(description = "Список возвращаемых полей объекта (не может быть пустым)",
                example = "[\"dealId\", \"portfolioId\", \"status\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> fields,

        @Schema(description = "RSQL-фильтр. Компиляция и валидация выполняются Engine Service",
                example = "portfolioId==P1", nullable = true)
        String filter,

        @Schema(description = "Режим работы Engine",
                example = "EVENT_WITH_REMOVE",
                allowableValues = {"OBJECT_STREAM", "OBJECT_WITH_PREVIOUS", "EVENT_WITH_REMOVE", "OBJECT_BATCH"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String engine
) {

    /**
     * A class target: {@code objectClass} plus whether subclasses are included. {@code includeSubclasses}
     * defaults to {@code true} (polymorphic) when omitted; pass {@code false} for an exact-class match.
     */
    @Schema(name = "TargetRequest", description = "Класс-цель подписки")
    public record TargetRequest(

            @Schema(description = "Класс объекта (sourceValue из метамодели)",
                    example = "FxSpotForwardTrade", requiredMode = Schema.RequiredMode.REQUIRED)
            String objectClass,

            @Schema(description = "Включать наследников класса. По умолчанию true (полиморфно); "
                    + "false — только точный класс.", example = "true", nullable = true)
            Boolean includeSubclasses
    ) {

        /** Effective flag with the default applied (true when the client omitted it). */
        public boolean includeSubclassesOrDefault() {
            return includeSubclasses == null || includeSubclasses;
        }
    }
}
