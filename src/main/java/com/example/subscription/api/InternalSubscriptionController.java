package com.example.subscription.api;

import com.example.subscription.api.dto.FailRequest;
import com.example.subscription.api.dto.SubscriptionResponse;
import com.example.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal API used by the Engine Service. Not part of the public subscriber namespace and not
 * exposed through Envoy to external callers.
 */
@RestController
@RequestMapping("/internal/subscriptions")
public class InternalSubscriptionController {

    private final SubscriptionService service;

    public InternalSubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    /**
     * Moves a subscription to FAILED (e.g. after a model change invalidated its filter).
     * A CONFIG_CHANGED signal is published afterwards.
     */
    @PostMapping("/{subscriptionId}/fail")
    public SubscriptionResponse fail(@PathVariable String subscriptionId,
                                     @Valid @RequestBody FailRequest request) {
        var subscription = service.fail(subscriptionId, request.reason(), request.message());
        return SubscriptionResponse.from(subscription, service.topicName(subscription));
    }
}
