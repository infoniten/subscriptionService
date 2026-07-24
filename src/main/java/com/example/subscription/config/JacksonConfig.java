package com.example.subscription.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders {@link Instant} timestamps (e.g. {@code createdAt} / {@code updatedAt} in API responses) in
 * the system-wide canonical form {@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX} — millisecond precision with a
 * zone offset, e.g. {@code 2026-03-11T10:12:33.000+03:00} — instead of Jackson's default ISO-instant
 * ({@code 2026-07-23T13:48:10.402157522Z}, nanoseconds, UTC {@code Z}).
 *
 * <p>The rendering zone is configurable via {@code subscription.api.time-zone} (default {@code +03:00}).
 * Registered as a Jackson {@link Module} bean, so Spring Boot installs it into the application
 * {@code ObjectMapper}. Only serialization is customized; {@code Instant} values themselves stay UTC.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module instantSerializationModule(@Value("${subscription.api.time-zone:+03:00}") String zone) {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneId.of(zone));
        SimpleModule module = new SimpleModule("InstantCanonicalFormat");
        module.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(formatter.format(value));
            }
        });
        return module;
    }
}
