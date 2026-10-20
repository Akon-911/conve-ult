package com.conveult;

import com.conveult.DocumentModel.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Universal Document conversion engine using Apache POI, Apache PDFBox, and OpenDocument standard.
 */
public class DocumentEngine {

    public static void convert(File inputFile, File outputFile, Format targetFormat, ImageEngine.Options imgOpts, String title, String author, Float margin) throws Exception {
        if (outputFile.getParentFile() != null) outputFile.getParentFile().mkdirs();

        Format inFmt = Format.fromFile(inputFile);
        String inExt = getExt(inputFile);
        String outExt = getExt(outputFile);

        Doc doc = parse(inputFile, inFmt, inExt);
        if (title != null) doc.setTitle(title);
        if (author != null) doc.setAuthor(author);

        render(doc, outputFile, targetFormat, outExt, imgOpts, margin);
    }

    // =========================================================================
    // 1. DOCUMENT PARSERS
    // =========================================================================

    public static Doc parse(File file, Format fmt, String ext) throws Exception {
        if (fmt == Format.PDF || "pdf".equalsIgnoreCase(ext)) return parsePdf(file);
        if (fmt == Format.DOCX || "docx".equalsIgnoreCase(ext)) return parseDocx(file);
        if (fmt == Format.DOC || "doc".equalsIgnoreCase(ext)) return parseDoc(file);
        if (fmt == Format.ODT || "odt".equalsIgnoreCase(ext)) return parseOdt(file);
        if (fmt == Format.HTML || "html".equalsIgnoreCase(ext) || "htm".equalsIgnoreCase(ext)) return parseHtml(file);
        if (fmt == Format.MD || "md".equalsIgnoreCase(ext) || "markdown".equalsIgnoreCase(ext)) return parseMarkdown(file);
        if (fmt != null && fmt.isImage()) return parseImageAsDoc(file);
        return parseText(file);
    }

    private static Doc parsePdf(File file) throws Exception {
        Doc doc = new Doc(getBaseName(file));
        try (PDDocument pdf = Loader.loadPDF(file)) {
            doc.setPageCount(pdf.getNumberOfPages());
            PDDocumentInformation info = pdf.getDocumentInformation();
            if (info != null) {
                if (info.getTitle() != null && !info.getTitle().isEmpty()) doc.setTitle(info.getTitle());
                if (info.getAuthor() != null) doc.setAuthor(info.getAuthor());
                if (info.getSubject() != null) doc.setSubject(info.getSubject());
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(pdf);
            parseTextLinesIntoDoc(fullText, doc);
        }
        return doc;
    }

    private static Doc parseDocx(File file) throws Exception {
        Doc doc = new Doc(getBaseName(file));
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument docx = new XWPFDocument(fis)) {

            try {
                var core = docx.getProperties().getCoreProperties();
                if (core.getTitle() != null && !core.getTitle().isEmpty()) doc.setTitle(core.getTitle());
                if (core.getCreator() != null) doc.setAuthor(core.getCreator());
            } catch (Exception ignored) {}

            for (IBodyElement elem : docx.getBodyElements()) {
                if (elem instanceof XWPFParagraph p) {
                    String text = p.getText();
                    if (text == null || text.trim().isEmpty()) continue;

                    String style = p.getStyle();
                    int headingLevel = 0;
                    if (style != null) {
                        String s = style.toLowerCase();
                        if (s.contains("heading") || s.contains("title")) {
                            if (s.contains("1") || s.contains("title")) headingLevel = 1;
                            else if (s.contains("2")) headingLevel = 2;
                            else if (s.contains("3")) headingLevel = 3;
                            else headingLevel = 4;
                        }
                    }

                    List<Run> runs = new ArrayList<>();
                    for (XWPFRun r : p.getRuns()) {
                        String rt = r.getText(0);
                        if (rt != null && !rt.isEmpty()) {
                            Run run = new Run(rt);
                            run.setBold(r.isBold());
                            run.setItalic(r.isItalic());
                            if (r.getUnderline() != UnderlinePatterns.NONE) run.setUnderline(true);
                            if (r.getColor() != null) run.setColor("#" + r.getColor());
                            runs.add(run);
                        }
                    }
                    if (runs.isEmpty()) runs.add(new Run(text));

                    if (headingLevel > 0) {
                        doc.addBlock(Block.heading(headingLevel, runs));
                    } else if (p.getNumID() != null) {
                        Block list = Block.list(false);
                        list.addListItem(new Item(runs));
                        doc.addBlock(list);
                    } else {
                        doc.addBlock(Block.paragraph(runs));
                    }
                } else if (elem instanceof XWPFTable table) {
                    Block tbl = Block.table();
                    boolean first = true;
                    for (XWPFTableRow r : table.getRows()) {
                        List<Cell> cells = new ArrayList<>();
                        for (XWPFTableCell c : r.getTableCells()) {
                            cells.add(new Cell(c.getText(), first));
                        }
                        tbl.addTableRow(cells);
                        first = false;
                    }
                    doc.addBlock(tbl);
                }
            }
        }
        return doc;
    }

    private static Doc parseDoc(File file) throws Exception {
        byte[] magic = new byte[8];
        try (FileInputStream in = new FileInputStream(file)) {
            int read = in.read(magic);
            if (read >= 2) {
                if (magic[0] == 0x50 && magic[1] == 0x4B) return parseDocx(file);
                if (magic[0] == '<') return parseHtml(file);
                if (magic[0] == '{' && magic[1] == '\\') return parseText(file);
            }
        }

        try (FileInputStream fis = new FileInputStream(file);
             HWPFDocument doc = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(doc)) {
            Doc udoc = new Doc(getBaseName(file));
            for (String p : extractor.getParagraphText()) {
                if (p != null && !p.trim().isEmpty()) {
                    String clean = p.trim();
                    if (clean.length() < 60 && !clean.endsWith(".") && !clean.endsWith(",")) {
                        udoc.addHeading(1, clean);
                    } else {
                        udoc.addParagraph(clean);
                    }
                }
            }
            return udoc;
        } catch (Exception e) {
            try { return parseHtml(file); } catch (Exception ignored) { return parseText(file); }
        }
    }

