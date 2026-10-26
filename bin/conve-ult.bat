@echo off
rem ==============================================================================
rem conve-ult — Windows Launcher Script
rem ==============================================================================
setlocal enabledelayedexpansion

set "BIN_DIR=%~dp0"
set "BASE_DIR=%BIN_DIR%.."
cd /d "%BASE_DIR%"

rem Auto-build if target\conve-ult.jar does not exist
if not exist "%BASE_DIR%\target\conve-ult.jar" (
    call "%BASE_DIR%\build.bat"
    if %errorlevel% neq 0 exit /b %errorlevel%
)

rem Build classpath
set "CP=%BASE_DIR%\target\conve-ult.jar"
for %%j in ("%BASE_DIR%\lib\*.jar") do (
    set "CP=!CP!;%%j"
)

rem Configure JavaFX module options if available
set "MODULE_OPTS="
if exist "%BASE_DIR%\javafx-sdk\lib" (
    set MODULE_OPTS=--module-path "%BASE_DIR%\javafx-sdk\lib" --add-modules javafx.controls,javafx.graphics --enable-native-access=javafx.graphics --sun-misc-unsafe-memory-access=allow
)

rem Run Java Application
java -Dlog4j2.statusLoggerLevel=OFF -Dorg.apache.logging.log4j.simplelog.StatusLogger.level=OFF %MODULE_OPTS% -cp "%CP%" com.conveult.ConveUltMain %*
