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
    echo [INFO] JAR missing — building with portable Maven...
    set "MVN="
    if exist ".tools\apache-maven-3.9.6\bin\mvn.cmd" set "MVN=.tools\apache-maven-3.9.6\bin\mvn.cmd"
    if exist ".tools\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=.tools\apache-maven-3.9.9\bin\mvn.cmd"
    if "%MVN%"=="" (
        where mvn >nul 2>&1
        if not errorlevel 1 set "MVN=mvn"
    )
    if "%MVN%"=="" (
        echo [ERROR] Maven not found. Cannot build target\classdrop-1.0.0.jar
        echo Install Maven or keep the prebuilt JAR under target\
        pause
        exit /b 1
    )
    call %MVN% -f "%CD%\pom.xml" -q package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

if not exist "target\classdrop-1.0.0.jar" (
    echo [ERROR] Missing file: target\classdrop-1.0.0.jar
    pause
    exit /b 1
)

echo [INFO] Starting ClassDrop...
echo [TIP]  Keep this window open while sharing.
echo [TIP]  Offline class: Start Hotspot -^> OFFLINE READY -^> Fix Firewall once.
echo [TIP]  Local check:   http://127.0.0.1:8080
echo [TIP]  Students:      http://192.168.137.1:8080  (on teacher hotspot, mobile data OFF)
echo.

java -jar "target\classdrop-1.0.0.jar" %*
set EXITCODE=%ERRORLEVEL%

echo.
echo [INFO] ClassDrop exited with code %EXITCODE%.
pause
exit /b %EXITCODE%
