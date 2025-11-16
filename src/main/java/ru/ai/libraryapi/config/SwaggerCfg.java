package ru.ai.libraryapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
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
                version = "v0.2.0",
                description = "REST API для работы с EPUB книгами. " +
                        "Предоставляет функциональность для разбора EPUB файлов " +
                        "и получения их страниц в удобном для чтения формате.",
                contact = @Contact(
                        name = "Library API Team +7 (8352) 35-50-71"
                )
        )
)
public class SwaggerCfg {
    @Value("${app.swagger.url}")
    private String url;

    @Bean
    public OpenAPI customOpenAPI() {
        OpenAPI openAPI = new OpenAPI();

        // Указываем HTTPS сервер для Swagger UI
        openAPI.addServersItem(new Server().url(url));

        return openAPI;
    }
}