package ru.ai.libraryapi;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.ai.libraryapi.config.BookCfg;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for processing EPUB books: extraction, cleaning, and splitting into pages.
 */
@Service
@RequiredArgsConstructor
public class BookServ {
    private static final Logger logger = LoggerFactory.getLogger(BookServ.class);

    private static final Pattern BODY_PATTERN = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");

    // Example selectors from config (can be List<String> in BookCfg)
    private static final String REMOVE_SELECTORS = "img, svg, meta, link, style";
    private static final String UNWRAP_SELECTORS = "span, a";

    private final BookCfg bookCfg;
    private final EpubExtractor epubExtractor;

    /**
     * Retrieves paginated content from an EPUB file.
     *
     * @param req Request DTO with path and page range.
     * @return Response DTO with pages and metadata.
     */
    public ResDTO getPages(ReqDTO req) {
        try {
            String epubFilePath = Paths.get(bookCfg.getLibraryPath(), req.path()).toString();
            logger.info("Reading EPUB file: {}", epubFilePath);

            List<String> pages = splitEpubByPages(epubFilePath);

            int from = req.from();
            int to = Math.min(req.to(), pages.size());

            logger.info("Returning pages {}–{} (total pages: {})", from, to, pages.size());
            return new ResDTO(Collections.singletonList(pages.subList(from, to)), from, to, pages.size());  // Assuming ResDTO updated to List<String>

        } catch (Exception e) {
            logger.error("Error opening EPUB: {}", e.getMessage(), e);
            return new ResDTO(new ArrayList<>(), req.from(), req.to(), 0);
        }
    }

    private List<String> splitEpubByPages(String epubPath) {
        try {
            List<String> rawChapters = epubExtractor.extractChaptersInReadingOrder(epubPath);
            int rawSize = rawChapters.size();
            List<String> cleanedChapters = cleanPages(rawChapters);
            int cleanedSize = cleanedChapters.size();
            List<String> splitPages = splitPages(cleanedChapters);
            int splitSize = splitPages.size();

            logger.info("Processed EPUB: raw chapters={}, cleaned={}, split pages={}", rawSize, cleanedSize, splitSize);
            return splitPages;
        } catch (Exception e) {
            logger.error("Failed to split EPUB: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Cleans raw chapters from EPUB.
     *
     * @param rawChapters List of raw HTML strings.
     * @return List of cleaned HTML strings.
     */
    public List<String> cleanPages(List<String> rawChapters) {
        List<String> cleanedChapters = new ArrayList<>();

        for (int index = 0; index < rawChapters.size(); index++) {
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Cleaning chapter #{}", index);
                }
                String cleaned = cleanChapter(rawChapters.get(index));
                if (!cleaned.isBlank()) {
                    cleanedChapters.add(cleaned);
                } else {
                    logger.warn("Cleaned chapter #{} is blank", index);
                }
            } catch (Exception e) {
                logger.error("Error cleaning chapter #{}: {}", index, e.getMessage());
            }
        }

        return cleanedChapters;
    }

    private String cleanChapter(String html) {
        // Remove BOM
        html = html.replace("\uFEFF", "");

        // Extract body content
        String body;
        Matcher bodyMatcher = BODY_PATTERN.matcher(html);
        if (bodyMatcher.find()) {
            body = bodyMatcher.group(1);
        } else {
            body = html;
            logger.warn("Body not found; using full content");
        }

        // Clean with JSoup
        return cleanHtml(body);
    }

    private String cleanHtml(String html) {
        Document doc = Jsoup.parse(html);

        // Remove unwanted elements
        doc.select(REMOVE_SELECTORS).remove();

        // Unwrap inline elements
        doc.select(UNWRAP_SELECTORS).unwrap();

        // Remove style attributes from all elements
        doc.select("[style]").removeAttr("style");

        // Remove empty divs and ps (including those with &nbsp; after cleanup)
        doc.select("div:empty, p:empty").remove();

        // Normalize whitespace
        String cleaned = doc.body().html().trim().replaceAll("\\s{2,}", " ");
        if (logger.isDebugEnabled()) {
            logger.debug("Cleaned HTML length: {}", cleaned.length());
        }
        return cleaned;
    }

    /**
     * Splits cleaned chapters into pages based on max length.
     *
     * @param cleanedChapters List of cleaned HTML strings.
     * @return List of page strings.
     */
    private List<String> splitPages(List<String> cleanedChapters) {
        List<String> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();

        for (String chapter : cleanedChapters) {
            String trimmedChapter = chapter.trim();
            if (trimmedChapter.isEmpty()) {
                continue;
            }

            if (chapter.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                }
                pages.addAll(splitLargeChapter(chapter));
                continue;
            }

            if (chapter.length() < 60) {  // Inline MIN_PAGE_LENGTH
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                }
                pages.add(chapter);
                continue;
            }

            if (currentPage.length() + chapter.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                }
                currentPage = new StringBuilder();
            }

            currentPage.append(chapter);
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage.toString());
        }

        return pages;
    }

    private List<String> splitLargeChapter(String chapter) {
        List<String> subPages = new ArrayList<>();

        // Parse fragment for efficiency
        Document doc = Jsoup.parseBodyFragment(chapter);
        Elements children = doc.body().children();

        StringBuilder current = new StringBuilder();

        for (Element el : children) {
            String subBlock = el.outerHtml();

            if (current.length() + subBlock.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                String candidate = current.toString().trim();
                if (!candidate.isEmpty()) {
                    subPages.add(candidate);
                }
                current = new StringBuilder();
            }

            current.append(subBlock);
        }

        if (!current.isEmpty()) {
            String candidate = current.toString().trim();
            if (!candidate.isEmpty()) {
                subPages.add(candidate);
            }
        }

        // Fallback for unsplittable large elements
        if (subPages.isEmpty() && !chapter.trim().isEmpty()) {
            subPages.add(chapter.trim());
            logger.warn("Added unsplittable large chapter (length: {})", chapter.length());
        }

        return subPages;
    }
}