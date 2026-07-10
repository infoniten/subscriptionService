package com.example.subscription.api.dto;

/**
 * Resume request body. When {@code runInitialization} is true, the Subscription Service also
 * invokes the Initialization Service after re-activating the subscription.
 */
public record ResumeRequest(boolean runInitialization) {

    public ResumeRequest {
        // Jackson uses the canonical constructor; default (false) applies when the field is absent.
    }
}
