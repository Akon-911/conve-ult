# conve-ult — Universal File Converter 🚀

> **Fast, local, zero-daemon file converter powered by Java, Apache POI 5.2.5, Apache PDFBox 3.0.1, and OpenJFX 21.**  
> Supports both a sleek **Dark Mode Desktop GUI** and a high-speed **Headless CLI** for Linux, macOS, and Windows.

---

## 🌟 Key Features

- **Universal Format Matrix**: Convert across all popular Image and Document formats seamlessly.
- **Dark Mode JavaFX GUI**: Modern obsidian-slate interface with drag-and-drop, real-time image tuning, and batch conversion.
- **High-Speed Headless CLI**: Instant command-line conversions with progress indicators, benchmarks, and ANSI formatting.
- **Batch Processing**: Convert entire directories or queued files simultaneously with a single action.
- **100% Local & Offline**: Zero background daemons, zero cloud dependencies, and zero telemetry. Everything runs inside your local JVM.
- **Cross-Platform**: Full native support for **Linux**, **macOS** (Intel & Apple Silicon), and **Windows**.

---

## 📑 Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Building the Project](#2-building-the-project)
3. [How to Use the JavaFX GUI](#3-how-to-use-the-javafx-gui)
4. [How to Use the CLI](#4-how-to-use-the-cli)
   - [Basic Conversions](#basic-conversions)
   - [Image Options (Resize, Compress, Rotate)](#image-options)
   - [Document Options (PDF Pages, Margins)](#document-options)
   - [Batch Directory Conversions](#batch-directory-conversions)
   - [Inspecting File Metadata](#inspecting-file-metadata)
   - [Listing Formats](#listing-formats)
5. [Supported Formats Table](#5-supported-formats-table)
6. [Cross-Platform Compatibility](#6-cross-platform-compatibility)
7. [Technical Architecture & Internals](#7-technical-architecture--internals)

---

## 1. Prerequisites

- **Java Development Kit (JDK) 17 or higher** (OpenJDK, Oracle, Corretto, Temurin, etc.)
  - Verify your installation:
    ```bash
    java -version
    javac -version
    ```
- That's it! All core libraries (Apache POI, PDFBox, TwelveMonkeys) are bundled locally in `lib/`.

---

## 2. Building the Project

`conve-ult` provides independent, zero-dependency build scripts for all operating systems that do not require Maven or Gradle to be pre-installed.

### On Linux & macOS
Run the universal build script:
```bash
./build.sh
```
*The script automatically detects your OS and architecture (Intel or Apple Silicon), configures OpenJFX, compiles the Java sources, and packages `target/conve-ult.jar`.*

### On Windows
Run the Windows batch script:
```cmd
build.bat
```
*The batch script bootstraps OpenJFX for Windows if needed, compiles with `javac`, and produces `target\conve-ult.jar`.*

### Optional: Using Apache Maven
If you prefer Maven:
```bash
mvn clean package
```

---

## 3. How to Use the JavaFX GUI

Launch the desktop GUI anytime:

```bash
# On Linux / macOS:
./conve-ult gui
# (or simply run without flags in any desktop session)
./conve-ult

# On Windows:
conve-ult.bat gui
# (or double-click conve-ult.bat)
```

```
┌────────────────────────────────────────────────────────────────────────┐
│  conve-ult [v1.0.0]        [Single File]  [Batch Convert] [Supported Formats]
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │               Drag & drop your file here                       │   │
│   │              or click to browse from system                    │   │
│   │                                                                │   │
│   │                     [ Browse File... ]                         │   │
│   └────────────────────────────────────────────────────────────────┘   │
│                                                                        │
│   Conversion Settings                                                  │
│   ├── Target Format: [ WebP Image (.webp)                  ▼ ]         │
│   └── Output File:   [/home/user/photos/banner.webp        ] [Browse]  │
│                                                                        │
│   Image Optimization Options                                           │
│   ├── Quality: [ 85%  ─────────●──── ]   [x] Grayscale (B&W)          │
│   └── Resize:  [ 1920 ] x [ 1080 ] [x] Keep Ratio   Rotate: [ 0° ▼ ]   │
│                                                                        │
│   [  Convert File  ]   [====================]  ✔ Converted in 180 ms   │
│                                                   [ Open Folder ]      │
└────────────────────────────────────────────────────────────────────────┘
```

### GUI Highlights:
1. **Drag & Drop Dropzone**: Drop any supported image or document directly onto the window.
2. **Dynamic Format Selector**: Automatically filters compatible formats based on whether your input is an image or a document.
3. **Contextual Settings**:
   - **For Images**: Slider for quality compression (`10% - 100%`), custom dimensions with aspect-ratio lock, rotation (`90°`, `180°`, `270°`), and grayscale conversion.
   - **For Documents**: Specify page ranges (`1-5`, `all`) and custom margins.
4. **Asynchronous Non-Blocking Processing**: Heavy PDF rasterization and document conversions run on worker threads so the UI never stutters.
5. **Open Folder**: Instantly open the output directory in your system file manager upon completion.
6. **Batch Conversion Tab**: Queue multiple files or select an entire folder to batch convert in one click.

---

## 4. How to Use the CLI

The CLI is ideal for terminal workflows, automated scripts, and headless servers.

```bash
# Linux / macOS:
./conve-ult <input> -o <output> [options]

# Windows:
conve-ult.bat <input> -o <output> [options]
```

### Basic Conversions

Convert a Markdown file to PDF:
```bash
./conve-ult notes.md -o notes.pdf
```

Convert an image from PNG to modern WebP:
```bash
./conve-ult photo.png -o photo.webp
```

Auto-detect output path by specifying `--to <format>`:
```bash
# Generates report.docx in the same directory
./conve-ult report.pdf --to docx
```

Force overwrite existing destination file:
```bash
./conve-ult document.odt -o document.html -f
```

---

### Image Options

| Flag | Parameter | Description |
| :--- | :--- | :--- |
| `-w`, `--width` | `<px>` | Target width in pixels (e.g. `1920`) |
| `-h`, `--height` | `<px>` | Target height in pixels (e.g. `1080`) |
| `-s`, `--scale` | `<val>` | Scaling factor or percentage (e.g. `0.5`, `50%`, `2.0`) |
| `--keep-ratio` | — | Preserve aspect ratio (default: `true`) |
| `--no-keep-ratio`| — | Stretch image to exact width and height |
| `--fit` | `<mode>` | Fit mode: `contain` (default), `cover`, or `fill` |
| `-q`, `--quality`| `<1-100>`| Compression quality (default: `85` for JPG/WebP) |
| `--compress` | `<preset>` | Compression preset: `low`, `medium`, `high`, `max` |
| `--grayscale`, `--bw`| — | Convert image to black and white |
| `--rotate` | `<degrees>` | Rotate clockwise: `90`, `180`, `270` |
| `--dpi` | `<dpi>` | Target DPI for PDF $\to$ image rasterization (default: `150`) |

#### Examples:
```bash
# Resize image to width 1200 while preserving aspect ratio and compressing to 80% quality
./conve-ult banner.png -o banner.webp -w 1200 -q 80

# Convert image to grayscale and rotate 90 degrees clockwise
./conve-ult photo.jpg -o photo_bw.jpg --grayscale --rotate 90

# Rasterize a PDF to high-resolution 300 DPI PNG images
./conve-ult paper.pdf -o paper_page.png --dpi 300
```

---

### Document Options

| Flag | Parameter | Description |
| :--- | :--- | :--- |
| `--pages` | `<range>` | Page range for PDF conversion (e.g. `1-3`, `5`, `all`) |
| `--title` | `<text>` | Custom document title metadata |
| `--author` | `<text>` | Custom document author metadata |
| `--margin` | `<pt>` | Page margins in points (default: `40`) |

#### Examples:
```bash
# Convert specific pages of a PDF to Markdown
./conve-ult book.pdf -o summary.md --pages 1-5

# Convert Markdown to a PDF with custom margins and author
./conve-ult draft.md -o draft.pdf --title "Project Draft" --author "Alice" --margin 50
```

---

### Batch Directory Conversions

Convert an entire folder of files with a single command:
```bash
# Convert all images in ./raw_images to WebP and save to ./optimized
./conve-ult batch ./raw_images --to webp -o ./optimized -q 80

# Convert all markdown files in ./docs to PDF
./conve-ult batch ./docs --to pdf -o ./pdf_exports
```

---

### Inspecting File Metadata

Inspect dimensions, format, size, or word count of any file:
```bash
./conve-ult info photo.png
./conve-ult info document.docx
```

---

### Listing Formats

View all registered image and document formats:
```bash
./conve-ult formats
```

---

## 5. Supported Formats Table

### Image Formats
| Format | Extension | Capabilities |
| :--- | :--- | :--- |
| **PNG** | `.png` | Lossless, alpha transparency, bicubic resizing |
| **JPEG** | `.jpg`, `.jpeg` | Quality compression (`1-100%`), rotation, EXIF preservation |
| **WebP** | `.webp` | Modern high-efficiency web compression |
| **GIF** | `.gif` | Palette quantization |
| **BMP** | `.bmp` | Standard uncompressed bitmap |
| **TIFF** | `.tiff`, `.tif` | High dynamic range, multi-mode compression |
| **PDF** | `.pdf` | Image $\leftrightarrow$ PDF multi-page rendering |

### Document Formats
| Format | Extension | Engine | Capabilities |
| :--- | :--- | :--- | :--- |
| **PDF** | `.pdf` | Apache PDFBox 3.0 | Custom layout, dynamic fonts, tables, code blocks, footers |
| **DOCX** | `.docx` | Apache POI XWPF | Microsoft Word OpenXML, headings, styles, bullet lists, shaded tables |
| **DOC** | `.doc` | Apache POI HWPF | Word 97-2004 binary format support |
| **ODT** | `.odt` | OpenDocument XML | OASIS OpenDocument Text package format |
| **HTML** | `.html`, `.htm` | HTML5 Engine | Semantic responsive HTML5 markup with typography styling |
| **Markdown** | `.md` | Markdown Engine | GitHub Flavored Markdown (headings, code fences, tables, quotes) |
| **Plain Text** | `.txt`, `.log` | Plain Text Engine | Clean structured plain text with ASCII grid tables |

---

## 6. Cross-Platform Compatibility

`conve-ult` is designed from the ground up for seamless operation across:

| Platform | Build Script | Launcher | GUI Launch Command | CLI Command |
| :--- | :--- | :--- | :--- | :--- |
| **Linux (x86_64 / arm64)** | `./build.sh` | `./conve-ult` | `./conve-ult gui` | `./conve-ult in.md -o out.pdf` |
| **macOS (Intel & Apple Silicon)** | `./build.sh` | `./conve-ult` | `./conve-ult gui` | `./conve-ult in.md -o out.pdf` |
| **Windows 10 / 11 (x64)** | `build.bat` | `conve-ult.bat` | `conve-ult.bat gui` | `conve-ult.bat in.md -o out.pdf` |

- **Native File Openers**:
  - Linux: Opens containing folder with `xdg-open` / `gio open`.
  - macOS: Opens containing folder with `open`.
  - Windows: Opens containing folder with `explorer.exe`.

---

## 7. Technical Architecture & Internals

For in-depth technical documentation, including:
- **Intermediate Representation ($N \times M$ matrix AST)**
- **Mermaid Flowcharts, Sequence Diagrams & Class Diagrams**
- **Apache PDFBox & POI Integration Details**
- **Developer Guide for adding new formats**

Please see the comprehensive technical document:  
👉 **[DOCUMENTATION.md](DOCUMENTATION.md)**

---

## 📄 License
This project is open-source and free to use. Powered by Apache POI, Apache PDFBox, and OpenJFX.
