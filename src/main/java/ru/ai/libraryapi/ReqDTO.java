package ru.ai.libraryapi;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO для запроса страниц EPUB книги.
 * 
 * Содержит параметры для получения определенного диапазона страниц
 * из EPUB файла по указанному пути.
 */
public record ReqDTO(
        /**
         * Путь к EPUB файлу относительно библиотеки.
         * Не может быть пустым.
         */
        @NotBlank(message = "Путь к книге не должен быть пустым")
        String path,

        /**
         * Начальная страница для получения (индекс).
         * Не может быть меньше 0.
         */
        @Min(value = 0, message = "Параметр from не может быть меньше 0")
        int from,

        /**
         * Конечная страница для получения (индекс).
         * Не может быть меньше 0.
         */
        @Min(value = 0, message = "Параметр to не может быть меньше 0")
        int to
) {}
