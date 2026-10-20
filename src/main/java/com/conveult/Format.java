package com.conveult;

import java.io.File;
import java.util.*;

/**
 * Registry of supported image and document file formats.
 */
public enum Format {
    // Image formats
    PNG(Kind.IMAGE, "image/png", "png"),
    JPG(Kind.IMAGE, "image/jpeg", "jpg", "jpeg", "jpe", "jfif"),
    WEBP(Kind.IMAGE, "image/webp", "webp"),
    GIF(Kind.IMAGE, "image/gif", "gif"),
    BMP(Kind.IMAGE, "image/bmp", "bmp"),
    TIFF(Kind.IMAGE, "image/tiff", "tiff", "tif"),
    WBMP(Kind.IMAGE, "image/vnd.wap.wbmp", "wbmp"),

    // Document formats
    PDF(Kind.DOCUMENT, "application/pdf", "pdf"),
    DOCX(Kind.DOCUMENT, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    DOC(Kind.DOCUMENT, "application/msword", "doc"),
    ODT(Kind.DOCUMENT, "application/vnd.oasis.opendocument.text", "odt"),
    HTML(Kind.DOCUMENT, "text/html", "html", "htm"),
    MD(Kind.DOCUMENT, "text/markdown", "md", "markdown"),
    TXT(Kind.DOCUMENT, "text/plain", "txt", "text", "log"),
    RTF(Kind.DOCUMENT, "application/rtf", "rtf");

    public enum Kind {
        IMAGE,
        DOCUMENT
    }

    private final Kind kind;
    private final String mimeType;
    private final String primaryExt;
    private final Set<String> allExts;

    Format(Kind kind, String mimeType, String primaryExt, String... aliases) {
        this.kind = kind;
        this.mimeType = mimeType;
        this.primaryExt = primaryExt.toLowerCase(Locale.ROOT);
        Set<String> set = new HashSet<>();
        set.add(this.primaryExt);
        for (String a : aliases) {
            set.add(a.toLowerCase(Locale.ROOT));
        }
        this.allExts = Collections.unmodifiableSet(set);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isImage() {
        return kind == Kind.IMAGE;
    }

    public boolean isDocument() {
        return kind == Kind.DOCUMENT;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getPrimaryExtension() {
        return primaryExt;
    }

    public Set<String> getExtensions() {
        return allExts;
    }

    public static Format fromExtension(String ext) {
        if (ext == null || ext.trim().isEmpty()) return null;
        String clean = ext.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith(".")) clean = clean.substring(1);
        for (Format f : values()) {
            if (f.allExts.contains(clean)) return f;
        }
        return null;
    }

    public static Format fromFile(File file) {
        if (file == null) return null;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return fromExtension(name.substring(dot + 1));
    }
}
