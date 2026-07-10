package com.example.subscription.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resume request body. When {@code runInitialization} is true, the Subscription Service also
 * invokes the Initialization Service after re-activating the subscription.
 */
@Schema(name = "ResumeRequest", description = "Параметры возобновления подписки")
public record ResumeRequest(

        @Schema(description = "Запустить Initialization Service после возобновления",
                example = "false", defaultValue = "false")
        boolean runInitialization
) {

    public ResumeRequest {
        // Jackson uses the canonical constructor; default (false) applies when the field is absent.
    }
}
