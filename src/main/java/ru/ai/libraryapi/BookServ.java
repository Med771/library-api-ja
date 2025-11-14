package ru.ai.libraryapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.domain.SpineReference;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServ {

    private final BookCfg bookCfg;

    public ResDTO getPages(ReqDTO req) {
        try {
            String epubFilePath = Paths.get(bookCfg.getLibraryPath(), req.path()).toString();
            log.info("Чтение EPUB файла: {}", epubFilePath);

            List<List<String>> pages = splitEpubByPages(epubFilePath, bookCfg.LIBRARY_MAX_LENGTH);

            int from = req.from();
            int to = Math.min(pages.size(), req.to());

            log.info("Возвращаем страницы {}–{} (всего страниц: {})", from, to, pages.size());

            return new ResDTO(pages.subList(from, to), from, to, pages.size());

        } catch (Exception e) {
            log.error("Ошибка при разборе EPUB: {}", e.getMessage(), e);
            return new ResDTO(new ArrayList<>(), req.from(), req.to(), 0);
        }
    }

    /**
     * Парсинг EPUB и разбиение на страницы.
     */
    public static List<List<String>> splitEpubByPages(String epubPath, int pageSize) throws IOException {

        List<List<String>> pages = new ArrayList<>();
        List<String> currentPage = new ArrayList<>();
        int currentSize = 0;

        log.info("Начало разбиения EPUB: {}", epubPath);

        try (FileInputStream fis = new FileInputStream(epubPath)) {

            Book book = new EpubReader().readEpub(fis);

            // === Правильное чтение главы через spine ===
            List<SpineReference> spine = book.getSpine().getSpineReferences();
            log.info("Найдено {} spine-ресурсов", spine.size());

            List<Resource> contents = new ArrayList<>();
            for (SpineReference ref : spine) {
                contents.add(ref.getResource());
            }

            for (int i = 0; i < contents.size(); i++) {
                Resource res = contents.get(i);

                byte[] data = res.getData();
                if (data == null) continue;

                Document doc = Jsoup.parse(new String(data, StandardCharsets.UTF_8));

                // Удаляем ненужные элементы
                doc.select("svg, img, script, style, a, sup").remove();

                // Выбираем текстовые блоки
                Elements blocks = doc.select(
                        "p, div, section, article, blockquote, " +
                                "h1, h2, h3, h4, h5, h6, ul, ol, li"
                );

                log.debug("Ресурс {} содержит {} элементов", i, blocks.size());

                for (Element block : blocks) {

                    if (block.text().isBlank()) continue;

                    String cleanHtml = cleanBlock(block);
                    int blockLen = block.text().length();

                    // Влезает?
                    if (currentSize + blockLen < pageSize) {
                        currentPage.add(cleanHtml);
                        currentSize += blockLen;
                    } else {
                        // Закрываем страницу
                        if (!currentPage.isEmpty()) {
                            pages.add(new ArrayList<>(currentPage));
                            log.debug("Страница создана: {} символов", currentSize);
                        }

                        currentPage.clear();
                        currentPage.add(cleanHtml);
                        currentSize = blockLen;
                    }
                }
            }

            // Последняя страница
            if (!currentPage.isEmpty()) {
                pages.add(new ArrayList<>(currentPage));
                log.debug("Добавлена последняя страница: {} символов", currentSize);
            }
        }

        log.info("Готово — страниц: {}", pages.size());
        return pages;
    }

    /**
     * Очистка одного HTML-блока.
     */
    private static String cleanBlock(Element block) {
        block.removeAttr("class");
        block.removeAttr("style");
        block.removeAttr("id");

        String html = block.outerHtml();
        html = html.replaceAll(">\\s+<", "><").trim();

        return html;
    }
}
