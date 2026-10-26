#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building conve-ult (CLI + JavaFX GUI) ==="

# Auto-download JavaFX SDK if missing
if [ ! -d "javafx-sdk/lib" ]; then
    echo "JavaFX SDK not found in ./javafx-sdk. Fetching OpenJFX 21 LTS..."
    mkdir -p /tmp/jfx_dl
    curl -sL -o /tmp/jfx_dl/openjfx.zip https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_linux-x64_bin-sdk.zip
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

echo "Build successful! Created target/conve-ult.jar"
