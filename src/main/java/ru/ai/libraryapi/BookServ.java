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
    private static final String UNWRAP_SELECTORS = "span, a, b, strong, i, em";

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

            List<List<String>> pages = new ArrayList<>();

            for (String page: splitEpubByPages(epubFilePath)) {
                pages.add(List.of(page));
            }

            int from = req.from();
            int to = Math.min(req.to(), pages.size());

            logger.info("Returning pages {}–{} (total pages: {})", from, to, pages.size());
            return new ResDTO(pages.subList(from, to), from, to, pages.size());

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
            List<String> mergedChapters = mergeHeaders(cleanedChapters);
            List<String> splitPages = splitPages(mergedChapters);
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

        // Remove Calibre-specific navigation (TOC-like ul and hr)
        doc.select("ul[class^=calibre], hr[class^=calibre]").remove();

        // Remove duplicate headings (e.g., repeated titles)
        removeDuplicateHeadings(doc);

        // Remove empty divs and ps (including those with &nbsp; after cleanup)
        doc.select("div:empty, p:empty").remove();

        // Normalize whitespace and remove excessive empty lines
        String cleaned = doc.body().html().trim();
        cleaned = cleaned.replaceAll("\\s{2,}", " ");  // Multiple spaces to single
        cleaned = cleaned.replaceAll("(<br\\s*/?>\\s*){2,}", "<br><br>");  // Max 1 empty line equivalent
        cleaned = cleaned.replaceAll("(\\n{3,})", "\n\n");  // Max 2 newlines

        if (logger.isDebugEnabled()) {
            logger.debug("Cleaned HTML length: {}", cleaned.length());
        }
        return cleaned;
    }

    private void removeDuplicateHeadings(Document doc) {
        Elements headings = doc.select("h1, h2, h3, h4, h5, h6");
        String prevText = null;
        for (Element heading : headings) {
            String text = heading.text().trim();
            if (text.equals(prevText)) {
                heading.remove();
            } else {
                prevText = text;
            }
        }
    }

    private List<String> mergeHeaders(List<String> chapters) {
        List<String> merged = new ArrayList<>();
        StringBuilder pendingHeader = new StringBuilder();

        for (String chapter : chapters) {
            if (isPrimarilyHeader(chapter)) {
                pendingHeader.append(chapter);
            } else {
                if (!pendingHeader.isEmpty()) {
                    merged.add("<div>" + pendingHeader + "</div>" + chapter);
                    pendingHeader = new StringBuilder();
                } else {
                    merged.add(chapter);
                }
            }
        }

        if (!pendingHeader.isEmpty()) {
            merged.add("<div>" + pendingHeader + "</div>");
        }

        return merged;
    }

    /**
     * Splits cleaned chapters into pages based on max length.
     *
     * @param chapters List of cleaned HTML strings.
     * @return List of page strings.
     */
    private List<String> splitPages(List<String> chapters) {
        List<String> pages = new ArrayList<>();
        StringBuilder currentPage = new StringBuilder();

        for (String chapter : chapters) {
            String trimmedChapter = chapter.trim();
            if (trimmedChapter.isEmpty()) {
                continue;
            }

            int minPageLength = getMinPageLength(chapter);  // Dynamic min length

            if (chapter.length() > bookCfg.LIBRARY_MAX_LENGTH) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                }
                pages.addAll(splitLargeChapter(chapter));
                continue;
            }

            boolean isHeader = isPrimarilyHeader(chapter);

            if (chapter.length() < minPageLength || isHeader) {
                // Merge with current or next by appending to current
                currentPage.append(chapter);
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

    private int getMinPageLength(String chapter) {
        // Check if there's clear division: parse and see if multiple children
        Document doc = Jsoup.parseBodyFragment(chapter);
        Elements children = doc.body().children();
        if (children.size() <= 1) {
            return 500;  // No clear division - increase min to 500
        } else {
            return 60;  // Default min
        }
    }

    private boolean isPrimarilyHeader(String chapter) {
        Document doc = Jsoup.parseBodyFragment(chapter);
        Elements children = doc.body().children();
        if (children.size() <= 2) {
            Element first = children.first();
            if (first != null) {
                String tag = first.tagName();
                String className = first.attr("class");
                String id = first.attr("id");
                String text = first.text().trim();
                return tag.matches("h[1-6]") ||
                        (tag.equals("div") && className.matches(".*(title|head).*")) ||
                        (!id.isEmpty() && id.matches(".*toc.*")) ||
                        (text.length() < 200 && children.size() == 1);
            }
        }
        return false;
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

        // Post-process to merge standalone headers
        for (int i = 0; i < subPages.size() - 1; i++) {
            if (isPrimarilyHeader(subPages.get(i))) {
                subPages.set(i, subPages.get(i) + subPages.get(i + 1));
                subPages.remove(i + 1);
                i--;  // Re-check the new merged subpage if needed
            }
        }

        return subPages;
    }
}