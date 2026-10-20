package com.conveult;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * conve-ult: Universal CLI File Converter
 * Powered by Java, Apache POI 5.2.5, and Apache PDFBox 3.0.1.
 */
public class ConveUlt {

    static {
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF");
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.OFF);
        Logger.getLogger("org.apache.fontbox").setLevel(Level.OFF);
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0 || hasFlag(args, "-h", "--help", "help")) {
            printHelp();
            return;
        }
        if (hasFlag(args, "-V", "--version", "version")) {
            printVersion();
            return;
        }
        if (hasFlag(args, "formats", "list-formats")) {
            printFormats();
            return;
        }

        try {
            if ("info".equalsIgnoreCase(args[0])) {
                handleInfo(args);
            } else if ("batch".equalsIgnoreCase(args[0])) {
                handleBatch(args);
            } else {
                handleConvert(args);
            }
        } catch (IllegalArgumentException e) {
            printError(e.getMessage());
            System.err.println("Run 'conve-ult --help' for usage guidance.");
            System.exit(1);
        } catch (Exception e) {
            printError("Conversion failed: " + e.getMessage());
            if (System.getProperty("debug") != null) e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================================================================
    // 1. COMMAND HANDLERS
    // =========================================================================

    private static void handleConvert(String[] args) throws Exception {
        Map<String, String> opts = parseFlags(args);
        List<String> pos = parsePositional(args);

        if (pos.isEmpty()) {
            printHelp();
            return;
        }

        File in = new File(pos.get(0));
        if (!in.exists()) throw new IllegalArgumentException("Input file not found: " + in.getPath());

        Format inFmt = Format.fromFile(in);
        String inExt = getExt(in);

        // Resolve output file and target format
        String targetExt = opts.get("to");
        Format targetFmt = targetExt != null ? Format.fromExtension(targetExt) : null;

        File out = null;
        if (opts.containsKey("output")) {
            out = new File(opts.get("output"));
        } else if (pos.size() > 1) {
            out = new File(pos.get(1));
        }

        if (out == null) {
            if (targetFmt == null && targetExt == null) {
                throw new IllegalArgumentException("Please specify an output file (-o <file>) or target format (--to <ext>)");
            }
            String base = getBaseName(in);
            String ext = targetFmt != null ? targetFmt.getPrimaryExtension() : targetExt;
            out = in.getParentFile() != null ? new File(in.getParentFile(), base + "." + ext) : new File(base + "." + ext);
        }

        if (targetFmt == null) {
            targetFmt = Format.fromFile(out);
        }

        boolean force = opts.containsKey("force");
        if (out.exists() && !force) {
            printWarn("Output file already exists: " + out.getName() + ". Use -f or --force to overwrite.");
            System.exit(2);
        }

        System.out.println(color("✦ Converting: ", "\u001B[1;36m") + in.getName() + " -> " + out.getName());

        long start = System.currentTimeMillis();
        long origLen = in.length();

        ImageEngine.Options imgOpts = extractImageOptions(opts);
        String title = opts.get("title");
        String author = opts.get("author");
        Float margin = opts.containsKey("margin") ? Float.parseFloat(opts.get("margin")) : null;

        String outExt = getExt(out);
        boolean isImageOp = (inFmt != null && inFmt.isImage() && (targetFmt != null && targetFmt.isImage() || "pdf".equalsIgnoreCase(outExt)))
                || ("pdf".equalsIgnoreCase(inExt) && targetFmt != null && targetFmt.isImage());

        List<File> generated = new ArrayList<>();
        if (isImageOp) {
            generated = ImageEngine.convert(in, out, targetFmt, imgOpts);
        } else {
            DocumentEngine.convert(in, out, targetFmt, imgOpts, title, author, margin);
            generated.add(out);
        }

        long elapsed = System.currentTimeMillis() - start;

        for (File f : generated) {
            long newLen = f.length();
            String change = "";
            if (origLen > 0) {
                double pct = (double) newLen / origLen * 100.0;
                change = (newLen < origLen) ? String.format(" [Reduced by %.1f%%]", 100.0 - pct)
                        : String.format(" [Size: %.1f%%]", pct);
            }
            printSuccess("Created: " + color(f.getAbsolutePath(), "\u001B[1m") + " (" + formatBytes(newLen) + change + ", " + elapsed + " ms)");
        }
    }

    private static void handleBatch(String[] args) throws Exception {
        Map<String, String> opts = parseFlags(args);
        List<String> pos = parsePositional(args);

        if (pos.size() < 2) throw new IllegalArgumentException("Usage: conve-ult batch <input-dir> --to <format> -o <output-dir>");

        File inDir = new File(pos.get(1));
        if (!inDir.isDirectory()) throw new IllegalArgumentException("Input directory not found: " + inDir.getPath());

        String to = opts.get("to");
        if (to == null) throw new IllegalArgumentException("Batch conversion requires --to <format>");
        Format targetFmt = Format.fromExtension(to);
        String targetExt = targetFmt != null ? targetFmt.getPrimaryExtension() : to;

        File outDir = opts.containsKey("output") ? new File(opts.get("output")) : inDir;
        outDir.mkdirs();

        File[] files = inDir.listFiles();
        if (files == null || files.length == 0) {
            printInfo("Batch", "No files found in directory.");
            return;
        }

        printBanner();
        System.out.println(color("✦ Batch Converting: ", "\u001B[1;36m") + inDir.getAbsolutePath() + " -> " + targetExt.toUpperCase());

        int count = 0, failed = 0;
        long totalStart = System.currentTimeMillis();

        for (File f : files) {
            if (f.isDirectory() || f.getName().startsWith(".")) continue;
            File dest = new File(outDir, getBaseName(f) + "." + targetExt);

            List<String> itemArgs = new ArrayList<>();
            itemArgs.add(f.getAbsolutePath());
            itemArgs.add("-o"); itemArgs.add(dest.getAbsolutePath());
            if (opts.containsKey("width")) { itemArgs.add("-w"); itemArgs.add(opts.get("width")); }
            if (opts.containsKey("height")) { itemArgs.add("-h"); itemArgs.add(opts.get("height")); }
            if (opts.containsKey("scale")) { itemArgs.add("-s"); itemArgs.add(opts.get("scale")); }
            if (opts.containsKey("quality")) { itemArgs.add("-q"); itemArgs.add(opts.get("quality")); }
            if (opts.containsKey("force")) itemArgs.add("-f");

            try {
                handleConvert(itemArgs.toArray(new String[0]));
                count++;
            } catch (Exception e) {
                printError("Failed " + f.getName() + ": " + e.getMessage());
                failed++;
            }
        }

        long totalElapsed = System.currentTimeMillis() - totalStart;
        System.out.println();
        printSuccess(String.format("Batch complete: %d converted, %d failed in %d ms", count, failed, totalElapsed));
    }

    private static void handleInfo(String[] args) throws Exception {
        List<String> pos = parsePositional(args);
        if (pos.size() < 2) throw new IllegalArgumentException("Usage: conve-ult info <file>");

        File f = new File(pos.get(1));
        if (!f.exists()) throw new IllegalArgumentException("File not found: " + f.getPath());

        Format fmt = Format.fromFile(f);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("File Path", f.getAbsolutePath());
        details.put("File Size", formatBytes(f.length()) + " (" + f.length() + " bytes)");

        if (fmt != null && fmt.isImage()) {
            details.put("Category", "Image");
            details.putAll(ImageEngine.inspect(f));
        } else {
            details.put("Category", "Document");
            try {
                DocumentModel.Doc doc = DocumentEngine.parse(f, fmt, getExt(f));
                if (doc.getTitle() != null) details.put("Title", doc.getTitle());
                if (doc.getAuthor() != null) details.put("Author", doc.getAuthor());
                if (doc.getPageCount() > 0) details.put("Pages", String.valueOf(doc.getPageCount()));
                details.put("Word Count", String.valueOf(doc.getWordCount()));
            } catch (Exception e) {
                details.put("Parser Note", e.getMessage());
            }
        }

        printBanner();
        System.out.println(color("FILE METADATA & INSPECTION:", "\u001B[1;36m"));
        System.out.println("─".repeat(50));
        for (var e : details.entrySet()) {
            System.out.printf("  %-15s: %s%n", color(e.getKey(), "\u001B[1m"), e.getValue());
        }
        System.out.println("─".repeat(50));
    }

    // =========================================================================
    // 2. CLI OPTION PARSER
    // =========================================================================

    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-o".equals(a) || "--output".equals(a)) {
                if (i + 1 < args.length) flags.put("output", args[++i]);
            } else if ("-t".equals(a) || "--to".equals(a) || "--format".equals(a)) {
                if (i + 1 < args.length) flags.put("to", args[++i]);
            } else if ("-w".equals(a) || "--width".equals(a)) {
                if (i + 1 < args.length) flags.put("width", args[++i]);
            } else if ("-h".equals(a) || "--height".equals(a)) {
                if (i + 1 < args.length) flags.put("height", args[++i]);
            } else if ("-s".equals(a) || "--scale".equals(a)) {
                if (i + 1 < args.length) flags.put("scale", args[++i]);
            } else if ("-q".equals(a) || "--quality".equals(a)) {
                if (i + 1 < args.length) flags.put("quality", args[++i]);
            } else if ("--compress".equals(a) || "--compression".equals(a)) {
                if (i + 1 < args.length) flags.put("compress", args[++i]);
            } else if ("--fit".equals(a)) {
                if (i + 1 < args.length) flags.put("fit", args[++i]);
            } else if ("--keep-ratio".equals(a)) {
                flags.put("keep-ratio", "true");
            } else if ("--no-keep-ratio".equals(a) || "--stretch".equals(a)) {
                flags.put("keep-ratio", "false");
            } else if ("--grayscale".equals(a) || "--gray".equals(a) || "--bw".equals(a)) {
                flags.put("grayscale", "true");
            } else if ("--rotate".equals(a)) {
                if (i + 1 < args.length) flags.put("rotate", args[++i]);
            } else if ("--flip".equals(a)) {
                if (i + 1 < args.length) flags.put("flip", args[++i]);
            } else if ("--dpi".equals(a)) {
                if (i + 1 < args.length) flags.put("dpi", args[++i]);
            } else if ("--pages".equals(a)) {
                if (i + 1 < args.length) flags.put("pages", args[++i]);
            } else if ("--title".equals(a)) {
                if (i + 1 < args.length) flags.put("title", args[++i]);
            } else if ("--author".equals(a)) {
                if (i + 1 < args.length) flags.put("author", args[++i]);
            } else if ("--margin".equals(a)) {
                if (i + 1 < args.length) flags.put("margin", args[++i]);
            } else if ("-f".equals(a) || "--force".equals(a) || "--overwrite".equals(a)) {
                flags.put("force", "true");
            } else if ("-v".equals(a) || "--verbose".equals(a)) {
                flags.put("verbose", "true");
            }
        }
        return flags;
    }

    private static List<String> parsePositional(String[] args) {
        List<String> pos = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                if ("-o".equals(a) || "--output".equals(a) || "-t".equals(a) || "--to".equals(a) ||
                    "-w".equals(a) || "--width".equals(a) || "-h".equals(a) || "--height".equals(a) ||
                    "-s".equals(a) || "--scale".equals(a) || "-q".equals(a) || "--quality".equals(a) ||
                    "--compress".equals(a) || "--fit".equals(a) || "--rotate".equals(a) || "--flip".equals(a) ||
                    "--dpi".equals(a) || "--pages".equals(a) || "--title".equals(a) || "--author".equals(a) ||
                    "--margin".equals(a)) {
                    i++; // skip parameter
                }
            } else {
                pos.add(a);
            }
        }
        return pos;
    }

    private static ImageEngine.Options extractImageOptions(Map<String, String> opts) {
        ImageEngine.Options o = new ImageEngine.Options();
        if (opts.containsKey("width")) o.width = Integer.parseInt(opts.get("width"));
        if (opts.containsKey("height")) o.height = Integer.parseInt(opts.get("height"));
        if (opts.containsKey("scale")) {
            String s = opts.get("scale");
            o.scale = s.endsWith("%") ? Double.parseDouble(s.substring(0, s.length() - 1)) / 100.0 : Double.parseDouble(s);
        }
        if (opts.containsKey("keep-ratio")) o.keepAspectRatio = Boolean.parseBoolean(opts.get("keep-ratio"));
        if (opts.containsKey("fit")) o.fitMode = opts.get("fit");
        if (opts.containsKey("quality")) {
            float q = Float.parseFloat(opts.get("quality"));
            o.quality = q > 1.0f ? q / 100.0f : q;
        }
        if (opts.containsKey("compress")) o.compressionPreset = opts.get("compress");
        if (opts.containsKey("grayscale")) o.grayscale = true;
        if (opts.containsKey("rotate")) o.rotateDegrees = Integer.parseInt(opts.get("rotate"));
        if (opts.containsKey("flip")) {
            String fl = opts.get("flip").toLowerCase();
            if (fl.startsWith("h")) o.flipHorizontal = true;
            if (fl.startsWith("v")) o.flipVertical = true;
        }
        if (opts.containsKey("dpi")) o.dpi = Float.parseFloat(opts.get("dpi"));
        if (opts.containsKey("pages")) o.pageRange = opts.get("pages");
        return o;
    }

    private static boolean hasFlag(String[] args, String... flags) {
        for (String a : args) {
            for (String f : flags) {
                if (f.equalsIgnoreCase(a)) return true;
            }
        }
        return false;
    }

    // =========================================================================
    // 3. TERMINAL UI & HELPERS
    // =========================================================================

    public static void printBanner() {
        System.out.println(color("   _____ ____  _   ___     ______   __  ____  LT", "\u001B[1;36m"));
        System.out.println(color("  / ____/ __ \\/ | / / |   / / __/  / / / / / / /", "\u001B[36m"));
        System.out.println(color(" / /   / / / /  |/ /| |  / / _/___/ /_/ / /_/ / ", "\u001B[34m"));
        System.out.println(color("/ /___/ /_/ / /|  / | | / / /____/ __  / __  /  ", "\u001B[34m"));
        System.out.println(color("\\____/\\____/_/ |_/  |___/_____/ /_/ /_/_/ /_/   ", "\u001B[35m"));
        System.out.println(color("  conve-ult v1.0.0 — Universal File Converter (Java • Apache POI • PDFBox)", "\u001B[2m"));
        System.out.println();
    }

    public static void printHelp() {
        printBanner();
        System.out.println(color("USAGE:", "\u001B[1m"));
        System.out.println("  conve-ult <input-file> -o <output-file> [options]");
        System.out.println("  conve-ult <input-file> --to <target-format> [options]");
        System.out.println("  conve-ult batch <input-dir> --to <target-format> -o <output-dir> [options]");
        System.out.println("  conve-ult info <file>");
        System.out.println("  conve-ult formats\n");

        System.out.println(color("IMAGE CONVERSION OPTIONS:", "\u001B[1;36m"));
        System.out.println("  -w, --width <px>           Output width in pixels (e.g. 1920)");
        System.out.println("  -h, --height <px>          Output height in pixels (e.g. 1080)");
        System.out.println("  -s, --scale <factor>       Scale factor or percentage (e.g. 0.5, 2.0, 50%)");
        System.out.println("      --keep-ratio           Keep aspect ratio (default: true)");
        System.out.println("      --no-keep-ratio        Stretch image to exact width and height");
        System.out.println("      --fit <mode>           Fit mode: 'contain', 'cover', or 'fill'");
        System.out.println("  -q, --quality <1-100>      Compression quality (e.g. 80)");
        System.out.println("      --compress <level>     Compression preset: low, medium, high, max");
        System.out.println("      --grayscale, --bw      Convert image to grayscale");
        System.out.println("      --rotate <degrees>     Rotate clockwise (90, 180, 270)");
        System.out.println("      --flip <h|v>           Flip horizontally or vertically");
        System.out.println("      --dpi <dpi>            Target DPI for rendering (default: 150)\n");

        System.out.println(color("DOCUMENT CONVERSION OPTIONS:", "\u001B[1;36m"));
        System.out.println("  --pages <range>            PDF page range (e.g. '1-3', '5', 'all')");
        System.out.println("  --title <text>             Set document title");
        System.out.println("  --author <text>            Set document author");
        System.out.println("  --margin <pt>              Page margins in points (default: 40)\n");

        System.out.println(color("GENERAL OPTIONS:", "\u001B[1;36m"));
        System.out.println("  -o, --output <path>        Output destination");
        System.out.println("  -t, --to <format>          Target format extension (e.g. webp, docx, pdf)");
        System.out.println("  -f, --force, --overwrite   Force overwrite existing files");
        System.out.println("  -h, --help                 Display this help");
        System.out.println("  -V, --version              Display version\n");
    }

    private static void printVersion() {
        System.out.println("conve-ult v1.0.0");
        System.out.println("Universal File Converter CLI");
        System.out.println("Runtime: Java " + System.getProperty("java.version"));
        System.out.println("Engines: Apache POI 5.2.5 • Apache PDFBox 3.0.1 • TwelveMonkeys ImageIO");
    }

    private static void printFormats() {
        printBanner();
        System.out.println(color("SUPPORTED FILE FORMATS:", "\u001B[1m\n"));
        System.out.println(color("1. IMAGE FORMATS", "\u001B[1;36m"));
        System.out.printf("  %-10s %-14s %s%n", "FORMAT", "EXTENSION", "CAPABILITIES");
        System.out.printf("  %-10s %-14s %s%n", "------", "---------", "------------");
        System.out.printf("  %-10s %-14s %s%n", "PNG", ".png", "Lossless, alpha transparency, resizing");
        System.out.printf("  %-10s %-14s %s%n", "JPEG", ".jpg, .jpeg", "Lossy quality compression, resizing, rotation");
        System.out.printf("  %-10s %-14s %s%n", "WEBP", ".webp", "Modern high compression, web-optimized");
        System.out.printf("  %-10s %-14s %s%n", "GIF", ".gif", "Palette quantization");
        System.out.printf("  %-10s %-14s %s%n", "BMP", ".bmp", "Standard bitmap");
        System.out.printf("  %-10s %-14s %s%n", "TIFF", ".tiff, .tif", "High dynamic range");
        System.out.printf("  %-10s %-14s %s%n", "PDF", ".pdf", "Image <-> PDF multi-page rendering\n");

        System.out.println(color("2. DOCUMENT FORMATS", "\u001B[1;36m"));
        System.out.printf("  %-10s %-14s %-20s %s%n", "FORMAT", "EXTENSION", "ENGINE", "CAPABILITIES");
        System.out.printf("  %-10s %-14s %-20s %s%n", "------", "---------", "------", "------------");
        System.out.printf("  %-10s %-14s %-20s %s%n", "PDF", ".pdf", "Apache PDFBox 3.0", "Full layout, tables, code blocks, pages");
        System.out.printf("  %-10s %-14s %-20s %s%n", "DOCX", ".docx", "Apache POI XWPF", "Styles, headings, bullet lists, shaded tables");
        System.out.printf("  %-10s %-14s %-20s %s%n", "DOC", ".doc", "Apache POI HWPF", "Word 97-2004 format extraction & generation");
        System.out.printf("  %-10s %-14s %-20s %s%n", "ODT", ".odt", "OpenDocument Engine", "Standard ODF XML archive, styles, tables");
        System.out.printf("  %-10s %-14s %-20s %s%n", "HTML", ".html, .htm", "HTML5 Engine", "Semantic tags, typography CSS, tables");
        System.out.printf("  %-10s %-14s %-20s %s%n", "MARKDOWN", ".md", "Markdown Engine", "GitHub Flavored Markdown, code blocks");
        System.out.printf("  %-10s %-14s %-20s %s%n", "TEXT", ".txt, .log", "Plain Text Engine", "Structured plain text, ASCII tables\n");
    }

    public static void printSuccess(String msg) {
        System.out.println(color("✔ SUCCESS: ", "\u001B[1;32m") + msg);
    }

    public static void printInfo(String lbl, String msg) {
        System.out.println(color("ℹ " + lbl + ": ", "\u001B[1;36m") + msg);
    }

    public static void printWarn(String msg) {
        System.out.println(color("⚠ WARNING: ", "\u001B[1;33m") + msg);
    }

    public static void printError(String msg) {
        System.err.println(color("✖ ERROR: ", "\u001B[1;31m") + msg);
    }

    public static String color(String text, String code) {
        return code + text + "\u001B[0m";
    }

    public static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        int exp = (int) (Math.log(b) / Math.log(1024));
        return String.format("%.2f %sB", b / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
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
