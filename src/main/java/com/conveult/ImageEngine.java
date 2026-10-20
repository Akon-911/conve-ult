package com.conveult;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Universal Image conversion and transformation engine.
 */
public class ImageEngine {

    public static class Options {
        public Integer width;
        public Integer height;
        public Double scale;
        public boolean keepAspectRatio = true;
        public String fitMode = "contain"; // contain, cover, fill
        public Float quality; // 0.01 - 1.0
        public String compressionPreset; // low, medium, high, max
        public boolean grayscale = false;
        public int rotateDegrees = 0;
        public boolean flipHorizontal = false;
        public boolean flipVertical = false;
        public float dpi = 150f;
        public String pageRange = "all";
    }

    public static List<File> convert(File inputFile, File outputFile, Format targetFormat, Options options) throws Exception {
        if (options == null) options = new Options();
        if (outputFile.getParentFile() != null) outputFile.getParentFile().mkdirs();

        String inExt = getExtension(inputFile);
        List<File> generatedFiles = new ArrayList<>();

        // 1. PDF -> Image(s)
        if ("pdf".equalsIgnoreCase(inExt)) {
            return convertPdfToImages(inputFile, outputFile, targetFormat, options);
        }

        // 2. Read input image
        BufferedImage srcImage = ImageIO.read(inputFile);
        if (srcImage == null) {
            throw new IOException("Unsupported image format or damaged file: " + inputFile.getName());
        }

        // 3. Transform: resize, color, rotate, flip
        BufferedImage processed = transform(srcImage, options);

        // 4. Image -> PDF
        if (targetFormat == Format.PDF || "pdf".equalsIgnoreCase(getExtension(outputFile))) {
            convertImageToPdf(processed, outputFile);
            generatedFiles.add(outputFile);
            return generatedFiles;
        }

        // 5. Image -> Image
        writeImage(processed, outputFile, targetFormat, options);
        generatedFiles.add(outputFile);
        return generatedFiles;
    }

