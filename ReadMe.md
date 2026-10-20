# conve-ult — Architectural Flow & Codebase Guide 🗺️
### *Complete Visual & Functional Flow of conve-ult*

---

## 📑 Contents
1. [High-Level System Architecture](#1-high-level-system-architecture)
2. [End-to-End Execution Flowchart](#2-end-to-end-execution-flowchart)
3. [Command Routing & CLI Lifecycle](#3-command-routing--cli-lifecycle)
4. [Image Pipeline Architecture & Transformation Flow](#4-image-pipeline-architecture--transformation-flow)
5. [Document Pipeline & The $N \times M$ Intermediate Model](#5-document-pipeline--the-n--m-intermediate-model)
6. [Component Interaction Sequence Diagram](#6-component-interaction-sequence-diagram)
7. [Class & Object Structure Diagram](#7-class--object-structure-diagram)
8. [Step-by-Step Code Execution Trace](#8-step-by-step-code-execution-trace)
9. [Resilience, Fallbacks & Safety Mechanics](#9-resilience-fallbacks--safety-mechanics)

---

## 1. High-Level System Architecture

`conve-ult` is designed around **zero external background daemons** and **high-cohesion modular engines**. The entire project is powered by 5 core Java source files:

```
┌────────────────────────────────────────────────────────────────────────┐
│                              ConveUlt.java                             │
│       CLI Entry Point • Argument Parsing • Subcommands • Terminal UI   │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
                    ▼                                ▼
       ┌─────────────────────────┐      ┌─────────────────────────┐
       │     ImageEngine.java    │      │   DocumentEngine.java   │
       │  Scaling • Compression  │      │  Parsing & Rendering    │
       │  Aspect Ratio • Filters │      │  POI • PDFBox • ODF XML │
       │   PDF <-> Image Bridge  │      └────────────┬────────────┘
       └────────────┬────────────┘                   │
                    │                                ▼
                    │                   ┌─────────────────────────┐
                    │                   │   DocumentModel.java    │
                    │                   │ Intermediate AST Model  │
                    │                   │ Doc • Block • Run • Cell│
                    │                   └────────────┬────────────┘
                    ▼                                │
       ┌─────────────────────────────────────────────▼───────────┐
       │                       Format.java                       │
       │     Registry of Formats, Extensions, MIME & Kind        │
       └─────────────────────────────────────────────────────────┘
```

---

## 2. End-to-End Execution Flowchart

The following diagram illustrates what happens from the millisecond the user runs `./conve-ult` in terminal until the converted file is saved and status is printed:

```mermaid
flowchart TD
    START(["User Terminal Command"]) --> WRAPPER["bin/conve-ult Wrapper Script"]
    WRAPPER --> CHECK_JAR{"target/conve-ult.jar exists?"}
    CHECK_JAR -- No --> COMPILE["Run build.sh (javac & jar)"]
    COMPILE --> LAUNCH["Invoke JVM with -Dlog4j2.statusLoggerLevel=OFF"]
    CHECK_JAR -- Yes --> LAUNCH

    LAUNCH --> ENTRY["ConveUlt.main(String[] args)"]
    ENTRY --> SILENCE["Static Block: Mute PDFBox/FontBox/Log4j Loggers"]
    SILENCE --> PARSE_ARGS["parseFlags() & parsePositional()"]

    PARSE_ARGS --> CMD_DISPATCH{"Command Mode?"}
    CMD_DISPATCH -- "--help / -h" --> HELP["printHelp() & Exit 0"]
    CMD_DISPATCH -- "--version / -V" --> VER["printVersion() & Exit 0"]
    CMD_DISPATCH -- "formats" --> FMT["printFormats() & Exit 0"]
    CMD_DISPATCH -- "info <file>" --> INFO["handleInfo() -> Inspect File Metadata"]
    CMD_DISPATCH -- "batch <dir>" --> BATCH["handleBatch() -> Iterate & Convert Folder"]
    CMD_DISPATCH -- "<input> [output]" --> CONVERT["handleConvert()"]

    CONVERT --> RESOLVE_OUT["Resolve Output Path & Target Format"]
    RESOLVE_OUT --> EXIST_CHECK{"Output Exists & no -f?"}
    EXIST_CHECK -- Yes --> WARN_EXIT["Warn and Exit 2"]
    EXIST_CHECK -- No --> OP_CHECK{"Is Image Operation?"}

    OP_CHECK -- Yes (Img->Img, Img->PDF, PDF->Img) --> IMG_FLOW["ImageEngine.convert(...)"]
    OP_CHECK -- No (Doc->Doc, Doc->Img) --> DOC_FLOW["DocumentEngine.convert(...)"]

    IMG_FLOW --> BENCHMARK["Calculate Elapsed Time & Size Delta"]
    DOC_FLOW --> BENCHMARK

    BENCHMARK --> SUCCESS["printSuccess() with Size % and Milliseconds"]
    SUCCESS --> DONE(["Process Complete (Exit 0)"])
```

---

## 3. Command Routing & CLI Lifecycle

Every CLI invocation passes through strict flag parsing and normalization:

```mermaid
flowchart TD
    CLI_INPUT["CLI Argument Array: args[]"] --> PARSE_OPTS["parseFlags()"]
    CLI_INPUT --> PARSE_POS["parsePositional()"]

    PARSE_OPTS --> EXTRACT_IMG["extractImageOptions()"]
    EXTRACT_IMG --> OPTS_OBJ["ImageEngine.Options Object"]

    OPTS_OBJ --> SET_W["width (-w / --width)"]
    OPTS_OBJ --> SET_H["height (-h / --height)"]
    OPTS_OBJ --> SET_S["scale (-s / --scale e.g. 50%)"]
    OPTS_OBJ --> SET_RATIO["keepAspectRatio (--keep-ratio / --no-keep-ratio)"]
    OPTS_OBJ --> SET_FIT["fitMode (--fit contain|cover|fill)"]
    OPTS_OBJ --> SET_Q["quality (-q / --quality 1-100)"]
    OPTS_OBJ --> SET_COMP["compressionPreset (--compress low|medium|high|max)"]
    OPTS_OBJ --> SET_ROT["rotateDegrees (--rotate 90|180|270)"]
    OPTS_OBJ --> SET_GRAY["grayscale (--grayscale / --bw)"]
    OPTS_OBJ --> SET_DPI["dpi (--dpi default: 150)"]
    OPTS_OBJ --> SET_PAGE["pageRange (--pages default: all)"]
```

---

## 4. Image Pipeline Architecture & Transformation Flow

The image conversion engine ([`ImageEngine.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/ImageEngine.java)) processes both raster images and PDF pages:

```mermaid
flowchart TD
    subgraph INGESTION ["1. Ingestion Phase"]
        INPUT_FILE["Source File"] --> CHECK_INPUT_TYPE{"Is Input PDF?"}
        CHECK_INPUT_TYPE -- Yes --> LOAD_PDF["Loader.loadPDF(file)"]
        LOAD_PDF --> RENDER_PAGE["PDFRenderer.renderImageWithDPI(pageIndex, dpi)"]
        CHECK_INPUT_TYPE -- No --> LOAD_IMG["ImageIO.read(file) via TwelveMonkeys"]
    end

    subgraph TRANSFORMATION ["2. Transformation Pipeline (transform())"]
        LOAD_IMG --> RESIZE_MATH["Calculate Target Width & Height"]
        RENDER_PAGE --> RESIZE_MATH

        RESIZE_MATH --> CHECK_RATIO{"keepAspectRatio?"}
        CHECK_RATIO -- True --> FIT_MODE{"Fit Mode"}
        FIT_MODE -- contain --> FIT_CONTAIN["Scale = min(tw/ow, th/oh)"]
        FIT_MODE -- cover --> FIT_COVER["Scale = max(tw/ow, th/oh)"]
        CHECK_RATIO -- False --> STRETCH["Use exact target width & height"]

        FIT_CONTAIN --> DRAW["Graphics2D.drawImage() with Bicubic Interpolation"]
        FIT_COVER --> DRAW
        STRETCH --> DRAW

        DRAW --> COLOR_CHECK{"grayscale requested?"}
        COLOR_CHECK -- Yes --> CONV_GRAY["Convert to TYPE_BYTE_GRAY"]
        COLOR_CHECK -- No --> COLOR_KEEP["Keep RGB / ARGB"]

        CONV_GRAY --> ORIENTATION["applyOrientation()"]
        COLOR_KEEP --> ORIENTATION

        ORIENTATION --> ROTATE_CHECK{"Rotation != 0 or Flip?"}
        ROTATE_CHECK -- Yes --> AFFINE["AffineTransform: translate() + rotate() + scale()"]
        ROTATE_CHECK -- No --> FINAL_BI["Transformed BufferedImage"]
        AFFINE --> FINAL_BI
    end

    subgraph ENCODING ["3. Target Encoding Phase (writeImage())"]
        FINAL_BI --> TARGET_CHECK{"Target Format"}
        
        TARGET_CHECK -- "PDF" --> PDF_GEN["convertImageToPdf(): PDDocument + LosslessFactory"]
        
        TARGET_CHECK -- "JPEG / JPG" --> FLATTEN_ALPHA["Flatten Alpha (Draw on White RGB)"]
        FLATTEN_ALPHA --> JPEG_WRITER["ImageIO JPEG Writer"]
        JPEG_WRITER --> SET_Q["ImageWriteParam: MODE_EXPLICIT quality"]
        SET_Q --> WRITE_JPEG["FileImageOutputStream.write()"]

        TARGET_CHECK -- "WEBP" --> WEBP_CHECK{"ImageIO WebP Writer present?"}
        WEBP_CHECK -- Yes --> WEBP_IMAGEIO["TwelveMonkeys WebP Writer"]
        WEBP_CHECK -- No --> WEBP_MAGICK["Fallback: magick -quality Q output.webp"]

        TARGET_CHECK -- "PNG / BMP / TIFF / GIF" --> RASTER_WRITER["ImageIO Writer for format"]
        RASTER_WRITER --> WRITE_RASTER["FileImageOutputStream.write()"]
    end

    PDF_GEN --> SAVED_FILE["Disk Output File"]
    WRITE_JPEG --> SAVED_FILE
    WEBP_IMAGEIO --> SAVED_FILE
    WEBP_MAGICK --> SAVED_FILE
    WRITE_RASTER --> SAVED_FILE
```

---

## 5. Document Pipeline & The $N \times M$ Intermediate Model

The document conversion system ([`DocumentEngine.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/DocumentEngine.java)) bridges arbitrary document formats through a structured Abstract Syntax Tree ([`DocumentModel.java`](file:///home/akon-s/Documents/Projects/vibecheck/conve-ult/src/main/java/com/conveult/DocumentModel.java)):

```mermaid
flowchart LR
    subgraph SOURCES ["Input Parsers"]
        SRC_PDF["PDF (.pdf)"]
        SRC_DOCX["Word (.docx)"]
        SRC_DOC["Legacy Word (.doc)"]
        SRC_ODT["OpenDocument (.odt)"]
        SRC_HTML["HTML (.html)"]
        SRC_MD["Markdown (.md)"]
        SRC_TXT["Plain Text (.txt)"]
    end

    subgraph IR ["Intermediate Representation (DocumentModel.Doc)"]
        direction TB
        DOC_ROOT["DocumentModel.Doc"]
        DOC_ROOT --> META["Metadata (Title, Author, Pages)"]
        DOC_ROOT --> BLOCKS["List of Block Elements"]
        
        BLOCKS --> B_HEAD["Heading (level 1-6)"]
        BLOCKS --> B_PARA["Paragraph (runs)"]
        BLOCKS --> B_CODE["CodeBlock (code, lang)"]
        BLOCKS --> B_LIST["List (ordered?, items)"]
        BLOCKS --> B_TABLE["Table (rows, cells, headers)"]
        BLOCKS --> B_QUOTE["Blockquote (runs)"]
        BLOCKS --> B_RULE["Horizontal Rule"]

        B_HEAD --> RUNS["TextRun (text, bold, italic, mono, underline, color)"]
        B_PARA --> RUNS
        B_QUOTE --> RUNS
    end

    subgraph TARGETS ["Output Renderers"]
        TGT_PDF["Apache PDFBox 3.0 (.pdf)"]
        TGT_DOCX["Apache POI XWPF (.docx)"]
        TGT_ODT["OpenDocument ZIP (.odt)"]
        TGT_HTML["Responsive HTML5 (.html)"]
        TGT_MD["GitHub Markdown (.md)"]
        TGT_TXT["ASCII Plain Text (.txt)"]
        TGT_DOC["Word HTML Doc (.doc)"]
        TGT_IMG["PDFBox Page Renderer (Images)"]
    end

    SRC_PDF -->|"PDFTextStripper"| DOC_ROOT
    SRC_DOCX -->|"XWPFDocument"| DOC_ROOT
    SRC_DOC -->|"HWPFDocument"| DOC_ROOT
    SRC_ODT -->|"content.xml DOM"| DOC_ROOT
    SRC_HTML -->|"Tag Tokenizer"| DOC_ROOT
    SRC_MD -->|"Syntax Parser"| DOC_ROOT
    SRC_TXT -->|"Line Parser"| DOC_ROOT

    DOC_ROOT -->|"Multi-page Layout Engine"| TGT_PDF
    DOC_ROOT -->|"XWPF Paragraphs/Tables"| TGT_DOCX
    DOC_ROOT -->|"ODF XML ZIP Packaging"| TGT_ODT
    DOC_ROOT -->|"Semantic HTML + CSS"| TGT_HTML
    DOC_ROOT -->|"GFM Markdown Generator"| TGT_MD
    DOC_ROOT -->|"ASCII Table Generator"| TGT_TXT
    DOC_ROOT -->|"Word Namespace HTML"| TGT_DOC
    DOC_ROOT -->|"PDF -> PDFRenderer"| TGT_IMG
```

---

## 6. Component Interaction Sequence Diagram

This sequence diagram depicts the exact control flow during a document conversion (e.g., Markdown $\to$ PDF):

```mermaid
sequenceDiagram
    autonumber
    actor User as User Terminal
    participant CLI as ConveUlt
    participant FMT as Format
    participant DE as DocumentEngine
    participant DM as DocumentModel
    participant PB as Apache PDFBox
    participant FS as File System

    User->>CLI: conve-ult README.md -o output.pdf --title "Guide"
    CLI->>CLI: parseFlags() & parsePositional()
    CLI->>FMT: fromFile(input) -> Format.MD
    CLI->>FMT: fromFile(output) -> Format.PDF
    CLI->>DE: convert(in, out, Format.PDF, options, "Guide", null, 45f)
    
    rect rgb(240, 248, 255)
        note over DE,DM: Parsing Phase
        DE->>FS: Files.readString("README.md")
        FS-->>DE: Raw Markdown text
        DE->>DM: new Doc("README")
        DE->>DM: addBlock(Block.heading(1, "Title"))
        DE->>DM: addBlock(Block.paragraph(runs))
        DE->>DM: addBlock(Block.table(rows))
    end

    rect rgb(255, 248, 240)
        note over DE,PB: Rendering Phase
        DE->>PB: new PDDocument()
        DE->>PB: new PDPage(PDRectangle.LETTER)
        DE->>PB: new PDPageContentStream(doc, page)
        loop Each Block in Doc
            alt Heading Block
                DE->>PB: setFont(Helvetica-Bold, sz)
                DE->>PB: showText(wrappedLine)
            else Table Block
                DE->>PB: addRect(cellBackground)
                DE->>PB: stroke(gridBorders)
                DE->>PB: showText(cellContent)
            else Page Break Needed
                DE->>PB: stream.close()
                DE->>PB: new PDPage() & new stream
            end
        end
        DE->>PB: drawFooters("Page X of Y")
        DE->>PB: pdf.save("output.pdf")
        DE->>PB: pdf.close()
    end

    DE-->>CLI: Finished rendering
    CLI->>FS: check output size (bytes)
    CLI->>User: ✔ SUCCESS: Created: output.pdf (2.4 KB, 280 ms)
```

---

## 7. Class & Object Structure Diagram

The entire application relies on a streamlined set of cohesive classes:

```mermaid
classDiagram
    class ConveUlt {
        +main(args: String[])
        -handleConvert(args: String[])
        -handleBatch(args: String[])
        -handleInfo(args: String[])
        -parseFlags(args: String[]) Map
        -parsePositional(args: String[]) List
        +printBanner()
        +printHelp()
        +printFormats()
    }

    class Format {
        <<enumeration>>
        PNG
        JPG
        WEBP
        GIF
        BMP
        TIFF
        PDF
        DOCX
        DOC
        ODT
        HTML
        MD
        TXT
        +isImage() boolean
        +isDocument() boolean
        +getPrimaryExtension() String
        +fromExtension(ext: String) Format
        +fromFile(file: File) Format
    }

    class ImageEngine {
        +convert(in: File, out: File, fmt: Format, opts: Options) List~File~
        +transform(src: BufferedImage, opts: Options) BufferedImage
        +writeImage(img: BufferedImage, out: File, fmt: Format, opts: Options)
        +convertImageToPdf(img: BufferedImage, out: File)
        +convertPdfToImages(in: File, out: File, fmt: Format, opts: Options) List~File~
        +inspect(file: File) Map~String, String~
    }

    class DocumentEngine {
        +convert(in: File, out: File, fmt: Format, imgOpts: Options, title: String, author: String, margin: Float)
        +parse(file: File, fmt: Format, ext: String) Doc
        +render(doc: Doc, out: File, fmt: Format, ext: String, imgOpts: Options, margin: Float)
        -parsePdf(file: File) Doc
        -parseDocx(file: File) Doc
        -parseDoc(file: File) Doc
        -parseOdt(file: File) Doc
        -parseHtml(file: File) Doc
        -parseMarkdown(file: File) Doc
        -renderPdf(doc: Doc, out: File, margin: Float)
        -renderDocx(doc: Doc, out: File)
        -renderOdt(doc: Doc, out: File)
        -renderHtml(doc: Doc, out: File)
        -renderMarkdown(doc: Doc, out: File)
    }

    class DocumentModel {
        <<container>>
    }

    class Doc {
        -title: String
        -author: String
        -pageCount: int
        -blocks: List~Block~
        +addBlock(b: Block) Doc
        +getWordCount() int
    }

    class Block {
        -type: Type
        -level: int
        -runs: List~Run~
        -code: String
        -tableRows: List~List~Cell~~
        -listItems: List~Item~
        +heading(level, text) Block
        +paragraph(runs) Block
        +code(code, lang) Block
        +table() Block
        +list(ordered) Block
    }

    class Run {
        -text: String
        -bold: boolean
        -italic: boolean
        -underline: boolean
        -monospace: boolean
        -color: String
        -linkUrl: String
    }

    ConveUlt ..> Format : queries
    ConveUlt ..> ImageEngine : delegates image ops
    ConveUlt ..> DocumentEngine : delegates document ops
    DocumentEngine ..> DocumentModel : builds & reads
    DocumentModel *-- Doc
    Doc *-- Block
    Block *-- Run
```

---

## 8. Step-by-Step Code Execution Trace

### Example: Converting an Image to Compressed WebP
```bash
conve-ult banner.png -o banner.webp --width 1200 --quality 80
```

1. **CLI Invocation:**
   - `bin/conve-ult` calls `java -cp target/conve-ult.jar:lib/* com.conveult.ConveUlt banner.png -o banner.webp --width 1200 --quality 80`.
2. **Flag Parsing (`ConveUlt.parseFlags`):**
   - Populates `opts["output"] = "banner.webp"`, `opts["width"] = "1200"`, `opts["quality"] = "80"`.
   - `extractImageOptions()` creates `ImageEngine.Options` with `width = 1200`, `quality = 0.80f`, `keepAspectRatio = true`, `fitMode = "contain"`.
3. **Format Detection (`Format.fromFile`):**
   - `inFmt = Format.PNG` (`isImage() = true`).
   - `targetFmt = Format.WEBP` (`isImage() = true`).
4. **Image Loading (`ImageEngine.convert`):**
   - Calls `ImageIO.read(inputFile)`. TwelveMonkeys decodes PNG into `BufferedImage` (e.g. $2400 \times 1600$).
5. **Transformation (`ImageEngine.transform`):**
   - $ow = 2400$, $oh = 1600$, requested width $tw = 1200$.
   - Since `keepAspectRatio = true` and height is unconstrained: $th = 1600 \times (1200 / 2400) = 800$.
   - Allocates new `BufferedImage(1200, 800, TYPE_INT_ARGB)`.
   - `Graphics2D` scales down using `RenderingHints.VALUE_INTERPOLATION_BICUBIC`.
6. **Encoding (`ImageEngine.writeImage`):**
   - Locates WebP writer.
   - Encodes image at quality `0.80f`.
   - Writes directly to `banner.webp`.
7. **Terminal Feedback:**
   - Computes initial size vs final size, calculates savings percentage (e.g. `[Reduced by 85.3%]`), and prints:
     ```
     ✔ SUCCESS: Created: /path/to/banner.webp (28.4 KB [Reduced by 85.3%], 145 ms)
     ```

---

## 9. Resilience, Fallbacks & Safety Mechanics

| Risk / Failure Mode | Safety Mechanism in conve-ult |
| :--- | :--- |
| **Accidental File Overwrite** | Checks `output.exists()`. If true and no `-f` or `--force` flag is passed, stops immediately with exit code `2` to prevent data loss. |
| **Disguised / Misnamed `.doc` files** | Legacy `.doc` files created by third-party apps are often misnamed HTML, RTF, or ZIP/DOCX files. `DocumentEngine.parseDoc()` checks the first 8 magic bytes: `PK` delegates to `parseDocx`, `<` delegates to `parseHtml`, `{\rtf` delegates to `parseText`. |
| **Unsupported PDF Type 1 Glyphs** | Standard 14 PDF fonts use WinAnsiEncoding. Unicode emojis or characters outside 0-255 throw `IllegalArgumentException`. `DocumentEngine.sanitize()` automatically translates smart quotes, em-dashes, and bullets into compliant ASCII equivalents before PDFBox touches them. |
| **Transparency in RGB-Only Formats** | When saving transparent PNGs as JPEG or BMP (which do not support alpha channels), `ImageEngine.writeImage()` automatically flattens alpha onto a clean white background, avoiding black box artifacts. |
| **Noisy JVM / Font Logs in Terminal** | Apache PDFBox, FontBox, and Log4j StatusLogger emit warnings for missing local system fonts. `ConveUlt` silences these loggers programmatically at class-load time to ensure clean, readable CLI output. |
