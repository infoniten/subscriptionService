package com.example.subscription.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that, through the actual Spring-configured ObjectMapper, an {@link Instant} is rendered in
 * the canonical {@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX} form (millisecond + offset) rather than the
 * default ISO-instant (nanoseconds, UTC {@code Z}).
 */
@JsonTest
@Import(JacksonConfig.class)
class JacksonConfigTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    void instantRenderedInCanonicalOffsetFormat() throws Exception {
        // 13:48:10.402157522 UTC -> 16:48:10.402 at +03:00, milliseconds only.
        Instant t = Instant.parse("2026-07-23T13:48:10.402157522Z");

        String json = mapper.writeValueAsString(Map.of("createdAt", t));

        assertThat(json).contains("\"2026-07-23T16:48:10.402+03:00\"");
        assertThat(json).doesNotContain("402157522");
        assertThat(json).doesNotContain("Z\"");
    }
}
