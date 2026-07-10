package com.example.subscription.api;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.api.dto.ResumeRequest;
import com.example.subscription.api.dto.SubscriptionResponse;
import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.Subscription;
import com.example.subscription.domain.SubscriptionStatus;
import com.example.subscription.exception.ValidationException;
import com.example.subscription.service.SubscriptionService;
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
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@PathVariable String subscriberName,
                                       @RequestBody CreateSubscriptionRequest request) {
        Subscription subscription = service.create(subscriberName, request);
        return toResponse(subscription);
    }

    @GetMapping("/{subscriptionId}")
    public SubscriptionResponse get(@PathVariable String subscriberName,
                                    @PathVariable String subscriptionId) {
        return toResponse(service.get(subscriberName, subscriptionId));
    }

    @GetMapping
    public List<SubscriptionResponse> list(@PathVariable String subscriberName,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String topicPostfix,
                                           @RequestParam(required = false) String engine) {
        SubscriptionStatus statusFilter = parseStatus(status);
        EngineType engineFilter = parseEngine(engine);
        return service.list(subscriberName, statusFilter, topicPostfix, engineFilter).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{subscriptionId}/pause")
    public SubscriptionResponse pause(@PathVariable String subscriberName,
                                      @PathVariable String subscriptionId) {
        return toResponse(service.pause(subscriberName, subscriptionId));
    }

    @PostMapping("/{subscriptionId}/resume")
    public SubscriptionResponse resume(@PathVariable String subscriberName,
                                       @PathVariable String subscriptionId,
                                       @RequestBody(required = false) ResumeRequest request) {
        boolean runInit = request != null && request.runInitialization();
        return toResponse(service.resume(subscriberName, subscriptionId, runInit));
    }

    @DeleteMapping("/{subscriptionId}")
    public SubscriptionResponse delete(@PathVariable String subscriberName,
                                       @PathVariable String subscriptionId) {
        return toResponse(service.delete(subscriberName, subscriptionId));
    }

    @PostMapping("/{subscriptionId}/initialization")
    @ResponseStatus(HttpStatus.ACCEPTED)
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
