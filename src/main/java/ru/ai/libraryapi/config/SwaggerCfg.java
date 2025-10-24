package ru.ai.libraryapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Swagger/OpenAPI документации.
 * 
 * Настраивает автоматическую генерацию документации API
 * с подробным описанием эндпоинтов и моделей данных.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Library API",
                version = "v0.1.0",
                description = "REST API для работы с EPUB книгами. " +
                        "Предоставляет функциональность для разбора EPUB файлов " +
                        "и получения их страниц в удобном для чтения формате.",
                contact = @Contact(
                        name = "Library API Team",
                        email = "support@example.com"
                ),
                license = @License(
                        name = "MIT License",
                        url = "https://opensource.org/licenses/MIT"
                )
        )
)
public class SwaggerCfg {
}