    private static Doc parseOdt(File file) throws Exception {
        Doc doc = new Doc(getBaseName(file));
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("content.xml");
            if (entry != null) {
                try (InputStream in = zip.getInputStream(entry)) {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document d = db.parse(in);

                    NodeList bodyList = d.getElementsByTagNameNS("*", "text");
                    if (bodyList.getLength() > 0) {
                        Node textBody = bodyList.item(0);
                        NodeList children = textBody.getChildNodes();
                        for (int i = 0; i < children.getLength(); i++) {
                            Node node = children.item(i);
                            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                            Element el = (Element) node;
                            String tag = el.getLocalName();
                            if ("h".equals(tag)) {
                                int lvl = 1;
                                String s = el.getAttributeNS("*", "outline-level");
                                if (!s.isEmpty()) {
                                    try { lvl = Integer.parseInt(s); } catch (Exception ignored) {}
                                }
                                doc.addHeading(lvl, el.getTextContent().trim());
                            } else if ("p".equals(tag)) {
                                String t = el.getTextContent().trim();
                                if (!t.isEmpty()) doc.addParagraph(t);
                            } else if ("list".equals(tag)) {
                                Block list = Block.list(false);
                                NodeList items = el.getElementsByTagNameNS("*", "list-item");
                                for (int j = 0; j < items.getLength(); j++) {
                                    String it = items.item(j).getTextContent().trim();
                                    if (!it.isEmpty()) list.addListItem(new Item(it));
                                }
                                if (!list.getListItems().isEmpty()) doc.addBlock(list);
                            } else if ("table".equals(tag)) {
                                Block table = Block.table();
                                NodeList rows = el.getElementsByTagNameNS("*", "table-row");
                                boolean first = true;
                                for (int r = 0; r < rows.getLength(); r++) {
                                    List<Cell> cells = new ArrayList<>();
                                    NodeList cList = ((Element) rows.item(r)).getElementsByTagNameNS("*", "table-cell");
                                    for (int c = 0; c < cList.getLength(); c++) {
                                        cells.add(new Cell(cList.item(c).getTextContent().trim(), first));
                                    }
                                    if (!cells.isEmpty()) {
                                        table.addTableRow(cells);
                                        first = false;
                                    }
                                }
                                if (!table.getTableRows().isEmpty()) doc.addBlock(table);
                            }
                        }
                    }
                }
            }
        }
        return doc;
    }

    private static Doc parseHtml(File file) throws Exception {
        String html = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Doc doc = new Doc(getBaseName(file));

        Matcher tm = Pattern.compile("(?is)<title>(.*?)</title>").matcher(html);
        if (tm.find()) doc.setTitle(cleanHtml(tm.group(1)));

        html = html.replaceAll("(?is)<head>.*?</head>", "")
                   .replaceAll("(?is)<script.*?</script>", "")
                   .replaceAll("(?is)<style.*?</style>", "");

        Matcher bm = Pattern.compile("(?is)<(h[1-6]|p|pre|blockquote|ul|ol|table|hr)([^>]*)>(.*?)</\\1>|(?is)<hr\\s*/?>").matcher(html);
        while (bm.find()) {
            String tag = bm.group(1);
            if (tag == null || "hr".equalsIgnoreCase(tag)) {
                doc.addBlock(Block.rule());
                continue;
            }
            tag = tag.toLowerCase();
            String inner = bm.group(3) != null ? bm.group(3) : "";

            if (tag.startsWith("h")) {
                int lvl = Character.getNumericValue(tag.charAt(1));
                doc.addBlock(Block.heading(lvl, parseInline(cleanHtml(inner))));
            } else if ("p".equals(tag)) {
                String c = cleanHtml(inner);
                if (!c.isEmpty()) doc.addBlock(Block.paragraph(parseInline(c)));
            } else if ("pre".equals(tag)) {
                doc.addBlock(Block.code(cleanHtml(inner), null));
            } else if ("blockquote".equals(tag)) {
                doc.addBlock(Block.quote(parseInline(cleanHtml(inner))));
            } else if ("ul".equals(tag) || "ol".equals(tag)) {
                Block list = Block.list("ol".equals(tag));
                Matcher lm = Pattern.compile("(?is)<li[^>]*>(.*?)</li>").matcher(inner);
                while (lm.find()) list.addListItem(new Item(parseInline(cleanHtml(lm.group(1)))));
                if (!list.getListItems().isEmpty()) doc.addBlock(list);
            } else if ("table".equals(tag)) {
                Block table = Block.table();
                Matcher trm = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>").matcher(inner);
                boolean first = true;
                while (trm.find()) {
                    List<Cell> cells = new ArrayList<>();
                    Matcher cm = Pattern.compile("(?is)<(th|td)[^>]*>(.*?)</\\1>").matcher(trm.group(1));
                    while (cm.find()) {
                        boolean h = "th".equalsIgnoreCase(cm.group(1)) || first;
                        cells.add(new Cell(parseInline(cleanHtml(cm.group(2))), h));
                    }
                    if (!cells.isEmpty()) {
                        table.addTableRow(cells);
                        first = false;
                    }
                }
                if (!table.getTableRows().isEmpty()) doc.addBlock(table);
            }
        }
        if (doc.getBlocks().isEmpty()) {
            parseTextLinesIntoDoc(cleanHtml(html), doc);
        }
        return doc;
    }

    private static Doc parseMarkdown(File file) throws Exception {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Doc doc = new Doc(getBaseName(file));

        String[] lines = content.split("\r?\n");
        int i = 0;
        Block currentList = null;

        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                i++;
                continue;
            }

            // Code block
            if (trimmed.startsWith("```")) {
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                String lang = trimmed.substring(3).trim();
                StringBuilder cb = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    cb.append(lines[i]).append("\n");
                    i++;
                }
                if (i < lines.length) i++;
                doc.addBlock(Block.code(cb.toString(), lang));
                continue;
            }

            // Headings
            if (trimmed.startsWith("#")) {
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                int lvl = 0;
                while (lvl < trimmed.length() && trimmed.charAt(lvl) == '#') lvl++;
                String ht = trimmed.substring(lvl).trim();
                doc.addBlock(Block.heading(lvl, parseInline(ht)));
                if (doc.getTitle() == null || doc.getTitle().equals(getBaseName(file))) doc.setTitle(ht);
                i++;
                continue;
            }

            // Blockquote
            if (trimmed.startsWith(">")) {
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                doc.addBlock(Block.quote(parseInline(trimmed.substring(1).trim())));
                i++;
                continue;
            }

            // Divider
            if (trimmed.equals("---") || trimmed.equals("***")) {
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                doc.addBlock(Block.rule());
                i++;
                continue;
            }

            // Lists
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
                if (currentList == null || currentList.isOrdered()) {
                    if (currentList != null) doc.addBlock(currentList);
                    currentList = Block.list(false);
                }
                currentList.addListItem(new Item(parseInline(trimmed.substring(2).trim())));
                i++;
                continue;
            }
            if (trimmed.matches("^\\d+\\.\\s+.*")) {
                if (currentList == null || !currentList.isOrdered()) {
                    if (currentList != null) doc.addBlock(currentList);
                    currentList = Block.list(true);
                }
                currentList.addListItem(new Item(parseInline(trimmed.replaceFirst("^\\d+\\.\\s+", ""))));
                i++;
                continue;
            }

            // Table
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                Block table = Block.table();
                boolean first = true;
                while (i < lines.length && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    String rowStr = lines[i].trim();
                    if (rowStr.matches("^\\|[\\s\\-:\\|]+\\|$")) { i++; continue; }
                    String[] cells = rowStr.split("\\|");
                    List<Cell> row = new ArrayList<>();
                    for (int c = 1; c < cells.length; c++) {
                        row.add(new Cell(parseInline(cells[c].trim()), first));
                    }
                    if (!row.isEmpty()) {
                        table.addTableRow(row);
                        first = false;
                    }
                    i++;
                }
                if (!table.getTableRows().isEmpty()) doc.addBlock(table);
                continue;
            }

            // Regular paragraph
            if (currentList != null) { doc.addBlock(currentList); currentList = null; }
            doc.addBlock(Block.paragraph(parseInline(trimmed)));
            i++;
        }
        if (currentList != null) doc.addBlock(currentList);
        return doc;
    }

    private static Doc parseText(File file) throws Exception {
        Doc doc = new Doc(getBaseName(file));
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        parseTextLinesIntoDoc(text, doc);
        return doc;
    }

    private static Doc parseImageAsDoc(File file) {
        Doc doc = new Doc(getBaseName(file));
        int w = 400, h = 300;
        try {
            BufferedImage bi = ImageIO.read(file);
            if (bi != null) { w = bi.getWidth(); h = bi.getHeight(); }
        } catch (Exception ignored) {}
        doc.addBlock(Block.image(file, w, h));
        return doc;
    }

    private static void parseTextLinesIntoDoc(String text, Doc doc) {
        if (text == null || text.trim().isEmpty()) return;
        String[] lines = text.split("\r?\n");
        StringBuilder currentPara = new StringBuilder();
        Block currentList = null;

        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (currentPara.length() > 0) { doc.addParagraph(currentPara.toString().trim()); currentPara.setLength(0); }
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                continue;
            }
            if (t.startsWith("• ") || t.startsWith("- ") || t.startsWith("* ")) {
                if (currentPara.length() > 0) { doc.addParagraph(currentPara.toString().trim()); currentPara.setLength(0); }
                if (currentList == null || currentList.isOrdered()) {
                    if (currentList != null) doc.addBlock(currentList);
                    currentList = Block.list(false);
                }
                currentList.addListItem(new Item(t.substring(2).trim()));
                continue;
            }
            if (t.matches("^\\d+\\.\\s+.*")) {
                if (currentPara.length() > 0) { doc.addParagraph(currentPara.toString().trim()); currentPara.setLength(0); }
                if (currentList == null || !currentList.isOrdered()) {
                    if (currentList != null) doc.addBlock(currentList);
                    currentList = Block.list(true);
                }
                currentList.addListItem(new Item(t.replaceFirst("^\\d+\\.\\s+", "")));
                continue;
            }
            if (t.length() < 60 && !t.endsWith(".") && !t.endsWith(",") && t.toUpperCase().equals(t) && t.matches(".*[A-Z].*")) {
                if (currentPara.length() > 0) { doc.addParagraph(currentPara.toString().trim()); currentPara.setLength(0); }
                if (currentList != null) { doc.addBlock(currentList); currentList = null; }
                doc.addHeading(1, t);
                continue;
            }
            if (currentPara.length() > 0) currentPara.append(" ");
            currentPara.append(t);
        }
        if (currentPara.length() > 0) doc.addParagraph(currentPara.toString().trim());
        if (currentList != null) doc.addBlock(currentList);
    }

    private static List<Run> parseInline(String text) {
        List<Run> runs = new ArrayList<>();
        if (text == null || text.isEmpty()) return runs;

        Pattern p = Pattern.compile("(`[^`]+`)|(\\*\\*[^\\*]+\\*\\*)|(\\*[^\\*]+\\*)|(\\[[^\\]]+\\]\\([^\\)]+\\))");
        Matcher m = p.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) runs.add(new Run(text.substring(last, m.start())));
            String g = m.group();
            if (g.startsWith("`")) {
                runs.add(new Run(g.substring(1, g.length() - 1)).setMonospace(true));
            } else if (g.startsWith("**")) {
                runs.add(new Run(g.substring(2, g.length() - 2)).setBold(true));
            } else if (g.startsWith("*")) {
                runs.add(new Run(g.substring(1, g.length() - 1)).setItalic(true));
            } else if (g.startsWith("[")) {
                int end = g.indexOf("](");
                runs.add(new Run(g.substring(1, end)).setLinkUrl(g.substring(end + 2, g.length() - 1)).setUnderline(true));
            }
            last = m.end();
        }
        if (last < text.length()) runs.add(new Run(text.substring(last)));
        return runs;
    }

    // =========================================================================
    // 2. DOCUMENT RENDERERS
    // =========================================================================

    public static void render(Doc doc, File out, Format fmt, String ext, ImageEngine.Options imgOpts, Float margin) throws Exception {
        if (fmt == Format.PDF || "pdf".equalsIgnoreCase(ext)) renderPdf(doc, out, margin);
        else if (fmt == Format.DOCX || "docx".equalsIgnoreCase(ext)) renderDocx(doc, out);
        else if (fmt == Format.ODT || "odt".equalsIgnoreCase(ext)) renderOdt(doc, out);
        else if (fmt == Format.HTML || "html".equalsIgnoreCase(ext) || "htm".equalsIgnoreCase(ext)) renderHtml(doc, out);
        else if (fmt == Format.MD || "md".equalsIgnoreCase(ext) || "markdown".equalsIgnoreCase(ext)) renderMarkdown(doc, out);
        else if (fmt == Format.DOC || "doc".equalsIgnoreCase(ext)) renderDoc(doc, out);
        else if (fmt != null && fmt.isImage()) renderDocAsImage(doc, out, fmt, imgOpts);
        else renderText(doc, out);
    }

    // --- PDFBox Renderer ---
    private static void renderPdf(Doc doc, File out, Float customMargin) throws Exception {
        float pageWidth = PDRectangle.LETTER.getWidth();
        float pageHeight = PDRectangle.LETTER.getHeight();
        float m = customMargin != null ? customMargin : 45f;
        float cw = pageWidth - (m * 2);

        PDType1Font fReg = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font fBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fItal = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        PDType1Font fMono = new PDType1Font(Standard14Fonts.FontName.COURIER);

        try (PDDocument pdf = new PDDocument()) {
            if (doc.getTitle() != null) pdf.getDocumentInformation().setTitle(doc.getTitle());
            if (doc.getAuthor() != null) pdf.getDocumentInformation().setAuthor(doc.getAuthor());

            List<PDPage> pages = new ArrayList<>();
            PDPage page = new PDPage(PDRectangle.LETTER);
            pdf.addPage(page);
            pages.add(page);
            PDPageContentStream cs = new PDPageContentStream(pdf, page);
            float y = pageHeight - m - 15f;

            for (Block b : doc.getBlocks()) {
                if (b.getType() == Block.Type.HEADING) {
                    float sz = b.getLevel() == 1 ? 20f : (b.getLevel() == 2 ? 15f : 12f);
                    y -= (b.getLevel() == 1 ? 16f : 10f);
                    if (y < m + 30f) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                        cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                    }
                    cs.setFont(fBold, sz);
                    cs.setNonStrokingColor(0.12f, 0.23f, 0.36f);
                    for (String line : wrap(sanitize(b.getText()), fBold, sz, cw)) {
                        cs.beginText(); cs.newLineAtOffset(m, y); cs.showText(line); cs.endText();
                        y -= (sz * 1.25f);
                    }
                    if (b.getLevel() == 1) {
                        y -= 2f;
                        cs.setStrokingColor(0.8f, 0.85f, 0.9f); cs.setLineWidth(1.2f);
                        cs.moveTo(m, y); cs.lineTo(m + cw, y); cs.stroke();
                        y -= 6f;
                    }
                } else if (b.getType() == Block.Type.PARAGRAPH) {
                    float sz = 10.5f, lead = 14f;
                    List<String> lines = wrap(sanitize(b.getText()), fReg, sz, cw);
                    if (y - (lines.size() * lead) < m + 20f) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                        cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                    }
                    cs.setFont(fReg, sz);
                    cs.setNonStrokingColor(0.15f, 0.15f, 0.15f);
                    for (String l : lines) {
                        cs.beginText(); cs.newLineAtOffset(m, y); cs.showText(l); cs.endText();
                        y -= lead;
                    }
                    y -= 6f;
                } else if (b.getType() == Block.Type.CODE) {
                    float sz = 8.5f, lead = 11f, pad = 8f;
                    String[] lines = sanitize(b.getCode()).split("\n");
                    float bh = (lines.length * lead) + (pad * 2);
                    if (y - Math.min(bh, 100f) < m + 20f) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                        cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                    }
                    float actualH = Math.min(bh, y - m);
                    cs.setNonStrokingColor(0.96f, 0.97f, 0.98f);
                    cs.addRect(m, y - actualH, cw, actualH);
                    cs.fill();
                    cs.setStrokingColor(0.85f, 0.88f, 0.91f); cs.setLineWidth(0.8f);
                    cs.addRect(m, y - actualH, cw, actualH);
                    cs.stroke();

                    float textY = y - pad - sz;
                    cs.setFont(fMono, sz);
                    cs.setNonStrokingColor(0.2f, 0.23f, 0.28f);
                    for (String l : lines) {
                        if (textY < m + 20f) {
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                            cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                            textY = y - pad - sz; cs.setFont(fMono, sz); cs.setNonStrokingColor(0.2f, 0.23f, 0.28f);
                        }
                        for (String wl : wrap(l, fMono, sz, cw - (pad * 2))) {
                            cs.beginText(); cs.newLineAtOffset(m + pad, textY); cs.showText(wl); cs.endText();
                            textY -= lead;
                        }
                    }
                    y = textY - pad - 6f;
                } else if (b.getType() == Block.Type.LIST) {
                    float sz = 10f, lead = 13f, ind = 18f;
                    int idx = 1;
                    for (Item it : b.getListItems()) {
                        String bullet = b.isOrdered() ? (idx++ + ". ") : "• ";
                        List<String> lines = wrap(sanitize(it.getText()), fReg, sz, cw - ind);
                        if (y - (lines.size() * lead) < m + 20f) {
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                            cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                        }
                        cs.setFont(fBold, sz); cs.setNonStrokingColor(0.25f, 0.35f, 0.5f);
                        cs.beginText(); cs.newLineAtOffset(m + 4f, y); cs.showText(bullet); cs.endText();
                        cs.setFont(fReg, sz); cs.setNonStrokingColor(0.15f, 0.15f, 0.15f);
                        for (String l : lines) {
                            cs.beginText(); cs.newLineAtOffset(m + ind, y); cs.showText(l); cs.endText();
                            y -= lead;
                        }
                        y -= 2f;
                    }
                    y -= 4f;
                } else if (b.getType() == Block.Type.TABLE) {
                    if (!b.getTableRows().isEmpty()) {
                        int cols = b.getTableRows().get(0).size();
                        if (cols > 0) {
                            float colW = cw / cols, pad = 6f, sz = 9.5f, lead = 12f;
                            for (List<Cell> row : b.getTableRows()) {
                                int maxL = 1;
                                List<List<String>> rowW = new ArrayList<>();
                                for (Cell c : row) {
                                    List<String> w = wrap(sanitize(c.getText()), c.isHeader() ? fBold : fReg, sz, colW - (pad * 2));
                                    if (w.isEmpty()) w.add("");
                                    if (w.size() > maxL) maxL = w.size();
                                    rowW.add(w);
                                }
                                float rh = (maxL * lead) + (pad * 2);
                                if (y - rh < m + 20f) {
                                    cs.close();
                                    page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                                    cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                                }
                                boolean isH = row.get(0).isHeader();
                                cs.setNonStrokingColor(isH ? 0.92f : 0.99f, isH ? 0.94f : 0.99f, isH ? 0.97f : 0.99f);
                                cs.addRect(m, y - rh, cw, rh); cs.fill();

                                float cx = m;
                                for (int ci = 0; ci < row.size(); ci++) {
                                    PDFont f = row.get(ci).isHeader() ? fBold : fReg;
                                    cs.setFont(f, sz);
                                    cs.setNonStrokingColor(row.get(ci).isHeader() ? 0.1f : 0.2f, row.get(ci).isHeader() ? 0.2f : 0.2f, row.get(ci).isHeader() ? 0.35f : 0.2f);
                                    float ly = y - pad - sz;
                                    for (String l : rowW.get(ci)) {
                                        cs.beginText(); cs.newLineAtOffset(cx + pad, ly); cs.showText(l); cs.endText();
                                        ly -= lead;
                                    }
                                    cs.setStrokingColor(0.85f, 0.88f, 0.91f); cs.setLineWidth(0.6f);
                                    cs.moveTo(cx, y); cs.lineTo(cx, y - rh); cs.stroke();
                                    cx += colW;
                                }
                                cs.moveTo(m + cw, y); cs.lineTo(m + cw, y - rh);
                                cs.moveTo(m, y - rh); cs.lineTo(m + cw, y - rh); cs.stroke();
                                y -= rh;
                            }
                            y -= 8f;
                        }
                    }
                } else if (b.getType() == Block.Type.QUOTE) {
                    float sz = 10f, lead = 13.5f, ind = 16f;
                    List<String> lines = wrap(sanitize(b.getText()), fItal, sz, cw - ind);
                    float qh = lines.size() * lead;
                    if (y - qh < m + 20f) {
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER); pdf.addPage(page); pages.add(page);
                        cs = new PDPageContentStream(pdf, page); y = pageHeight - m - 15f;
                    }
                    cs.setStrokingColor(0.23f, 0.51f, 0.96f); cs.setLineWidth(3f);
                    cs.moveTo(m + 2f, y); cs.lineTo(m + 2f, y - qh); cs.stroke();
                    cs.setFont(fItal, sz); cs.setNonStrokingColor(0.35f, 0.38f, 0.42f);
                    for (String l : lines) {
                        cs.beginText(); cs.newLineAtOffset(m + ind, y - 2f); cs.showText(l); cs.endText();
                        y -= lead;
                    }
                    y -= 8f;
                } else if (b.getType() == Block.Type.RULE) {
                    y -= 6f;
                    cs.setStrokingColor(0.85f, 0.88f, 0.91f); cs.setLineWidth(0.8f);
                    cs.moveTo(m, y); cs.lineTo(m + cw, y); cs.stroke();
                    y -= 8f;
                }
            }
            cs.close();

            // Page numbers
            int tot = pages.size();
            for (int pIdx = 0; pIdx < tot; pIdx++) {
                try (PDPageContentStream fcs = new PDPageContentStream(pdf, pages.get(pIdx), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String foot = String.format("Page %d of %d", pIdx + 1, tot);
                    float fw = fReg.getStringWidth(foot) / 1000f * 8.5f;
                    fcs.setFont(fReg, 8.5f); fcs.setNonStrokingColor(0.55f, 0.58f, 0.62f);
                    fcs.beginText(); fcs.newLineAtOffset((pageWidth - fw) / 2.0f, m - 15f); fcs.showText(foot); fcs.endText();
                }
            }
            pdf.save(out);
        }
    }

    // --- DOCX Renderer (Apache POI) ---
    private static void renderDocx(Doc doc, File out) throws Exception {
        try (XWPFDocument docx = new XWPFDocument()) {
            if (doc.getTitle() != null) docx.getProperties().getCoreProperties().setTitle(doc.getTitle());
            if (doc.getAuthor() != null) docx.getProperties().getCoreProperties().setCreator(doc.getAuthor());

            for (Block b : doc.getBlocks()) {
                if (b.getType() == Block.Type.HEADING) {
                    XWPFParagraph p = docx.createParagraph();
                    p.setSpacingBefore(b.getLevel() == 1 ? 240 : 160);
                    p.setSpacingAfter(b.getLevel() == 1 ? 120 : 80);
                    XWPFRun r = p.createRun();
                    r.setText(b.getText());
                    r.setBold(true);
                    r.setFontSize(b.getLevel() == 1 ? 20 : (b.getLevel() == 2 ? 16 : 13));
                    r.setColor(b.getLevel() == 1 ? "1F497D" : "333333");
                } else if (b.getType() == Block.Type.PARAGRAPH) {
                    XWPFParagraph p = docx.createParagraph();
                    p.setSpacingAfter(100);
                    writeRunsToDocx(p, b.getRuns(), b.getText());
                } else if (b.getType() == Block.Type.CODE) {
                    XWPFTable t = docx.createTable(1, 1);
                    t.setWidth("100%");
                    XWPFTableCell c = t.getRow(0).getCell(0);
                    c.setColor("F4F6F8");
                    XWPFParagraph p = c.getParagraphArray(0);
                    String[] lines = b.getCode().split("\n");
                    for (int li = 0; li < lines.length; li++) {
                        XWPFRun r = p.createRun();
                        r.setFontFamily("Consolas"); r.setFontSize(9); r.setText(lines[li]);
                        if (li < lines.length - 1) r.addBreak();
                    }
                    docx.createParagraph().setSpacingAfter(80);
                } else if (b.getType() == Block.Type.LIST) {
                    int num = 1;
                    for (Item it : b.getListItems()) {
                        XWPFParagraph p = docx.createParagraph();
                        p.setIndentationLeft(360); p.setSpacingAfter(40);
                        XWPFRun pr = p.createRun(); pr.setBold(true);
                        pr.setText(b.isOrdered() ? (num++ + ".  ") : "•   ");
                        writeRunsToDocx(p, it.getRuns(), it.getText());
                    }
                } else if (b.getType() == Block.Type.TABLE) {
                    if (!b.getTableRows().isEmpty()) {
                        XWPFTable t = docx.createTable();
                        t.setWidth("100%");
                        boolean first = true;
                        for (List<Cell> row : b.getTableRows()) {
                            XWPFTableRow tr = first ? t.getRow(0) : t.createRow();
                            first = false;
                            for (int ci = 0; ci < row.size(); ci++) {
                                Cell cell = row.get(ci);
                                XWPFTableCell tc = (ci < tr.getTableCells().size()) ? tr.getCell(ci) : tr.addNewTableCell();
                                if (cell.isHeader()) tc.setColor("EAECEF");
                                XWPFParagraph p = tc.getParagraphArray(0);
                                p.setSpacingBefore(40); p.setSpacingAfter(40);
                                writeRunsToDocx(p, cell.getRuns(), cell.getText());
                            }
                        }
                        docx.createParagraph().setSpacingAfter(80);
                    }
                } else if (b.getType() == Block.Type.QUOTE) {
                    XWPFParagraph p = docx.createParagraph();
                    p.setIndentationLeft(720); p.setSpacingBefore(60); p.setSpacingAfter(60);
                    for (Run r : b.getRuns()) {
                        XWPFRun xr = p.createRun();
                        xr.setText(r.getText()); xr.setItalic(true); xr.setColor("555555");
                    }
                } else if (b.getType() == Block.Type.RULE) {
                    docx.createParagraph().setBorderBottom(Borders.SINGLE);
                }
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                docx.write(fos);
            }
        }
    }

    private static void writeRunsToDocx(XWPFParagraph p, List<Run> runs, String fallback) {
        if (runs == null || runs.isEmpty()) {
            XWPFRun r = p.createRun(); r.setText(fallback); r.setFontSize(11); return;
        }
        for (Run r : runs) {
            XWPFRun xr = p.createRun();
            xr.setText(r.getText());
            if (r.isBold()) xr.setBold(true);
            if (r.isItalic()) xr.setItalic(true);
            if (r.isUnderline()) xr.setUnderline(UnderlinePatterns.SINGLE);
            if (r.isMonospace()) xr.setFontFamily("Courier New");
            xr.setFontSize(11);
        }
    }

    // --- ODT Renderer ---
    private static void renderOdt(Doc doc, File out) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(out);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            byte[] mime = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            ZipEntry me = new ZipEntry("mimetype");
            me.setMethod(ZipEntry.STORED); me.setSize(mime.length); me.setCompressedSize(mime.length);
            CRC32 crc = new CRC32(); crc.update(mime); me.setCrc(crc.getValue());
            zos.putNextEntry(me); zos.write(mime); zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/manifest.xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\"><manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.2\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/><manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/><manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/></manifest:manifest>").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("styles.xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-styles xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\" office:version=\"1.2\"><office:styles><style:default-style style:family=\"paragraph\"><style:text-properties fo:font-size=\"11pt\"/></style:default-style></office:styles></office:document-styles>").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" office:version=\"1.2\"><office:body><office:text>\n");

            for (Block b : doc.getBlocks()) {
                if (b.getType() == Block.Type.HEADING) {
                    sb.append("<text:h text:outline-level=\"").append(b.getLevel()).append("\">").append(escapeXml(b.getText())).append("</text:h>\n");
                } else if (b.getType() == Block.Type.PARAGRAPH) {
                    sb.append("<text:p>").append(escapeXml(b.getText())).append("</text:p>\n");
                } else if (b.getType() == Block.Type.CODE) {
                    for (String l : b.getCode().split("\n")) sb.append("<text:p>").append(escapeXml(l)).append("</text:p>\n");
                } else if (b.getType() == Block.Type.LIST) {
                    sb.append("<text:list>\n");
                    for (Item it : b.getListItems()) sb.append("<text:list-item><text:p>").append(escapeXml(it.getText())).append("</text:p></text:list-item>\n");
                    sb.append("</text:list>\n");
                } else if (b.getType() == Block.Type.TABLE) {
                    sb.append("<table:table table:name=\"Table1\">\n");
                    for (List<Cell> row : b.getTableRows()) {
                        sb.append("<table:table-row>\n");
                        for (Cell c : row) sb.append("<table:table-cell office:value-type=\"string\"><text:p>").append(escapeXml(c.getText())).append("</text:p></table:table-cell>\n");
                        sb.append("</table:table-row>\n");
                    }
                    sb.append("</table:table>\n");
                }
            }
            sb.append("</office:text></office:body></office:document-content>");

            zos.putNextEntry(new ZipEntry("content.xml"));
            zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    // --- HTML Renderer ---
    private static void renderHtml(Doc doc, File out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><title>").append(escapeHtml(doc.getTitle() != null ? doc.getTitle() : "Document")).append("</title>\n");
        sb.append("<style>body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;line-height:1.6;color:#1f2328;max-width:840px;margin:2rem auto;padding:0 1rem;}h1,h2,h3{color:#1f2328;}h1{border-bottom:1px solid #d0d7de;padding-bottom:.3em;}pre{background:#f6f8fa;padding:1rem;border-radius:6px;overflow-x:auto;border:1px solid #d0d7de;}code{font-family:monospace;background:#f6f8fa;padding:.2em .4em;border-radius:4px;}table{width:100%;border-collapse:collapse;margin:1rem 0;}th,td{border:1px solid #d0d7de;padding:8px 12px;}th{background:#f6f8fa;}blockquote{border-left:3px solid #0969da;margin:1rem 0;padding-left:1rem;color:#57606a;}</style></head><body>\n");

        for (Block b : doc.getBlocks()) {
            if (b.getType() == Block.Type.HEADING) {
                sb.append("<h").append(b.getLevel()).append(">").append(escapeHtml(b.getText())).append("</h").append(b.getLevel()).append(">\n");
            } else if (b.getType() == Block.Type.PARAGRAPH) {
                sb.append("<p>").append(escapeHtml(b.getText())).append("</p>\n");
            } else if (b.getType() == Block.Type.CODE) {
                sb.append("<pre><code>").append(escapeHtml(b.getCode())).append("</code></pre>\n");
            } else if (b.getType() == Block.Type.LIST) {
                sb.append(b.isOrdered() ? "<ol>\n" : "<ul>\n");
                for (Item it : b.getListItems()) sb.append("<li>").append(escapeHtml(it.getText())).append("</li>\n");
                sb.append(b.isOrdered() ? "</ol>\n" : "</ul>\n");
            } else if (b.getType() == Block.Type.TABLE) {
                sb.append("<table>\n");
                boolean first = true;
                for (List<Cell> row : b.getTableRows()) {
                    sb.append("<tr>\n");
                    for (Cell c : row) {
                        String t = (c.isHeader() || first) ? "th" : "td";
                        sb.append("<").append(t).append(">").append(escapeHtml(c.getText())).append("</").append(t).append(">\n");
                    }
                    sb.append("</tr>\n");
                    first = false;
                }
                sb.append("</table>\n");
            } else if (b.getType() == Block.Type.QUOTE) {
                sb.append("<blockquote>").append(escapeHtml(b.getText())).append("</blockquote>\n");
            } else if (b.getType() == Block.Type.RULE) {
                sb.append("<hr>\n");
            }
        }
        sb.append("</body></html>");
        Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    // --- Markdown Renderer ---
    private static void renderMarkdown(Doc doc, File out) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Block b : doc.getBlocks()) {
            if (b.getType() == Block.Type.HEADING) {
                sb.append("#".repeat(b.getLevel())).append(" ").append(b.getText()).append("\n\n");
            } else if (b.getType() == Block.Type.PARAGRAPH) {
                sb.append(b.getText()).append("\n\n");
            } else if (b.getType() == Block.Type.CODE) {
                String l = b.getLanguage() != null ? b.getLanguage() : "";
                sb.append("```").append(l).append("\n").append(b.getCode()).append("\n```\n\n");
            } else if (b.getType() == Block.Type.LIST) {
                int num = 1;
                for (Item it : b.getListItems()) {
                    sb.append(b.isOrdered() ? (num++ + ". ") : "- ").append(it.getText()).append("\n");
                }
                sb.append("\n");
            } else if (b.getType() == Block.Type.TABLE) {
                if (!b.getTableRows().isEmpty()) {
                    List<Cell> h = b.getTableRows().get(0);
                    sb.append("|"); for (Cell c : h) sb.append(" ").append(c.getText()).append(" |"); sb.append("\n|");
                    for (int i = 0; i < h.size(); i++) sb.append(" --- |"); sb.append("\n");
                    for (int r = 1; r < b.getTableRows().size(); r++) {
                        sb.append("|"); for (Cell c : b.getTableRows().get(r)) sb.append(" ").append(c.getText()).append(" |"); sb.append("\n");
                    }
                    sb.append("\n");
                }
            } else if (b.getType() == Block.Type.QUOTE) {
                sb.append("> ").append(b.getText()).append("\n\n");
            } else if (b.getType() == Block.Type.RULE) {
                sb.append("---\n\n");
            }
        }
        Files.writeString(out.toPath(), sb.toString().trim() + "\n", StandardCharsets.UTF_8);
    }

    // --- Plain Text Renderer ---
    private static void renderText(Doc doc, File out) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Block b : doc.getBlocks()) {
            if (b.getType() == Block.Type.HEADING) {
                String t = b.getText().trim();
                sb.append(t).append("\n").append((b.getLevel() == 1 ? "=" : "-").repeat(t.length())).append("\n\n");
            } else if (b.getType() == Block.Type.PARAGRAPH) {
                sb.append(b.getText().trim()).append("\n\n");
            } else if (b.getType() == Block.Type.CODE) {
                sb.append(b.getCode()).append("\n\n");
            } else if (b.getType() == Block.Type.LIST) {
                int num = 1;
                for (Item it : b.getListItems()) {
                    sb.append("  ").append(b.isOrdered() ? (num++ + ". ") : "• ").append(it.getText().trim()).append("\n");
                }
                sb.append("\n");
            } else if (b.getType() == Block.Type.TABLE) {
                for (List<Cell> r : b.getTableRows()) {
                    sb.append("| "); for (Cell c : r) sb.append(c.getText().trim()).append(" | "); sb.append("\n");
                }
                sb.append("\n");
            }
        }
        Files.writeString(out.toPath(), sb.toString().trim() + "\n", StandardCharsets.UTF_8);
    }

    // --- Word DOC (HTML-based compatible structure) ---
    private static void renderDoc(Doc doc, File out) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>\n");
        sb.append("<head><meta charset='utf-8'><title>").append(escapeHtml(doc.getTitle() != null ? doc.getTitle() : "Document")).append("</title>\n");
        sb.append("<style>body{font-family:'Calibri',sans-serif;font-size:11pt;color:#222;}h1{font-size:18pt;color:#1F497D;}table{border-collapse:collapse;width:100%;}th,td{border:1px solid #ccc;padding:6px;}</style></head><body>\n");
        for (Block b : doc.getBlocks()) {
            if (b.getType() == Block.Type.HEADING) sb.append("<h").append(b.getLevel()).append(">").append(escapeHtml(b.getText())).append("</h").append(b.getLevel()).append(">\n");
            else if (b.getType() == Block.Type.PARAGRAPH) sb.append("<p>").append(escapeHtml(b.getText())).append("</p>\n");
            else if (b.getType() == Block.Type.CODE) sb.append("<pre><code>").append(escapeHtml(b.getCode())).append("</code></pre>\n");
            else if (b.getType() == Block.Type.LIST) {
                sb.append(b.isOrdered() ? "<ol>" : "<ul>");
                for (Item it : b.getListItems()) sb.append("<li>").append(escapeHtml(it.getText())).append("</li>");
                sb.append(b.isOrdered() ? "</ol>" : "</ul>");
            }
        }
        sb.append("</body></html>");
        Files.writeString(out.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    // --- Document to Image(s) ---
    private static void renderDocAsImage(Doc doc, File out, Format targetFmt, ImageEngine.Options imgOpts) throws Exception {
        File temp = File.createTempFile("conve_doc_tmp_", ".pdf");
        try {
            renderPdf(doc, temp, 45f);
            ImageEngine.convertPdfToImages(temp, out, targetFmt, imgOpts);
        } finally {
            temp.delete();
        }
    }

    // =========================================================================
    // 3. UTILITY METHODS
    // =========================================================================

    private static List<String> wrap(String text, PDFont font, float sz, float maxW) throws IOException {
        List<String> res = new ArrayList<>();
        if (text == null || text.isEmpty()) return res;
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (font.getStringWidth(sanitize(test)) / 1000f * sz > maxW && line.length() > 0) {
                res.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) res.add(line.toString());
        return res;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c <= 126) sb.append(c);
            else if (c == '\t') sb.append("    ");
            else if (c == 0x2022 || c == 0x25E6) sb.append("*");
            else if (c == 0x2018 || c == 0x2019) sb.append("'");
            else if (c == 0x201C || c == 0x201D) sb.append("\"");
            else if (c == 0x2013 || c == 0x2014) sb.append("-");
            else if (c >= 128 && c <= 255) sb.append(c);
            else sb.append(" ");
        }
        return sb.toString();
    }

    private static String cleanHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").trim();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String getExt(File f) {
        String n = f.getName();
        int d = n.lastIndexOf('.');
        return (d >= 0) ? n.substring(d + 1).toLowerCase() : "";
    }

    private static String getBaseName(File f) {
        String n = f.getName();
        int d = n.lastIndexOf('.');
        return (d >= 0) ? n.substring(0, d) : n;
    }
}
