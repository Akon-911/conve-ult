#!/usr/bin/env bash
# ==============================================================================
# conve-ult — Universal Build Script for Linux & macOS
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building conve-ult (Linux & macOS) ==="

OS="$(uname -s)"
ARCH="$(uname -m)"

# Auto-download OpenJFX SDK if missing
if [ ! -d "javafx-sdk/lib" ]; then
    echo "Notice: JavaFX SDK not found in ./javafx-sdk."
    JFX_URL=""
    if [ "$OS" = "Darwin" ]; then
        if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
            echo "Detected macOS (Apple Silicon arm64)"
            JFX_URL="https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_osx-aarch64_bin-sdk.zip"
        else
            echo "Detected macOS (Intel x86_64)"
            JFX_URL="https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_osx-x64_bin-sdk.zip"
        fi
    else
        echo "Detected Linux ($ARCH)"
        JFX_URL="https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_linux-x64_bin-sdk.zip"
    fi

    echo "Fetching OpenJFX 21 LTS from: $JFX_URL"
    mkdir -p /tmp/jfx_dl
    if command -v curl >/dev/null 2>&1; then
        curl -sL -o /tmp/jfx_dl/openjfx.zip "$JFX_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O /tmp/jfx_dl/openjfx.zip "$JFX_URL"
    else
        echo "Error: Neither curl nor wget found. Please download $JFX_URL manually into ./javafx-sdk"
        exit 1
    fi

    unzip -q /tmp/jfx_dl/openjfx.zip -d /tmp/jfx_dl
    mv /tmp/jfx_dl/javafx-sdk-21.0.6 ./javafx-sdk
    rm -rf /tmp/jfx_dl
    echo "OpenJFX installed successfully into ./javafx-sdk"
fi

# Build classpath from lib/*.jar and javafx-sdk/lib/*.jar
CP=""
for jar in lib/*.jar javafx-sdk/lib/*.jar; do
    if [ -f "$jar" ]; then
        if [ -z "$CP" ]; then
            CP="$jar"
        else
            CP="$CP:$jar"
        fi
    fi
done

# Create target directories
mkdir -p target/classes

# Copy resources (stylesheets, icons) to target/classes
if [ -d "src/main/resources" ]; then
    cp -r src/main/resources/* target/classes/
fi

# Find all java source files
SOURCES=$(find src/main/java -name "*.java")

echo "Compiling Java sources with javac..."
javac -encoding UTF-8 -cp "$CP" -d target/classes $SOURCES

echo "Packaging target/conve-ult.jar..."
jar cfe target/conve-ult.jar com.conveult.ConveUltMain -C target/classes .

# Ensure executable permissions on launchers
chmod +x bin/conve-ult build.sh 2>/dev/null || true
if [ -L "conve-ult" ] || [ -f "conve-ult" ]; then
    chmod +x conve-ult 2>/dev/null || true
fi

echo "Build successful! Created target/conve-ult.jar"
