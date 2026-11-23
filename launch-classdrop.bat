@echo off
setlocal
cd /d "%~dp0"

title ClassDrop Host
chcp 437 >nul

echo ================================================================
echo   ClassDrop - Offline Classroom File Sharing Hub
echo ================================================================
echo.
echo Project folder: %CD%
echo.

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found in PATH.
    echo Install Temurin/OpenJDK 17+ and try again.
    pause
    exit /b 1
)

echo [INFO] Java found:
java -version
echo.

if not exist "target\classdrop-1.0.0.jar" (
    echo [ERROR] Missing file: target\classdrop-1.0.0.jar
    echo Build it first, or ask Cursor to rebuild the project.
    pause
    exit /b 1
)

echo [INFO] Starting ClassDrop...
echo [TIP] Keep this window open. A desktop window should appear.
echo [TIP] Then open browser: http://127.0.0.1:8080
echo.

java -jar "target\classdrop-1.0.0.jar"
set EXITCODE=%ERRORLEVEL%

echo.
echo [INFO] ClassDrop exited with code %EXITCODE%.
pause
exit /b %EXITCODE%
