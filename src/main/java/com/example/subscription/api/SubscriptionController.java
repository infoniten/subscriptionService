package com.example.subscription.api;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.api.dto.ResumeRequest;
import com.example.subscription.api.dto.SubscriptionResponse;
import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.api.error.ErrorResponse;
import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.exception.ValidationException;
import com.example.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public REST API. All operations live inside the subscriber namespace; {@code subscriberName}
 * is always part of the URI and never read from the request body (requirement 4).
 */
@RestController
@RequestMapping("/api/v1/subscribers/{subscriberName}/subscriptions")
@Tag(name = "Subscriptions", description = "Управление жизненным циклом подписок в namespace подписчика")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать подписку",
            description = "Валидация → сохранение в PostgreSQL → runtime-конфигурация в Redis → "
                    + "публикация CONFIG_CHANGED. Redis — обязательная часть write-path: при его "
                    + "недоступности подписка не создаётся (503).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Подписка создана"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос "
                    + "(INVALID_TOPIC_POSTFIX / INVALID_FIELDS / UNSUPPORTED_ENGINE / INVALID_SUBSCRIBER_NAME)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "QUOTA_EXCEEDED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "REDIS_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SubscriptionResponse create(@PathVariable String subscriberName,
                                       @RequestBody CreateSubscriptionRequest request) {
        Subscription subscription = service.create(subscriberName, request);
        return toResponse(subscription);
    }

    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Получить подписку по id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUBSCRIPTION_NOT_FOUND "
                    + "(в том числе при обращении к чужой подписке)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SubscriptionResponse get(@PathVariable String subscriberName,
                                    @PathVariable String subscriptionId) {
        return toResponse(service.get(subscriberName, subscriptionId));
    }

    @GetMapping
    @Operation(summary = "Список подписок с фильтрами",
            description = "Без фильтра по статусу подписки в статусе DELETED скрыты.")
    public List<SubscriptionResponse> list(
            @PathVariable String subscriberName,
            @Parameter(description = "Фильтр по статусу",
                    schema = @Schema(allowableValues = {"ACTIVE", "PAUSED", "FAILED", "DELETED"}))
            @RequestParam(required = false) String status,
            @Parameter(description = "Фильтр по постфиксу топика", example = "prod")
            @RequestParam(required = false) String topicPostfix,
            @Parameter(description = "Фильтр по режиму Engine",
                    schema = @Schema(allowableValues = {"OBJECT_STREAM", "OBJECT_WITH_PREVIOUS", "EVENT_WITH_REMOVE"}))
            @RequestParam(required = false) String engine) {
        SubscriptionStatus statusFilter = parseStatus(status);
        EngineType engineFilter = parseEngine(engine);
        return service.list(subscriberName, statusFilter, topicPostfix, engineFilter).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{subscriptionId}/pause")
    @Operation(summary = "Приостановить подписку",
            description = "ACTIVE → PAUSED. Идемпотентно для уже приостановленной подписки. "
                    + "Replay при последующем resume отсутствует.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUBSCRIPTION_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "REDIS_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SubscriptionResponse pause(@PathVariable String subscriberName,
                                      @PathVariable String subscriptionId) {
        return toResponse(service.pause(subscriberName, subscriptionId));
    }

    @PostMapping("/{subscriptionId}/resume")
    @Operation(summary = "Возобновить подписку",
            description = "PAUSED → ACTIVE. При runInitialization=true дополнительно вызывается "
                    + "Initialization Service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUBSCRIPTION_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "QUOTA_EXCEEDED (лимит initialization)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "INITIALIZATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "REDIS_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SubscriptionResponse resume(@PathVariable String subscriberName,
                                       @PathVariable String subscriptionId,
                                       @RequestBody(required = false) ResumeRequest request) {
        boolean runInit = request != null && request.runInitialization();
        return toResponse(service.resume(subscriberName, subscriptionId, runInit));
    }

    @DeleteMapping("/{subscriptionId}")
    @Operation(summary = "Удалить подписку",
            description = "ACTIVE/PAUSED/FAILED → DELETED. Идемпотентно. Kafka-топик не удаляется.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SUBSCRIPTION_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "REDIS_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public SubscriptionResponse delete(@PathVariable String subscriberName,
                                       @PathVariable String subscriptionId) {
        return toResponse(service.delete(subscriberName, subscriptionId));
    }

    @PostMapping("/{subscriptionId}/initialization")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Запустить initialization",
            description = "Subscription Service вызывает Initialization Service; выгрузкой данных "
                    + "сам не занимается.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Initialization job запрошен"),
            @ApiResponse(responseCode = "404", description = "SUBSCRIPTION_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "QUOTA_EXCEEDED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "INITIALIZATION_FAILED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> initialize(@PathVariable String subscriberName,
                                           @PathVariable String subscriptionId) {
        service.initialize(subscriberName, subscriptionId);
        return ResponseEntity.accepted().build();
    }

    private SubscriptionResponse toResponse(Subscription subscription) {
        return SubscriptionResponse.from(subscription, service.topicName(subscription));
    }

    private SubscriptionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return SubscriptionStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "Invalid status filter: " + status);
        }
    }

    private EngineType parseEngine(String engine) {
        if (engine == null || engine.isBlank()) {
            return null;
        }
        try {
            return EngineType.valueOf(engine);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.UNSUPPORTED_ENGINE, "Invalid engine filter: " + engine);
        }
    }
}
