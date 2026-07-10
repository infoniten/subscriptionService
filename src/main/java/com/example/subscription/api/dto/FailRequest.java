package com.example.subscription.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Internal request used by the Engine Service to move a subscription to FAILED
 * (e.g. after a model change made the filter invalid).
 */
public record FailRequest(
        @NotBlank String reason,
        String message
) {
}
