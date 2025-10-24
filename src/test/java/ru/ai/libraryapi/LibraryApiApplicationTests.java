package ru.ai.libraryapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для главного класса приложения.
 */
@SpringBootTest
@ActiveProfiles("test")
class LibraryApiApplicationTests {

    @Test
    void contextLoads() {
        // Проверяем, что Spring контекст загружается без ошибок
        assertThat(true).isTrue();
    }

    @Test
    void applicationStartsSuccessfully() {
        // Проверяем, что приложение запускается корректно
        assertThat(System.getProperty("java.version")).isNotNull();
    }
}
