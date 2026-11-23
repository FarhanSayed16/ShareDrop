#!/usr/bin/env bash
# ClassDrop — Offline Classroom File & Code Sharing Hub (KJSIM MCA)
# Farhan Sayed - MCA Mini Project

echo "================================================================"
echo "  ClassDrop — Offline Classroom File Sharing Hub (KJSIM MCA)   "
echo "  Farhan Sayed - MCA Mini Project                              "
echo "================================================================"
echo ""

if ! command -v java &> /dev/null; then
    echo "[ERROR] Java 17+ is required but not found in PATH."
    exit 1
fi

if [ ! -f "target/classdrop-1.0.0.jar" ]; then
    echo "[INFO] target/classdrop-1.0.0.jar not found. Building project with Maven..."
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "[ERROR] Maven build failed."
        exit 1
    fi
fi

echo "[INFO] Starting ClassDrop Host Hub..."
echo "[TIP]  For classroom usage, enable Mobile Hotspot or connect to the same Wi-Fi subnet."
echo ""

java -jar target/classdrop-1.0.0.jar "$@"