    public static BufferedImage transform(BufferedImage src, Options opts) {
        int origW = src.getWidth();
        int origH = src.getHeight();
        int targetW = origW;
        int targetH = origH;

        if (opts.scale != null && opts.scale > 0) {
            targetW = Math.max(1, (int) Math.round(origW * opts.scale));
            targetH = Math.max(1, (int) Math.round(origH * opts.scale));
        } else if (opts.width != null && opts.height != null) {
            if (opts.keepAspectRatio) {
                if ("cover".equalsIgnoreCase(opts.fitMode)) {
                    double s = Math.max((double) opts.width / origW, (double) opts.height / origH);
                    targetW = Math.max(1, (int) Math.round(origW * s));
                    targetH = Math.max(1, (int) Math.round(origH * s));
                } else {
                    double s = Math.min((double) opts.width / origW, (double) opts.height / origH);
                    targetW = Math.max(1, (int) Math.round(origW * s));
                    targetH = Math.max(1, (int) Math.round(origH * s));
                }
            } else {
                targetW = opts.width;
                targetH = opts.height;
            }
        } else if (opts.width != null) {
            targetW = opts.width;
            if (opts.keepAspectRatio) targetH = Math.max(1, (int) Math.round(origH * ((double) targetW / origW)));
        } else if (opts.height != null) {
            targetH = opts.height;
            if (opts.keepAspectRatio) targetW = Math.max(1, (int) Math.round(origW * ((double) targetH / origH)));
        }

        int imgType = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (opts.grayscale) imgType = BufferedImage.TYPE_BYTE_GRAY;

        BufferedImage resized = new BufferedImage(targetW, targetH, imgType);
        Graphics2D g2 = resized.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (imgType != BufferedImage.TYPE_INT_ARGB) {
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, targetW, targetH);
            }
            g2.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g2.dispose();
        }

        return applyOrientation(resized, opts);
    }

    private static BufferedImage applyOrientation(BufferedImage img, Options opts) {
        int rot = (opts.rotateDegrees % 360 + 360) % 360;
        if (rot == 0 && !opts.flipHorizontal && !opts.flipVertical) return img;

        int w = img.getWidth();
        int h = img.getHeight();
        int newW = (rot == 90 || rot == 270) ? h : w;
        int newH = (rot == 90 || rot == 270) ? w : h;

        int imgType = img.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (img.getType() == BufferedImage.TYPE_BYTE_GRAY) imgType = BufferedImage.TYPE_BYTE_GRAY;

        BufferedImage out = new BufferedImage(newW, newH, imgType);
        Graphics2D g2 = out.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            AffineTransform at = new AffineTransform();
            at.translate(newW / 2.0, newH / 2.0);
            if (rot != 0) at.rotate(Math.toRadians(rot));
            if (opts.flipHorizontal) at.scale(-1.0, 1.0);
            if (opts.flipVertical) at.scale(1.0, -1.0);
            at.translate(-w / 2.0, -h / 2.0);
            g2.drawRenderedImage(img, at);
        } finally {
            g2.dispose();
        }
        return out;
    }

    public static void writeImage(BufferedImage img, File outputFile, Format targetFormat, Options opts) throws IOException {
        String fmt = targetFormat != null ? targetFormat.getPrimaryExtension() : getExtension(outputFile);
        if (fmt.isEmpty()) fmt = "png";
        fmt = fmt.toLowerCase(Locale.ROOT);
        if ("jpg".equals(fmt)) fmt = "jpeg";

        // WebP output via ImageMagick fallback if pure Java writer is not present
        if ("webp".equals(fmt)) {
            Iterator<ImageWriter> test = ImageIO.getImageWritersByFormatName("webp");
            if (!test.hasNext()) {
                writeViaMagick(img, outputFile, "webp", resolveQuality(opts, "webp"));
                return;
            }
        }

        // Flatten transparency for formats that don't support alpha
        if (("jpeg".equals(fmt) || "bmp".equals(fmt) || "wbmp".equals(fmt)) && img.getColorModel().hasAlpha()) {
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
            img = rgb;
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(fmt);
        if (!writers.hasNext()) {
            writeViaMagick(img, outputFile, fmt, resolveQuality(opts, fmt));
            return;
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        float q = resolveQuality(opts, fmt);

        if (param.canWriteCompressed()) {
            try {
                if ("jpeg".equals(fmt)) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(q);
                } else if (opts.quality != null || opts.compressionPreset != null) {
                    String[] types = param.getCompressionTypes();
                    if (types != null && types.length > 0) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionType(types[0]);
                        param.setCompressionQuality(q);
                    }
                }
            } catch (Exception ignored) {}
        }

        try (FileImageOutputStream out = new FileImageOutputStream(outputFile)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static float resolveQuality(Options opts, String fmt) {
        if (opts.quality != null) return opts.quality;
        if (opts.compressionPreset != null) {
            return switch (opts.compressionPreset.toLowerCase(Locale.ROOT)) {
                case "low" -> 0.95f;
                case "medium" -> 0.80f;
                case "high" -> 0.60f;
                case "max" -> 0.40f;
                default -> 0.85f;
            };
        }
        return 0.85f;
    }

    private static void writeViaMagick(BufferedImage img, File outputFile, String fmt, float quality) throws IOException {
        File temp = File.createTempFile("conve_tmp_", ".png");
        try {
            ImageIO.write(img, "png", temp);
            ProcessBuilder pb = new ProcessBuilder("magick", temp.getAbsolutePath(), "-quality",
                    String.valueOf(Math.round(quality * 100)), outputFile.getAbsolutePath());
            Process p = pb.start();
            if (p.waitFor() != 0 || !outputFile.exists() || outputFile.length() == 0) {
                throw new IOException("External image converter failed for " + fmt);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image conversion interrupted");
        } finally {
            temp.delete();
        }
    }

    public static void convertImageToPdf(BufferedImage img, File outputFile) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(img.getWidth(), img.getHeight()));
            doc.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.drawImage(pdImage, 0, 0, img.getWidth(), img.getHeight());
            }
            doc.save(outputFile);
        }
    }

    public static List<File> convertPdfToImages(File inputFile, File outputFile, Format targetFormat, Options opts) throws Exception {
        List<File> result = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(inputFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int total = doc.getNumberOfPages();
            List<Integer> pages = parsePages(opts.pageRange, total);

            String base = getBaseName(outputFile);
            File parent = outputFile.getParentFile() != null ? outputFile.getParentFile() : new File(".");
            String ext = targetFormat != null ? targetFormat.getPrimaryExtension() : "png";

            for (int idx : pages) {
                BufferedImage pageImg = renderer.renderImageWithDPI(idx, opts.dpi);
                BufferedImage processed = transform(pageImg, opts);

                File dest = (pages.size() == 1) ? outputFile : new File(parent, String.format("%s_page_%d.%s", base, idx + 1, ext));
                writeImage(processed, dest, targetFormat, opts);
                result.add(dest);
            }
        }
        return result;
    }

    public static Map<String, String> inspect(File file) {
        Map<String, String> info = new LinkedHashMap<>();
        try (FileImageInputStream in = new FileImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (readers.hasNext()) {
                ImageReader r = readers.next();
                r.setInput(in);
                info.put("Image Format", r.getFormatName().toUpperCase());
                int w = r.getWidth(0);
                int h = r.getHeight(0);
                info.put("Dimensions", w + " × " + h + " px");
                info.put("Aspect Ratio", String.format("%.2f:1", (double) w / h));
                r.dispose();
                return info;
            }
        } catch (Exception ignored) {}

        try {
            BufferedImage bi = ImageIO.read(file);
            if (bi != null) {
                info.put("Dimensions", bi.getWidth() + " × " + bi.getHeight() + " px");
                info.put("Color Model", bi.getColorModel().hasAlpha() ? "RGBA" : "RGB");
            }
        } catch (Exception ignored) {}
        return info;
    }

    private static List<Integer> parsePages(String rangeStr, int total) {
        List<Integer> pages = new ArrayList<>();
        if (rangeStr == null || "all".equalsIgnoreCase(rangeStr.trim())) {
            for (int i = 0; i < total; i++) pages.add(i);
            return pages;
        }
        for (String p : rangeStr.split(",")) {
            p = p.trim();
            if (p.contains("-")) {
                String[] parts = p.split("-");
                if (parts.length == 2) {
                    int s = Math.max(1, Integer.parseInt(parts[0].trim()));
                    int e = Math.min(total, Integer.parseInt(parts[1].trim()));
                    for (int i = s; i <= e; i++) pages.add(i - 1);
                }
            } else if (!p.isEmpty()) {
                int val = Integer.parseInt(p);
                if (val >= 1 && val <= total) pages.add(val - 1);
            }
        }
        if (pages.isEmpty()) pages.add(0);
        return pages;
    }

    private static String getExtension(File f) {
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
