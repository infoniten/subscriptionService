package com.example.subscription.api.dto;

import com.example.subscription.domain.Subscription;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Subscription representation returned by the public API.
 */
@Schema(name = "SubscriptionResponse", description = "Представление подписки")
public record SubscriptionResponse(

        @Schema(description = "Идентификатор подписки", example = "sub-fc78797d-888f-447d-9c63-e24ea9a0aaa0")
        String subscriptionId,

        @Schema(description = "Имя подписчика (из URI)", example = "risk-service")
        String subscriberName,

        @Schema(description = "Постфикс топика", example = "prod")
        String topicPostfix,

        @Schema(description = "Полное имя Kafka-топика", example = "subscription.risk-service.prod")
        String topic,

        @Schema(description = "Возвращаемые поля объекта", example = "[\"dealId\", \"portfolioId\", \"status\"]")
        List<String> fields,

        @Schema(description = "RSQL-фильтр", example = "portfolioId==P1", nullable = true)
        String filter,

        @Schema(description = "Режим работы Engine", example = "EVENT_WITH_REMOVE")
        String engine,

        @Schema(description = "Статус жизненного цикла",
                allowableValues = {"ACTIVE", "PAUSED", "FAILED", "DELETED"}, example = "ACTIVE")
        String status,

        @Schema(description = "Причина перевода в FAILED (машиночитаемая)",
                example = "FILTER_SCHEMA_MISMATCH", nullable = true)
        String failureReason,

        @Schema(description = "Пояснение к FAILED", example = "Field portfolioId not found", nullable = true)
        String failureMessage,

        @Schema(description = "Момент создания")
        Instant createdAt,

        @Schema(description = "Момент последнего изменения статуса")
        Instant updatedAt
) {

    public static SubscriptionResponse from(Subscription s, String topic) {
        return new SubscriptionResponse(
                s.getId(),
                s.getSubscriberName(),
                s.getTopicPostfix(),
                topic,
                List.copyOf(s.getFields()),
                s.getFilter(),
                s.getEngine().name(),
                s.getStatus().name(),
                s.getFailureReason(),
                s.getFailureMessage(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
