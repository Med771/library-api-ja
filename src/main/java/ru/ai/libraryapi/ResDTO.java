package ru.ai.libraryapi;

import java.util.List;

/**
 * DTO для ответа с результатом разбора EPUB книги.
 *
 * Содержит список страниц, где каждая страница представляет собой
 * список HTML блоков с очищенным контентом.
 */
public record ResDTO(
        List<List<String>> pages,
        int from,
        int to,
        int total
) {
}
