#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Building conve-ult CLI ==="

# Build classpath from lib/*.jar
CP=""
for jar in lib/*.jar; do
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

# Find all java source files
SOURCES=$(find src/main/java -name "*.java")

echo "Compiling Java sources with javac..."
javac -encoding UTF-8 -cp "$CP" -d target/classes $SOURCES

echo "Packaging target/conve-ult.jar..."
jar cfe target/conve-ult.jar com.conveult.ConveUltMain -C target/classes .

echo "Build successful! Created target/conve-ult.jar"
