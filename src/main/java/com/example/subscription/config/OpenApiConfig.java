package com.example.subscription.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. Only the public API ({@code /api/v1/**}) is exposed; the internal
 * {@code /internal/**} endpoints are excluded via {@code springdoc.paths-to-match} in
 * application.yml, so the internal Engine contract does not leak into the public spec.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subscriptionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Subscription Service API")
                        .version("v1")
                        .description("""
                                Управление конфигурацией подписок пользователей на поток объектов.

                                Все операции выполняются внутри namespace подписчика
                                (`/api/v1/subscribers/{subscriberName}/...`); `subscriberName`
                                всегда берётся из URI. Обращение к чужой подписке возвращает 404.

                                PostgreSQL — единственный Source Of Truth; Redis — обязательная
                                часть write-path (при его недоступности операции изменения
                                конфигурации возвращают 503).""")
                        .contact(new Contact().name("Subscription Service"))
                        .license(new License().name("Proprietary")));
    }
}
