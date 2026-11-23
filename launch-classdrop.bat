@echo off
title ClassDrop — Offline Classroom File & Code Sharing Hub
echo ================================================================
echo   ClassDrop — Offline Classroom File Sharing Hub (KJSIM MCA)
echo   Farhan Sayed - MCA Mini Project
echo ================================================================
echo.

:: Check Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java 17+ is required but not found in PATH.
    echo Please install OpenJDK 17+ or Temurin JDK and try again.
    pause
    exit /b 1
)

:: Check if JAR exists
if not exist "target\classdrop-1.0.0.jar" (
    echo [INFO] target\classdrop-1.0.0.jar not found. Building project with Maven...
    call mvn clean package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Maven build failed.
        pause
        exit /b 1
    )
)

echo [INFO] Starting ClassDrop Host Hub...
echo [TIP]  For classroom usage, enable Windows Mobile Hotspot (192.168.137.1)
echo        to bypass Somaiya campus Wi-Fi AP client isolation.
echo.

java -jar target\classdrop-1.0.0.jar

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [NOTICE] ClassDrop exited with code %ERRORLEVEL%.
    pause
)
