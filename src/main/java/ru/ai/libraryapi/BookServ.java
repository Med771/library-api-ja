package ru.ai.libraryapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import ru.ai.libraryapi.config.BookCfg;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для работы с EPUB книгами.
 * 
 * Предоставляет функциональность для разбора EPUB файлов,
 * очистки HTML контента и разбиения на страницы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookServ {
    
    private final BookCfg bookCfg;

    /**
     * Получение страниц из EPUB файла.
     * 
     * @param req запрос с путем к файлу и диапазоном страниц
     * @return результат с разобранными страницами
     */
    public ResDTO getPages(ReqDTO req) {
        try {
            String epubFilePath = Paths.get(bookCfg.getLibraryPath(), req.path()).toString();
            log.info("Чтение EPUB файла: {}", epubFilePath);

            List<List<String>> pages = splitEpubByPages(epubFilePath, bookCfg.LIBRARY_MAX_LENGTH);

            // Нормализация диапазона страниц
            int from = req.from();
            int to = Math.min(pages.size(), req.to());

            log.info("Возвращаем страницы с {} по {} (всего страниц: {})", from, to, pages.size());

            return new ResDTO(pages.subList(from, to), from, to, pages.size());

        } catch (Exception e) {
            log.error("Ошибка при разборе EPUB: {}", e.getMessage(), e);
            return new ResDTO(new ArrayList<>(), req.from(), req.to(), 0);
        }
    }

    /**
     * Основной метод для разбиения EPUB файла на страницы.
     * 
     * Читает EPUB файл, очищает HTML контент от ненужных элементов
     * и разбивает на страницы заданного размера.
     * 
     * @param epubPath путь к EPUB файлу
     * @param pageSize максимальный размер страницы в символах
     * @return список страниц с очищенным HTML контентом
     * @throws IOException если произошла ошибка при чтении файла
     */
    public static List<List<String>> splitEpubByPages(String epubPath, int pageSize) throws IOException {
        List<List<String>> pages = new ArrayList<>();
        List<String> currentPage = new ArrayList<>();
        int currentSize = 0;

        log.info("Начало разбиения EPUB: {}", epubPath);

        try (FileInputStream fis = new FileInputStream(epubPath)) {
            Book book = new EpubReader().readEpub(fis);
            List<Resource> contents = book.getContents();
            log.info("Найдено {} ресурсов в EPUB", contents.size());

            // Обрабатываем каждый ресурс в книге
            for (int i = 0; i < contents.size(); i++) {
                Resource res = contents.get(i);
                Document doc = Jsoup.parse(new String(res.getData(), StandardCharsets.UTF_8));

                // Удаляем ненужные теги (изображения, скрипты, стили)
                doc.select("svg, img, script, style").remove();

                // Выбираем только блочные элементы с текстовым содержимым
                Elements blocks = doc.select("p, div, section, article, blockquote, h1, h2, h3, h4, h5, h6");
                log.debug("Ресурс {} содержит {} блочных элементов", i, blocks.size());

                // Обрабатываем каждый блок
                for (Element block : blocks) {
                    String cleanHtml = cleanBlock(block);
                    int blockLength = block.text().length();

                    // Если текущая страница не переполнится, добавляем блок
                    if (currentSize + blockLength < pageSize) {
                        currentPage.add(cleanHtml);
                        currentSize += blockLength;
                    } else {
                        // Создаем новую страницу
                        if (!currentPage.isEmpty()) {
                            pages.add(new ArrayList<>(currentPage));
                            log.debug("Создана новая страница размером {} символов", currentSize);
                        }
                        
                        currentPage.clear();
                        currentPage.add(cleanHtml);
                        currentSize = blockLength;
                    }
                }
            }

            // Добавляем последнюю страницу, если она не пустая
            if (!currentPage.isEmpty()) {
                pages.add(new ArrayList<>(currentPage));
                log.debug("Добавлена последняя страница размером {} символов", currentSize);
            }
        }

        log.info("Разбор EPUB завершен, всего страниц: {}", pages.size());
        return pages;
    }

    /**
     * Очищает HTML блок от ненужных атрибутов и форматирования.
     * 
     * Удаляет атрибуты class и style, а также лишние пробелы
     * между тегами для получения чистого HTML.
     * 
     * @param block HTML элемент для очистки
     * @return очищенный HTML в виде строки
     */
    private static String cleanBlock(Element block) {
        // Удаляем атрибуты стилизации
        block.removeAttr("class");
        block.removeAttr("style");

        String html = block.outerHtml();
        
        // Убираем лишние пробелы между тегами
        html = html.replaceAll(">\\s+<", "><").trim();

        return html;
    }
}
