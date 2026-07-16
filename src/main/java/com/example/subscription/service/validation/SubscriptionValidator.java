package com.example.subscription.service.validation;

import com.example.subscription.domain.EngineType;
import com.example.subscription.domain.SubscriptionTarget;

import java.util.List;

/**
 * Validation component. Architecturally a separate concern; in the future it will validate RSQL
 * correctness, field existence, operators, functions and additional business rules.
 *
 * <p>The public API must not depend on the current implementation — hence this interface. The
 * present implementation is a stub that accepts everything (requirement 14).
 */
public interface SubscriptionValidator {

    /**
     * Validates the semantic correctness of a subscription (filter/fields against the model).
     * Throws {@link com.example.subscription.exception.ValidationException} on failure.
     */
    void validate(String subscriberName, EngineType engine,
                  List<SubscriptionTarget> targets, String filter, List<String> fields);
}
