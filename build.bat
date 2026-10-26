@echo off
rem ==============================================================================
rem conve-ult — Native Build Script for Windows (cmd / PowerShell)
rem ==============================================================================
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo === Building conve-ult (Windows x64) ===

rem Check for JDK tools
where javac >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: 'javac' compiler was not found in PATH.
    echo Please install Java Development Kit (JDK 17+) and ensure it is added to your PATH.
    exit /b 1
)

where jar >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: 'jar' utility was not found in PATH.
    exit /b 1
)

rem Create build directories
if not exist "target\classes" mkdir "target\classes"

rem Auto-download Windows OpenJFX SDK if missing
if not exist "javafx-sdk\lib" (
    echo Notice: JavaFX SDK not found in .\javafx-sdk.
    echo Fetching OpenJFX 21 LTS for Windows x64...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Write-Host 'Downloading OpenJFX 21...'; Invoke-WebRequest -Uri 'https://download2.gluonhq.com/openjfx/21.0.6/openjfx-21.0.6_windows-x64_bin-sdk.zip' -OutFile 'jfx_dl.zip'; Write-Host 'Extracting OpenJFX...'; Expand-Archive -Path 'jfx_dl.zip' -DestinationPath 'jfx_tmp'; Move-Item -Path 'jfx_tmp\javafx-sdk-21.0.6' -Destination 'javafx-sdk'; Remove-Item -Path 'jfx_dl.zip' -Force; Remove-Item -Recurse -Path 'jfx_tmp' -Force; Write-Host 'OpenJFX installed into .\javafx-sdk'"
)

rem Build classpath from lib\*.jar and javafx-sdk\lib\*.jar
set "CP="
for %%j in (lib\*.jar) do (
    if defined CP (
        set "CP=!CP!;%%j"
    ) else (
        set "CP=%%j"
    )
)
if exist "javafx-sdk\lib" (
    for %%j in (javafx-sdk\lib\*.jar) do (
        set "CP=!CP!;%%j"
    )
)

rem Collect all java source files
dir /s /b src\main\java\*.java > target\sources.txt

echo Compiling Java sources with javac...
javac -encoding UTF-8 -cp "%CP%" -d target\classes @target\sources.txt
if %errorlevel% neq 0 (
    echo Error during compilation!
    exit /b %errorlevel%
)

rem Copy resources (dark-theme.css, etc.)
if exist "src\main\resources" (
    xcopy /E /I /Y "src\main\resources\*" "target\classes\" >nul 2>&1
)

echo Packaging target\conve-ult.jar...
jar cfe target\conve-ult.jar com.conveult.ConveUltMain -C target\classes .
if %errorlevel% neq 0 (
    echo Error packaging JAR!
    exit /b %errorlevel%
)

rem Clean up temporary source list
if exist "target\sources.txt" del "target\sources.txt"

echo Build successful! Created target\conve-ult.jar
