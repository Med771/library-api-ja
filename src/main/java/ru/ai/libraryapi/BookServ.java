package ru.ai.libraryapi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.ai.libraryapi.config.BookCfg;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServ {
    private static final Pattern bodyPattern = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");
    private static final Pattern OPEN_DIV_PATTERN = Pattern.compile("(?is)<div[^>]*>");
    private static final Pattern CLOSE_DIV_PATTERN = Pattern.compile("(?is)</div>");
    private static final Pattern P_PATTERN = Pattern.compile("(?is)<p.*?>.*?</p>");

    private final BookCfg bookCfg;

    private final EpubExtractor epubExtractor;

    public ResDTO getPages(ReqDTO req) {
        try {
            String epubFilePath = Paths.get(bookCfg.getLibraryPath(), req.path()).toString();
            log.info("Reading EPUB file: {}", epubFilePath);

            List<List<String>> pages = splitEpubByPages(epubFilePath);

            int from = req.from();
            int to = Math.min(pages.size(), req.to());

            log.info("Returning pages {}–{} (total pages: {})", from, to, pages.size());

            return new ResDTO(pages.subList(from, to), from, to, pages.size());

        } catch (Exception e) {
            log.error("Error when open EPUB: {}", e.getMessage(), e);
            return new ResDTO(new ArrayList<>(), req.from(), req.to(), 0);
        }
    }

    private List<List<String>> splitEpubByPages(String epubPath) {
        try {
            List<String> pages = epubExtractor.extractChaptersInReadingOrder(epubPath);
            List<String> cleanedPages = cleanPages(pages);
            List<String> splitPages = splitPages(cleanedPages);

            List<List<String>> splitPagesList = new ArrayList<>();

            for (String page : splitPages) {
                log.info("Processing page {}", page.length());

                splitPagesList.add(List.of(page));
            }

            return splitPagesList;
        }
        catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<String> cleanPages(List<String> pages) {
        List<String> cleanedPages = new ArrayList<>();

        for (String page : pages) {
            Matcher matcher = bodyPattern.matcher(page);

            String bodyContent = matcher.find() ? matcher.group(1) : "";

            String bodyClean;

            bodyClean = bodyContent.replaceAll("(?is)<a\\b[^>]*>.*?</a>", "");
            bodyClean = bodyClean.replaceAll("(?is)<img\\b[^>]*>", "");
            bodyClean = bodyClean.replaceAll("(?is)<" + "span" + "\\b[^>]*>", "");
            bodyClean = bodyClean.replaceAll("(?is)</" + "span" + ">", "");
            bodyClean = bodyClean.replaceAll("(?is)<svg\\b[^>]*>.*?</svg>", "");

            if (!bodyClean.isBlank()) {
                cleanedPages.add(bodyClean);
            }
        }

        return cleanedPages;
    }

    private static final int MIN_PAGE_LENGTH = 60;

    private List<String> splitPages(List<String> blocks) {
        List<String> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();

        for (String block : blocks) {
            if (block.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                }
                pages.addAll(splitLargeBlock(block));
                continue;
            }

            if (currentPage.length() + block.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                }
                currentPage = new StringBuilder();
            }

            if (block.length() < MIN_PAGE_LENGTH) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                }
                pages.add(block);
                continue;
            }

            currentPage.append(block);
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage.toString());
        }

        return pages;
    }

    private List<String> splitLargeBlock(String block) {
        List<String> subPages = new ArrayList<>();

        String openingDiv = extractFirstTag(block, OPEN_DIV_PATTERN);
        String closingDiv = extractFirstTag(block, CLOSE_DIV_PATTERN);

        Matcher matcher = P_PATTERN.matcher(block);
        StringBuilder current = new StringBuilder();
        if (!openingDiv.isEmpty()) current.append(openingDiv);

        while (matcher.find()) {
            String subBlock = matcher.group();

            if (current.length() + subBlock.length() + closingDiv.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                if (!closingDiv.isEmpty()) current.append(closingDiv);
                subPages.add(current.toString());

                current = new StringBuilder();
                if (!openingDiv.isEmpty()) current.append(openingDiv);
            }

            current.append(subBlock);
        }

        if (!current.isEmpty()) {
            if (!closingDiv.isEmpty()) current.append(closingDiv);
            subPages.add(current.toString());
        }

        return subPages;
    }


    private String extractFirstTag(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "";
    }
}
