package ru.ai.libraryapi.config;

import lombok.NonNull;
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
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/**") // или /api/** — если нужно ограничить маршруты
                        .allowedOriginPatterns("*") // вместо allowedOrigins("*"), чтобы можно было использовать allowCredentials(true)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
