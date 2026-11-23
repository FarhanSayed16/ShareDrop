# ClassDrop — Local Execution & Operations Guide

**Author**: Farhan Sayed — KJSIM MCA  
**Project**: ClassDrop — Classroom File & Code Snippet Sharing Hub  
**Target Environment**: Windows 10 / 11, macOS, Linux  

---

## 1. Prerequisites Checklist

Before running ClassDrop locally, verify that your machine has:

1. **Java Development Kit (JDK) 17 or higher**:
   ```powershell
   java -version
   ```
   *Expected output: OpenJDK / Temurin 17, 21, or 27.*
   If not installed, download from [Adoptium Temurin](https://adoptium.net/).

2. **Apache Maven 3.8+** (Optional, only needed if rebuilding source):
   ```powershell
   mvn -version
   ```

3. **MySQL 8** (Optional):
   ClassDrop includes **automatic in-memory fallback**. If MySQL is not running, the app starts and functions 100% normally in-memory without crashing.

---

## 2. Setting Up MySQL Database (Optional for History Persistence)

### Step 1: Start MySQL Service
- On Windows: Open `services.msc` and ensure **MySQL80** is **Running**.
- Or via PowerShell (Admin):
  ```powershell
  Start-Service MySQL80
  ```

### Step 2: Initialize Database Schema
Run the provided [schema.sql](file:///D:/ShareDrop/schema.sql) against your MySQL server:
```powershell
mysql -u root -p < schema.sql
```
*(Enter your MySQL root password when prompted).*

This creates the database `classdrop` and all 4 tables:
- `sessions`: Session ID, 6-digit room code, start time, end time, host IP, mode.
- `devices`: Connected student devices, names, IP addresses, join timestamps.
- `transfers`: Uploaded and downloaded file records, sizes, MIME kinds, status.
- `snippets`: Shared clipboard text and code snippets with language tags.

### Step 3: Configure Credentials (If Root Password Exists)
If your MySQL root user has a password, edit [src/main/resources/application.properties](file:///D:/ShareDrop/src/main/resources/application.properties) or create an `application.properties` in the project root:
```properties
db.url=jdbc:mysql://localhost:3306/classdrop
db.user=root
db.password=your_actual_password
```

---

## 3. Classroom Network Setup (Bypassing Campus Wi-Fi Isolation)

In campus environments (such as Somaiya Vidyavihar), university Wi-Fi access points enforce **client isolation**, preventing phones from talking to the host laptop.

### How to Enable Windows Mobile Hotspot:
1. Open Windows **Settings** $\rightarrow$ **Network & Internet** $\rightarrow$ **Mobile Hotspot**.
2. Turn the toggle to **ON**.
3. Configure the hotspot:
   - **Network Name**: `ClassDrop-Hub`
   - **Network Password**: `classdrop123` (or any 8-character password)
   - **Band**: 2.4 GHz or 5 GHz.
4. Windows assigns the host adapter the static IP: `192.168.137.1`.
5. ClassDrop automatically detects this adapter and selects `HOTSPOT` mode.

### Windows Firewall Configuration (One-Time Command):
Ensure Windows Firewall allows inbound TCP traffic on port 8080. Run PowerShell as **Administrator**:
```powershell
netsh advfirewall firewall add rule name="ClassDrop HTTP 8080" dir=in action=allow protocol=TCP localport=8080 profile=private
```

---

## 4. How to Run ClassDrop Locally

### Method 1: Double-Click Launcher (Fastest on Windows)
Simply double-click:
```
D:\ShareDrop\launch-classdrop.bat
```
This batch script automatically:
1. Validates that Java 17+ is installed.
2. Checks if `target/classdrop-1.0.0.jar` exists (builds it if missing).
3. Launches the application with the Swing Desktop GUI.

---

### Method 2: Running via Pre-Built Fat JAR
From the project root:
```powershell
java -jar target/classdrop-1.0.0.jar
```

#### Useful CLI Flags:
- **Restore previous session from snapshot**:
  ```powershell
  java -jar target/classdrop-1.0.0.jar --restore
  ```
  *(Or `-r`). Restores devices, file catalog, and clipboard from `data/session.ser`.*

- **Headless Mode (Server / Terminal only, no Swing GUI)**:
  ```powershell
  java -jar target/classdrop-1.0.0.jar --headless
  ```
  *(Or `-h`). Ideal for Linux servers or low-resource environments.*

---

### Method 3: Running / Testing from Maven Source
```powershell
# Run the complete test suite (66 automated tests)
mvn test

# Clean rebuild and package the 4.6 MB shaded fat JAR
mvn clean package -DskipTests
```

---

## 5. Host Desktop GUI Guide

When ClassDrop starts on your desktop:

1. **Header Panel**:
   - **Mode Badge**: Shows `HOTSPOT` (or `LAN`).
   - **Host IP**: Displays your IP (e.g. `192.168.137.1`).
   - **Room Code**: 6-digit large green display (e.g. `482917`).
   - **QR Code Thumbnail**: Automatically generated pure-Java QR code.
   - **Share Buttons**: Click **Copy**, **WhatsApp**, or **Email** to generate instant invite links (`http://192.168.137.1:8080/r/482917`).

2. **Center Tabs**:
   - **Live Transfers Tab**: Real-time table displaying filenames, sizes, directions (Upload/Download), smoothed transfer speed (EMA), and progress bars.
   - **Connected Devices Tab**: Live roster showing student display names, IP addresses, join times, and last seen timestamps.
   - **Classroom Clipboard Tab**: Live feed of shared code snippets and formulas with syntax language badges.
   - **History & Search Tab**: Query historical transfers from MySQL by filename (`LIKE %query%`), kind (`DOCUMENT`, `IMAGE`, `SNIPPET`), and date range.
   - **System Logs Tab**: Timestamped operational audit log.

3. **Status Bar Controls**:
   - **New Session**: Starts a fresh classroom room code.
   - **Save Snapshot**: Manually serializes active room state to `data/session.ser`.
   - **Restore Snapshot**: Restores the previous session state from disk.
   - **End Session**: Invalidates all tokens, deletes staged files from disk, and stops sharing.

---

## 6. Student / Client Device Walkthrough

Students join from their phones or laptops without installing any app:

1. Connect phone Wi-Fi to the host's **Mobile Hotspot**.
2. Open phone camera and scan the QR code on the laptop screen (or type `http://192.168.137.1:8080`).
3. The browser automatically opens `/r/<6-digit-code>` with the room code pre-filled.
4. Student enters their name (e.g., *Farhan*) and taps **Join Room**.
5. Once joined:
   - **Keep Screen Awake**: `NoSleep.min.js` automatically engages to prevent the phone from sleeping during transfers.
   - **Upload**: Tap *Browse Files* or drag-and-drop up to 100 MB files. Real-time upload percentage is displayed.
   - **Download**: Tap *⬇ Download* next to any shared file to stream it directly to their device.
   - **Clipboard**: Paste code snippets, select language (`Java`, `Python`, `SQL`, `Text`), and tap *Post Snippet*.

---

## 7. Troubleshooting & FAQ

| Problem | Root Cause | Solution |
|---|---|---|
| **Phone says "Site cannot be reached"** | Phone is on college Wi-Fi instead of host hotspot, or Firewall is blocking port 8080. | 1. Ensure phone is connected to host's hotspot.<br>2. Run the `netsh` firewall command in Section 3 as Administrator. |
| **"MySQL unavailable (running in-memory fallback)" in console** | MySQL service is stopped or port 3306 is not open. | Normal behavior. ClassDrop continues running 100% in-memory. If you want history saved to DB, start MySQL via `services.msc`. |
| **Port 8080 already in use** | Another app (Tomcat, Jenkins, Oracle XE) is using port 8080. | Edit `server.port=8085` in [application.properties](file:///D:/ShareDrop/src/main/resources/application.properties). |
| **Upload fails on phone when screen turns off** | Phone battery saver killed background network socket. | Make sure you join through the web UI where `NoSleep` is enabled, or keep the browser tab open during transfer. |
| **File transfer is rejected with 413** | File exceeds the configured max limit (default 100 MB). | Increase `max.file.size.bytes` in `application.properties` if larger files are needed. |
