package com.example.subscription.service;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.EngineType;
import com.example.subscription.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Basic structural/format validation of subscription input, producing the specific error codes
 * (INVALID_SUBSCRIBER_NAME, INVALID_TOPIC_POSTFIX, INVALID_FIELDS, UNSUPPORTED_ENGINE).
 *
 * <p>This is distinct from the semantic {@link com.example.subscription.service.validation.SubscriptionValidator}
 * (RSQL/model validation), which is a stub at this stage.
 */
@Component
public class SubscriptionInputParser {

    // DNS/Kafka-friendly names: lowercase alphanumerics and hyphens.
    private static final Pattern SUBSCRIBER_NAME = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern TOPIC_POSTFIX = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9._-]{0,61}[a-zA-Z0-9])?$");
    private static final Pattern FIELD_NAME = Pattern.compile("^[a-zA-Z0-9_.]+$");
    // Class token (sourceValue), e.g. FxSpotForwardTrade.
    private static final Pattern CLASS_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    public void validateSubscriberName(String subscriberName) {
        if (subscriberName == null || !SUBSCRIBER_NAME.matcher(subscriberName).matches()) {
            throw new ValidationException(ErrorCode.INVALID_SUBSCRIBER_NAME,
                    "Invalid subscriberName");
        }
    }

    /**
     * Validates request format and returns the parsed engine.
     */
    public EngineType parseAndValidate(CreateSubscriptionRequest request) {
        if (request == null) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "Missing request body");
        }

        String topicPostfix = request.topicPostfix();
        if (topicPostfix == null || !TOPIC_POSTFIX.matcher(topicPostfix).matches()) {
            throw new ValidationException(ErrorCode.INVALID_TOPIC_POSTFIX, "Invalid topicPostfix");
        }

        validateTargetsFormat(request.targets());

        List<String> fields = request.fields();
        if (fields == null || fields.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_FIELDS, "fields must not be empty");
        }
        for (String field : fields) {
            if (field == null || field.isBlank() || !FIELD_NAME.matcher(field).matches()) {
                throw new ValidationException(ErrorCode.INVALID_FIELDS, "Invalid field name: " + field);
            }
        }

        return parseEngine(request.engine());
    }

    /** Structural validation of targets: non-empty, each with a well-formed objectClass. */
    private void validateTargetsFormat(List<CreateSubscriptionRequest.TargetRequest> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new ValidationException(ErrorCode.INVALID_TARGETS, "targets must not be empty");
        }
        for (CreateSubscriptionRequest.TargetRequest t : targets) {
            if (t == null || t.objectClass() == null || t.objectClass().isBlank()
                    || !CLASS_NAME.matcher(t.objectClass()).matches()) {
                throw new ValidationException(ErrorCode.INVALID_TARGETS,
                        "Invalid target objectClass: " + (t == null ? null : t.objectClass()));
            }
        }
    }

    private EngineType parseEngine(String engine) {
        if (engine == null || engine.isBlank()) {
            throw new ValidationException(ErrorCode.UNSUPPORTED_ENGINE, "engine is required");
        }
        try {
            return EngineType.valueOf(engine);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.UNSUPPORTED_ENGINE, "Unsupported engine: " + engine);
        }
    }
}
