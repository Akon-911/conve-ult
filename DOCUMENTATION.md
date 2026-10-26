# conve-ult — Technical Code & Architecture Documentation 📘

---

## 📑 Table of Contents
1. [Architecture & Design Principles](#1-architecture--design-principles)
2. [Project Structure](#2-project-structure)
3. [Core Module Walkthrough](#3-core-module-walkthrough)
   - [3.1 Format Registry (`Format.java`)](#31-format-registry-formatjava)
   - [3.2 Unified Document Model (`DocumentModel.java`)](#32-unified-document-model-documentmodeljava)
   - [3.3 Image Processing Engine (`ImageEngine.java`)](#33-image-processing-engine-imageenginejava)
   - [3.4 Document Processing Engine (`DocumentEngine.java`)](#34-document-processing-engine-documentenginejava)
   - [3.5 CLI Application Controller (`ConveUlt.java`)](#35-cli-application-controller-conveultjava)
   - [3.6 JavaFX GUI Desktop Application (`ConveUltApp.java` & `DarkTheme.java`)](#36-javafx-gui-desktop-application)
4. [Deep Dives into Underlying Technologies](#4-deep-dives-into-underlying-technologies)
   - [Apache PDFBox 3.0.1](#apache-pdfbox-301)
   - [Apache POI 5.2.5 (OOXML & Scratchpad)](#apache-poi-525-ooxml--scratchpad)
   - [TwelveMonkeys ImageIO](#twelvemonkeys-imageio)
5. [Developer Guide: Extending conve-ult](#5-developer-guide-extending-conve-ult)
6. [Compilation & Execution Architecture](#6-compilation--execution-architecture)

---

## 1. Architecture & Design Principles

### 1.1 The $N \times M$ Matrix Problem
Traditional converters often write separate translation logic for each format pair ($A \to B$). Supporting $N$ input formats and $M$ output formats typically requires $N \times M$ individual translators, which leads to massive code duplication and fragility.

**`conve-ult` solves this using an Intermediate Representation (IR) Pattern:**

```
                  ┌───────────────┐
                  │ Input Format  │ (PDF, DOCX, DOC, ODT, HTML, MD, TXT, IMG)
                  └───────┬───────┘
                          │  Parse
                          ▼
            ┌───────────────────────────┐
            │   Unified DocumentModel   │ (Doc AST: Headings, Runs, Tables, Lists, Code)
            └─────────────┬─────────────┘
                          │  Render
                          ▼
                  ┌───────────────┐
                  │ Output Format │ (PDF, DOCX, DOC, ODT, HTML, MD, TXT, IMG)
                  └───────────────┘
```

With this architecture:
- Adding a new input parser immediately makes that format convertible to **all** target formats.
- Adding a new output renderer immediately allows **all** input formats to target it.
- Total complexity is reduced from $O(N \times M)$ to $O(N + M)$.

### 1.2 Zero-Daemon, Headless Native JVM Execution
Many office conversion tools rely on running background LibreOffice / OpenOffice headless daemons or headless Chromium instances. While feature-rich, these daemons:
- Consume hundreds of megabytes of memory.
- Introduce IPC timeouts and daemon crashes.
- Often fail in locked-down sandbox environments due to missing display/DBus services.

`conve-ult` runs **100% inside the Java Virtual Machine (JVM)** using battle-tested libraries:
- **Apache POI** for Microsoft formats (`.docx`, `.doc`).
- **Apache PDFBox 3** for PDF rendering and generation.
- **TwelveMonkeys ImageIO** for WebP and modern image formats.
- **Standard Java XML & Zip APIs** for OpenDocument (`.odt`) packaging.

---

## 2. Project Structure

The project has been organized into a simple, cohesive structure where each file has a single, well-defined responsibility:

```
conve-ult/
├── bin/
│   └── conve-ult               # Executable shell launcher (auto-builds & sets classpath)
├── conve-ult                   # Symlink to bin/conve-ult for local invocation
├── lib/                        # Pre-bundled runtime JARs (POI, PDFBox, TwelveMonkeys)
├── javafx-sdk/                 # OpenJFX 21 LTS runtime (auto-bootstrapped if missing)
├── pom.xml                     # Maven build configuration
├── build.sh                    # Independent compiler script using standard javac & jar
├── README.md                   # User guide & command reference
├── DOCUMENTATION.md            # Detailed code and architectural documentation
├── test_samples/               # Example test assets (sample.png, sample.md)
├── src/main/resources/
│   └── com/conveult/gui/
│       └── dark-theme.css      # Dark mode stylesheet (slate/indigo palette)
└── src/main/java/com/conveult/
    ├── ConveUltMain.java       # Universal entry point: dispatches to GUI or CLI
    ├── ConveUlt.java           # CLI entry point, flag parser & terminal UI
    ├── Format.java             # Format registry (MIME types, extensions, category)
    ├── DocumentModel.java      # Intermediate document AST (Doc, Block, Run, Cell, Item)
    ├── ImageEngine.java        # All image processing (resize, quality, rotate, PDF<->IMG)
    ├── DocumentEngine.java     # Complete document parsing & rendering matrix
    └── gui/
        ├── ConveUltApp.java    # JavaFX desktop GUI application (Single & Batch modes)
        └── DarkTheme.java      # Styling helpers, SVG vector icons & theme loader
```

### Why this structure?
- **Zero Confusion:** Developers don't need to hunt through dozens of fragmented subpackages.
- **High Cohesion:** All image logic lives in `ImageEngine.java`; all document logic lives in `DocumentEngine.java`; data models live in `DocumentModel.java`.
- **Fast Navigation:** You can understand the entire project by reading 5 clean, self-contained files.

---

## 3. Core Module Walkthrough

### 3.1 Format Registry (`Format.java`)

[`Format.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/Format.java) defines an enum representing all supported formats:

```java
public enum Format {
    // Images
    PNG, JPG, WEBP, GIF, BMP, TIFF, WBMP,
    // Documents
    PDF, DOCX, DOC, ODT, HTML, MD, TXT, RTF;
}
```

Key features:
- **Categorization:** Each format belongs to `Kind.IMAGE` or `Kind.DOCUMENT`.
- **Extension Aliases:** Automatically maps `.jpeg`, `.jpe`, and `.jfif` to `JPG`; `.markdown` to `MD`; `.htm` to `HTML`; `.tif` to `TIFF`.
- **Lookup Helpers:**
  - `Format.fromExtension("webp")` $\to$ `Format.WEBP`
  - `Format.fromFile(file)` $\to$ extracts extension and maps to `Format`.

---

### 3.2 Unified Document Model (`DocumentModel.java`)

[`DocumentModel.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/DocumentModel.java) provides the Intermediate Representation (AST) used during conversion:

```java
public class DocumentModel {
    public static class Doc {
        String title, author, subject;
        int pageCount;
        List<Block> blocks;
    }

    public static class Block {
        public enum Type {
            HEADING, PARAGRAPH, CODE, LIST, TABLE, QUOTE, RULE, PAGE_BREAK, IMAGE
        }
        Type type;
        int level;                    // For headings (1-6)
        List<Run> runs;              // Formatted text spans
        String code, language;       // For code blocks
        boolean ordered;             // For lists (1,2,3 vs •)
        List<Item> listItems;        // List elements
        List<List<Cell>> tableRows;  // Grid table cells
        File imageFile;              // Embedded images
    }

    public static class Run {
        String text;
        boolean bold, italic, underline, monospace;
        String color, linkUrl;
    }
}
```

#### Supported Elements & Properties
1. **Headings:** Levels 1 through 6, preserving bold styling and text runs.
2. **Paragraphs:** Collections of `Run` objects allowing mixed inline styling (e.g. bold word inside italic sentence).
3. **Code Blocks:** Monospaced source code with language tag.
4. **Lists:** Ordered (numbered) or unordered (bulleted), containing items with text runs.
5. **Tables:** 2D grid of `Cell` objects with automatic header row detection and cell styling.
6. **Blockquotes:** Quoted paragraphs styled with accent bars.
7. **Rules & Breaks:** Horizontal divider lines (`---`) and page breaks.

---

### 3.3 Image Processing Engine (`ImageEngine.java`)

[`ImageEngine.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/ImageEngine.java) encapsulates all raster image transformations, scaling, compression, and PDF image operations.

#### Pipeline:
```
File/PDF -> Load BufferedImage -> Transform (Resize, Aspect, Rotate, Flip, Gray) -> Encode
```

#### Key Capabilities:
- **Resizing & Scaling:**
  - High-quality bicubic interpolation:
    ```java
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    ```
  - **Aspect Ratio Options:**
    - `keepAspectRatio = true` (default): Scales proportionally to fit within `--width` and `--height`.
    - `fitMode = "contain"`: Fits the whole image inside bounding box.
    - `fitMode = "cover"`: Scales to cover entire box.
    - `--no-keep-ratio`: Forces exact dimensions.
    - `--scale`: Scales by numeric multiplier (`0.5`) or percentage (`50%`).
- **Compression & Quality:**
  - For JPEG: Sets `ImageWriteParam.MODE_EXPLICIT` and quality float (`0.01f - 1.0f`).
  - For WebP: Uses TwelveMonkeys ImageIO or falls back cleanly to ImageMagick if an external encoder is preferred.
  - Handles palette quantization and transparency flattening when converting RGBA $\to$ RGB formats (e.g., PNG with alpha converted to JPG).
- **PDF $\leftrightarrow$ Image Conversion:**
  - **Image $\to$ PDF:** Creates a PDF document using Apache PDFBox, creates a page matching the image dimensions, and draws the image using `LosslessFactory.createFromImage(doc, img)`.
  - **PDF $\to$ Image(s):** Loads PDF with `Loader.loadPDF(file)`, uses `PDFRenderer.renderImageWithDPI(page, dpi)`, and renders selected pages (supports ranges e.g. `--pages 1-3`, `all`).

---

### 3.4 Document Processing Engine (`DocumentEngine.java`)

[`DocumentEngine.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/DocumentEngine.java) contains all document ingestion (parsing) and document generation (rendering).

#### Parsing Implementations:
| Format | Ingestion Technique |
| :--- | :--- |
| **PDF** | Uses Apache PDFBox `PDFTextStripper` with positional sorting. Detects headings via uppercase/length heuristics, bullet points, and numbered lists. |
| **DOCX** | Uses Apache POI `XWPFDocument`. Extracts paragraphs, styles (`Heading 1-4`), runs (bold, italic, font colors), bullet lists (`numID`), and tables (`XWPFTable`). |
| **DOC** | Uses Apache POI `HWPFDocument` & `WordExtractor`. Features automatic magic-byte sniffing (`PK` for misnamed docx, `<` for HTML-wrapped docs). |
| **ODT** | Opens the zip archive, extracts `content.xml`, and parses OASIS ODF tags (`<text:h>`, `<text:p>`, `<text:list>`, `<table:table>`). |
| **HTML** | Strips non-content tags (`<script>`, `<style>`), parses semantic tags (`<h1>`-`<h6>`, `<p>`, `<pre>`, `<ul>`, `<ol>`, `<table>`, `<blockquote>`), and decodes HTML entities. |
| **Markdown** | Parses `#` headings, fenced code blocks ````lang ... ````, blockquotes `>`, horizontal rules `---`, list markers `- ` / `1. `, and tables `|...|`. |
| **Plain Text** | Parses text lines, identifies paragraph boundaries, divider lines, and numbered/bulleted items. |

#### Rendering Implementations:
| Format | Generation Technique |
| :--- | :--- |
| **PDF** | Uses **Apache PDFBox 3.0**. Implements custom line-wrapping, dynamic font metrics, table rendering with grid borders and cell padding, code blocks with background shading, and automatic pagination with footers ("Page X of Y"). |
| **DOCX** | Uses **Apache POI XWPF**. Builds Word documents with styled headings, paragraphs, formatted runs (bold, italic, color, font size), bullet lists, and shaded tables. |
| **ODT** | Packages a valid **OASIS OpenDocument Text** zip archive containing uncompressed `mimetype`, `META-INF/manifest.xml`, `styles.xml`, and `content.xml`. |
| **HTML** | Generates responsive, semantic HTML5 with modern CSS styling (clean typography, bordered tables, syntax-styled code blocks, blockquotes). |
| **Markdown** | Generates clean GitHub-flavored Markdown (`#`, `**bold**`, `*italic*`, fenced code blocks, tables). |
| **Plain Text** | Formats text with capitalized/underlined headings, indented bullet items, and ASCII grid tables (`+---+---+`). |
| **DOC** | Generates Word-compatible compound document structure that Microsoft Word and LibreOffice open directly. |

---

### 3.5 CLI Application Controller (`ConveUlt.java`)

[`ConveUlt.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/ConveUlt.java) serves as the main CLI entry point:
- **Flag Parsing:** Extracts options like `-o`, `--to`, `-w`, `-h`, `-s`, `-q`, `--compress`, `--rotate`, `--grayscale`, `--dpi`, `--margin`.
- **Command Routing:** Routes to `convert`, `batch`, `info`, `formats`, `help`, or `version`.
- **Diagnostics & Feedback:** Measures execution time in milliseconds, calculates file size changes and compression savings percentage, and outputs formatted ANSI color messages.
- **Log Suppression:** Programmatically disables noisy StatusLogger and font warnings from Apache PDFBox and Log4j so CLI output remains clean.

---

### 3.6 JavaFX GUI Desktop Application

[`ConveUltApp.java`](file:///home/akon-s/Documents/Projects/vibecheck/src/main/java/com/conveult/gui/ConveUltApp.java) and [`DarkTheme.java`](file:///home/akon-s/Documents/Projects/vibecheck/src/main/java/com/conveult/gui/DarkTheme.java) provide the frontend interface for `conve-ult`:

- **Universal Dispatcher (`ConveUltMain.java`):**
  - When invoked with `gui`, `--gui`, or with no arguments inside a desktop environment (`$DISPLAY` or `$WAYLAND_DISPLAY`), launches the JavaFX GUI application.
  - When invoked with file arguments or in headless environments, executes the CLI pipeline without initializing graphic toolkits.
- **Dark Mode Design System:**
  - Designed with an obsidian-slate palette (`#13141f` base, `#1a1c2b` surfaces, `#282c42` borders, and `#6366f1` indigo accents).
  - High-DPI scalable SVG vector icons for upload, convert, check, info, trash, and folder actions.
- **Drag-and-Drop Dropzone:**
  - Implements JavaFX `Dragboard` event handlers (`DRAG_OVER`, `DRAG_ENTERED`, `DRAG_EXITED`, `DRAG_DROPPED`).
  - Provides visual hover states and smooth transition to the file inspection card.
- **Asynchronous Worker Threading:**
  - Conversions are dispatched via `javafx.concurrent.Task` onto separate worker threads.
  - The UI thread remains completely responsive; progress bars and status labels are updated via `Platform.runLater()`.
- **Batch Processing Table:**
  - Backed by an `ObservableList<BatchItem>` in a JavaFX `TableView`.
  - Supports adding individual files or entire folders with automatic file filtering.
  - Features real-time status tracking (`Queued` $\to$ `Processing...` $\to$ `✔ Done` or `✘ Failed`).
- **Desktop Integration:**
  - Direct links to launch the converted file with the user's default system application (`Desktop.getDesktop().open(file)`) or reveal the containing folder in the desktop file manager.

---

## 4. Deep Dives into Underlying Technologies

### Apache PDFBox 3.0.1
`conve-ult` utilizes the latest **Apache PDFBox 3.0.1**:
- **PDF Loading:** Uses the modern `Loader.loadPDF(File)` API instead of legacy `PDDocument.load(File)`.
- **Font Management:** Standard 14 fonts are instantiated via `new PDType1Font(Standard14Fonts.FontName.HELVETICA)`.
- **Character Sanitization:** Type 1 PDF fonts rely on WinAnsiEncoding. Exotic Unicode characters (e.g. smart quotes, em-dashes, bullets) are automatically sanitized into WinAnsi/ASCII equivalents to prevent `IllegalArgumentException`.
- **Pagination & Footers:** A two-pass approach tracks total pages created and appends centered footer page numbers ("Page 1 of 3") across all pages before saving.

### Apache POI 5.2.5 (OOXML & Scratchpad)
- **DOCX Generation:** `XWPFDocument` builds OpenXML Word structures directly:
  - Paragraphs: `doc.createParagraph()` with `setSpacingBefore` and `setSpacingAfter`.
  - Runs: `p.createRun()` with `setBold`, `setItalic`, `setFontSize`, `setColor`.
  - Tables: `doc.createTable()` with `XWPFTableRow` and `XWPFTableCell`, applying `cell.setColor("EAECEF")` for header shading.
- **DOC Compatibility:** `HWPFDocument` reads legacy Word 97-2004 binary files.

### TwelveMonkeys ImageIO
- Extends standard Java ImageIO with full support for:
  - **WebP:** High compression web format.
  - **TIFF:** Tagged Image File Format with multiple compression modes.
  - **JPEG Lossless & Metadata:** Preserves resolution and density headers.

---

## 5. Developer Guide: Extending conve-ult

### How to Add a New Format
Suppose you want to add support for a new format (e.g. `CSV`):

1. **Register the Format in [`Format.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/Format.java):**
   ```java
   CSV(Kind.DOCUMENT, "text/csv", "csv")
   ```

2. **Add Parser in [`DocumentEngine.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/DocumentEngine.java):**
   ```java
   private static Doc parseCsv(File file) throws Exception {
       Doc doc = new Doc(getBaseName(file));
       Block table = Block.table();
       // Parse CSV rows into table.addTableRow(...)
       doc.addBlock(table);
       return doc;
   }
   ```
   Add the condition in `DocumentEngine.parse(...)`:
   ```java
   if (fmt == Format.CSV || "csv".equalsIgnoreCase(ext)) return parseCsv(file);
   ```

3. **Add Renderer in [`DocumentEngine.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/DocumentEngine.java):**
   ```java
   private static void renderCsv(Doc doc, File out) throws IOException {
       // Iterate doc.getBlocks(), find tables, and write comma-separated rows
   }
   ```
   Add the condition in `DocumentEngine.render(...)`:
   ```java
   else if (fmt == Format.CSV || "csv".equalsIgnoreCase(ext)) renderCsv(doc, out);
   ```

4. **Recompile:**
   ```bash
   ./build.sh
   ```
   Your new format is now immediately convertible to and from **every existing format** (CSV $\to$ PDF, CSV $\to$ DOCX, DOCX $\to$ CSV, MD $\to$ CSV, etc.)!

---

## 6. Compilation & Execution Architecture

### Self-Contained Build (`build.sh`)
```bash
#!/usr/bin/env bash
# Collects all JARs from lib/ into classpath
# Compiles all sources in src/main/java/com/conveult/*.java
# Packages classes into target/conve-ult.jar
```
Does not require Maven or Gradle to be installed on the host system; works directly with standard `javac` and `jar`.

### Executable Wrapper (`bin/conve-ult`)
The launcher script:
1. Dynamically resolves the project directory (even if invoked via symlink).
2. Verifies that `target/conve-ult.jar` exists (auto-compiles with `build.sh` if not present).
3. Adds `lib/*.jar` and `conve-ult.jar` to the classpath.
4. Executes the JVM with optimal logging parameters (`-Dlog4j2.statusLoggerLevel=OFF`).
5. Passes all CLI arguments transparently (`"$@"`).
