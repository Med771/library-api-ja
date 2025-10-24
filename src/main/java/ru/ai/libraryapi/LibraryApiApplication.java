package ru.ai.libraryapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Главный класс приложения Library API.
 * 
 * Приложение предоставляет REST API для работы с EPUB книгами,
 * включая разбор и получение страниц книг.
 * 
 * @author Library API Team
 * @version 0.1.0
 */
@SpringBootApplication
public class LibraryApiApplication {

    /**
     * Точка входа в приложение.
     * 
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(LibraryApiApplication.class, args);
    }
}
