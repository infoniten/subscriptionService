package com.example.subscription.service.validation;

import com.example.subscription.domain.EngineType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stub validator: every subscription is considered valid (requirement 14).
 * RSQL / field / operator validation will be added here later without changing the public API.
 */
@Component
public class StubSubscriptionValidator implements SubscriptionValidator {

    private static final Logger log = LoggerFactory.getLogger(StubSubscriptionValidator.class);

    @Override
    public void validate(String subscriberName, EngineType engine, String filter, List<String> fields) {
        log.debug("Stub validation passed for subscriber={} engine={} fields={} filterLen={}",
                subscriberName, engine, fields == null ? 0 : fields.size(),
                filter == null ? 0 : filter.length());
    }
}
