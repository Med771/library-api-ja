package ru.ai.libraryapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Конфигурация для работы с библиотекой книг.
 * 
 * Содержит настройки путей к библиотеке и параметры
 * разбиения книг на страницы.
 */
@Configuration
public class BookCfg {
    
    /**
     * Директория библиотеки относительно рабочей директории приложения.
     */
    @Value("${app.library.dir}")
    public String LIBRARY_DIR;

    /**
     * Максимальный размер страницы в символах.
     */
    @Value("${app.library.max-length}")
    public int LIBRARY_MAX_LENGTH;

    /**
     * Возвращает полный путь к библиотеке книг.
     * 
     * @return абсолютный путь к директории библиотеки
     */
    @Bean
    public String getLibraryPath() {
        String baseDir = Paths.get(System.getProperty("user.dir")).toString();
        return Paths.get(baseDir, LIBRARY_DIR).toString();
    }
}