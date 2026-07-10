package com.example.subscription.service.validation;

import com.example.subscription.domain.EngineType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * No-op validator that accepts every subscription (the original requirement-14 stub).
 *
 * <p>Superseded in production by {@link MetamodelSubscriptionValidator}; retained (not a Spring
 * bean) as a convenient test double and as a fallback should metamodel validation be disabled.
 */
public class StubSubscriptionValidator implements SubscriptionValidator {

    private static final Logger log = LoggerFactory.getLogger(StubSubscriptionValidator.class);

    @Override
    public void validate(String subscriberName, EngineType engine, String filter, List<String> fields) {
        log.debug("Stub validation passed for subscriber={} engine={} fields={} filterLen={}",
                subscriberName, engine, fields == null ? 0 : fields.size(),
                filter == null ? 0 : filter.length());
    }
}
