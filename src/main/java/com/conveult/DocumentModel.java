package com.conveult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified intermediate document model for cross-format document conversion.
 */
public class DocumentModel {

    // --- Document Container ---
    public static class Doc {
        private String title;
        private String author;
        private String subject;
        private int pageCount = 0;
        private final List<Block> blocks = new ArrayList<>();

        public Doc() {}

        public Doc(String title) {
            this.title = title;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public int getPageCount() { return pageCount; }
        public void setPageCount(int pageCount) { this.pageCount = pageCount; }

        public List<Block> getBlocks() { return blocks; }

        public Doc addBlock(Block block) {
            if (block != null) blocks.add(block);
            return this;
        }

        public Doc addHeading(int level, String text) {
            blocks.add(Block.heading(level, text));
            return this;
        }

        public Doc addParagraph(String text) {
            blocks.add(Block.paragraph(text));
            return this;
        }

        public Doc addCodeBlock(String code, String lang) {
            blocks.add(Block.code(code, lang));
            return this;
        }

        public int getWordCount() {
            int words = 0;
            for (Block b : blocks) {
                String s = b.getText();
                if (s != null && !s.trim().isEmpty()) {
                    words += s.trim().split("\\s+").length;
                }
            }
            return words;
        }
    }

    // --- Block Element ---
    public static class Block {
        public enum Type {
            HEADING, PARAGRAPH, CODE, LIST, TABLE, QUOTE, RULE, PAGE_BREAK, IMAGE
        }

        private final Type type;
        private int level = 1;                    // For headings (1-6)
        private final List<Run> runs = new ArrayList<>(); // For headings, paragraphs, quotes
        private String code;                      // For code blocks
        private String language;                  // For code blocks
        private boolean ordered = false;          // For lists
        private final List<Item> listItems = new ArrayList<>(); // For lists
        private final List<List<Cell>> tableRows = new ArrayList<>(); // For tables
        private File imageFile;                   // For images
        private int imageWidth;
        private int imageHeight;

        private Block(Type type) {
            this.type = type;
        }

        public static Block heading(int level, String text) {
            Block b = new Block(Type.HEADING);
            b.level = Math.max(1, Math.min(6, level));
            b.runs.add(new Run(text).setBold(true));
            return b;
        }

        public static Block heading(int level, List<Run> runs) {
            Block b = new Block(Type.HEADING);
            b.level = Math.max(1, Math.min(6, level));
            if (runs != null) b.runs.addAll(runs);
            return b;
        }

        public static Block paragraph(String text) {
            Block b = new Block(Type.PARAGRAPH);
            if (text != null && !text.isEmpty()) b.runs.add(new Run(text));
            return b;
        }

        public static Block paragraph(List<Run> runs) {
            Block b = new Block(Type.PARAGRAPH);
            if (runs != null) b.runs.addAll(runs);
            return b;
        }

        public static Block code(String code, String lang) {
            Block b = new Block(Type.CODE);
            b.code = code != null ? code : "";
            b.language = lang;
            return b;
        }

        public static Block quote(List<Run> runs) {
            Block b = new Block(Type.QUOTE);
            if (runs != null) b.runs.addAll(runs);
            return b;
        }

        public static Block list(boolean ordered) {
            Block b = new Block(Type.LIST);
            b.ordered = ordered;
            return b;
        }

        public static Block table() {
            return new Block(Type.TABLE);
        }

        public static Block rule() {
            return new Block(Type.RULE);
        }

        public static Block pageBreak() {
            return new Block(Type.PAGE_BREAK);
        }

        public static Block image(File file, int w, int h) {
            Block b = new Block(Type.IMAGE);
            b.imageFile = file;
            b.imageWidth = w;
            b.imageHeight = h;
            return b;
        }

        public Type getType() { return type; }
        public int getLevel() { return level; }
        public List<Run> getRuns() { return runs; }
        public String getCode() { return code; }
        public String getLanguage() { return language; }
        public boolean isOrdered() { return ordered; }
        public List<Item> getListItems() { return listItems; }
        public List<List<Cell>> getTableRows() { return tableRows; }
        public File getImageFile() { return imageFile; }
        public int getImageWidth() { return imageWidth; }
        public int getImageHeight() { return imageHeight; }

        public Block addListItem(Item item) {
            if (item != null) listItems.add(item);
            return this;
        }

        public Block addTableRow(List<Cell> row) {
            if (row != null) tableRows.add(row);
            return this;
        }

        public String getText() {
            if (type == Type.CODE) return code;
            if (type == Type.LIST) {
                StringBuilder sb = new StringBuilder();
                for (Item it : listItems) sb.append(it.getText()).append(" ");
                return sb.toString();
            }
            if (type == Type.TABLE) {
                StringBuilder sb = new StringBuilder();
                for (List<Cell> row : tableRows) {
                    for (Cell c : row) sb.append(c.getText()).append(" ");
                }
                return sb.toString();
            }
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) sb.append(r.getText());
            return sb.toString();
        }
    }

    // --- Styled Text Run ---
    public static class Run {
        private String text;
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private boolean monospace;
        private String color;
        private String linkUrl;

        public Run(String text) {
            this.text = text != null ? text : "";
        }

        public String getText() { return text; }
        public Run setText(String text) { this.text = text; return this; }

        public boolean isBold() { return bold; }
        public Run setBold(boolean bold) { this.bold = bold; return this; }

        public boolean isItalic() { return italic; }
        public Run setItalic(boolean italic) { this.italic = italic; return this; }

        public boolean isUnderline() { return underline; }
        public Run setUnderline(boolean underline) { this.underline = underline; return this; }

        public boolean isMonospace() { return monospace; }
        public Run setMonospace(boolean monospace) { this.monospace = monospace; return this; }

        public String getColor() { return color; }
        public Run setColor(String color) { this.color = color; return this; }

        public String getLinkUrl() { return linkUrl; }
        public Run setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; return this; }
    }

    // --- List Item ---
    public static class Item {
        private final List<Run> runs = new ArrayList<>();

        public Item(String text) {
            if (text != null && !text.isEmpty()) runs.add(new Run(text));
        }

        public Item(List<Run> runs) {
            if (runs != null) this.runs.addAll(runs);
        }

        public List<Run> getRuns() { return runs; }

        public String getText() {
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) sb.append(r.getText());
            return sb.toString();
        }
    }

    // --- Table Cell ---
    public static class Cell {
        private final List<Run> runs = new ArrayList<>();
        private boolean header;

        public Cell(String text, boolean header) {
            this.header = header;
            if (text != null) {
                Run r = new Run(text);
                if (header) r.setBold(true);
                runs.add(r);
            }
        }

        public Cell(List<Run> runs, boolean header) {
            this.header = header;
            if (runs != null) this.runs.addAll(runs);
        }

        public boolean isHeader() { return header; }
        public List<Run> getRuns() { return runs; }

        public String getText() {
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) sb.append(r.getText());
            return sb.toString();
        }
    }
}
