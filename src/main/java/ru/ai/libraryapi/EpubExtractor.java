package ru.ai.libraryapi;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;

import org.springframework.stereotype.Service;
import org.w3c.dom.*;

@Service
public class EpubExtractor {
    public List<String> extractChaptersInReadingOrder(String epubPath) throws Exception {
        ZipFile zip = new ZipFile(epubPath);

        // 1. container.xml
        ZipEntry containerEntry = zip.getEntry("META-INF/container.xml");
        Document containerXml = parseXml(zip.getInputStream(containerEntry));
        String opfPath = containerXml.getElementsByTagName("rootfile")
                                     .item(0)
                                     .getAttributes()
                                     .getNamedItem("full-path")
                                     .getNodeValue();

        // 2. content.opf
        ZipEntry opfEntry = zip.getEntry(opfPath);
        Document opf = parseXml(zip.getInputStream(opfEntry));

        Map<String, String> manifest = new HashMap<>();

        NodeList items = opf.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            manifest.put(item.getAttribute("id"), item.getAttribute("href"));
        }

        List<String> chapters = new ArrayList<>();

        NodeList spine = opf.getElementsByTagName("itemref");
        String basePath = opfPath.substring(0, opfPath.lastIndexOf("/") + 1);

        for (int i = 0; i < spine.getLength(); i++) {
            Element itemref = (Element) spine.item(i);
            String idref = itemref.getAttribute("idref");
            String href = manifest.get(idref);

            if (href == null) continue;

            String fullPath = basePath + href;
            ZipEntry chapterEntry = zip.getEntry(fullPath);

            if (chapterEntry != null) {
                InputStream is = zip.getInputStream(chapterEntry);
                String text = new String(is.readAllBytes(), "UTF-8");
                chapters.add(text);
            }
        }

        return chapters;
    }

    private Document parseXml(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(is);
    }
}
