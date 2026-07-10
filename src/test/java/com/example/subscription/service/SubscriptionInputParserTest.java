package com.example.subscription.service;

import com.example.subscription.api.dto.CreateSubscriptionRequest;
import com.example.subscription.api.error.ErrorCode;
import com.example.subscription.domain.EngineType;
import com.example.subscription.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionInputParserTest {

    private final SubscriptionInputParser parser = new SubscriptionInputParser();

    @Test
    void acceptsValidRequest() {
        EngineType engine = parser.parseAndValidate(new CreateSubscriptionRequest(
                "prod", List.of("dealId"), "portfolioId==P1", "EVENT_WITH_REMOVE"));
        assertThat(engine).isEqualTo(EngineType.EVENT_WITH_REMOVE);
    }

    @Test
    void rejectsBadSubscriberName() {
        assertThatThrownBy(() -> parser.validateSubscriberName("Bad Name!"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_SUBSCRIBER_NAME);
    }

    @Test
    void acceptsGoodSubscriberName() {
        assertThatCode(() -> parser.validateSubscriberName("risk-service"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyFields() {
        assertThatThrownBy(() -> parser.parseAndValidate(new CreateSubscriptionRequest(
                "prod", List.of(), null, "OBJECT_STREAM")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_FIELDS);
    }

    @Test
    void rejectsBadTopicPostfix() {
        assertThatThrownBy(() -> parser.parseAndValidate(new CreateSubscriptionRequest(
                "bad postfix", List.of("a"), null, "OBJECT_STREAM")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode())
                .isEqualTo(ErrorCode.INVALID_TOPIC_POSTFIX);
    }

    @Test
    void rejectsUnsupportedEngine() {
        assertThatThrownBy(() -> parser.parseAndValidate(new CreateSubscriptionRequest(
                "prod", List.of("a"), null, "TURBO")))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getCode())
                .isEqualTo(ErrorCode.UNSUPPORTED_ENGINE);
    }
}
