package ru.ai.libraryapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCfg {

    /**
     * Разрешенный домен для доступа к API.
     */
    @Value("${app.cors.allowed-origin}")
    private String allowedOrigin;

    /**
     * Путь для доступа к Swagger UI.
     */
    @Value("${app.cors.swagger-path}")
    private String swaggerPath;

    /**
     * Настройка CORS политики.
     *
     * @return конфигуратор CORS
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Разрешаем доступ только с основного сайта для всех эндпоинтов
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigin)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowCredentials(true);

                // Разрешаем доступ к Swagger только с основного сайта
                registry.addMapping(swaggerPath)
                        .allowedOrigins(allowedOrigin)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowCredentials(true);
            }
        };
    }
}
