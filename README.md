# ClassDrop — Offline Classroom File & Code Sharing Hub

> *"In the room, in the browser, off the cloud."*

**Author**: Farhan Sayed  
**Course**: Master of Computer Applications (MCA), K. J. Somaiya Institute of Management (KJSIM)  
**Academic Year**: 2026–2027  
**Technology**: Java 17+ · Swing GUI · JDK `HttpServer` · MySQL 8 JDBC · Vanilla HTML5/CSS3/JS · Pure-Java QR (`io.nayuki:qrcodegen`)

---

## 1. Problem & Context

In campus classrooms (such as Somaiya Vidyavihar), sharing large assignment PDFs, datasets, screenshots, or code snippets across laptops and mobile phones presents severe hurdles:
1. **Campus Wi-Fi Client Isolation**: Campus enterprise APs enforce client isolation, blocking peer-to-peer device discovery and pinging between student devices.
2. **Slow/Restricted Cloud Sharing**: Cloud services (Google Drive, WhatsApp) consume cellular data, require logins, have cloud upload delays, and expose classroom data to third-party servers.
3. **ToffeeShare Limitations in Campus**: While ToffeeShare is popular for P2P WebRTC sharing, WebRTC STUN/TURN handshakes fail under strict symmetric campus firewalls.

**ClassDrop Solution**: The presenter's laptop acts as the localized host hub via **Windows Mobile Hotspot** (`192.168.137.1`) or local LAN. Students connect over Wi-Fi without installing any app, navigate to `http://192.168.137.1:8080` (or scan the on-screen QR code), enter a 6-digit room code, and instantly share files and code snippets with zero cloud dependency.

---

## 2. Architecture & Data Flow

```
                      +-----------------------------------+
                      |         Student Devices           |
                      | (Chrome / Safari / Edge Browsers) |
                      +-----------------+-----------------+
                                        | HTTP (Port 8080)
                                        | Zero-install Web Client
                                        v
+---------------------------------------------------------------------------------+
|                         Host Laptop (ClassDrop Application)                     |
|                                                                                 |
|  +-------------------------+      +------------------------------------------+  |
|  |    Swing Desktop UI     |      |        JDK Built-in HttpServer           |  |
|  | - Live Transfers (EMA)  |      | - Port 8080, 16-thread bounded pool      |  |
|  | - Connected Devices     |      | - Streaming 64 KB chunk buffer           |  |
|  | - Shared Clipboard tab  |      | - Token authentication & 410 auto-expiry |  |
|  | - History & Search tab  |<---->| - Nayuki algorithmic QR engine           |  |
|  | - System Logs & Stats   |      +--------------------+---------------------+  |
|  +-------------------------+                           |                        |
|                                                        v                        |
|  +---------------------------------------------------------------------------+  |
|  |                           Service Layer                                   |  |
|  | - RoomService (Session lifecycle, 6-digit generator, brute-force lockout) |  |
|  | - FileService (64 KB chunk streaming to data/<session_id>/, 100 MB cap)   |  |
|  | - ClipboardService (64 KB body cap, code language tagging, listeners)     |  |
|  | - SnapshotService (Java Object Serialization -> session.ser)              |  |
|  | - CleanupTask (Scheduled daemon every 30s: timeout, purge disk on exit)   |  |
|  +-------------------------------------+-------------------------------------+  |
|                                        |                                        |
|                                        v                                        |
|  +---------------------------------------------------------------------------+  |
|  |                  Persistence Layer (MySQL 8 JDBC)                         |  |
|  | - ConnectionFactory (graceful fallback to in-memory if MySQL offline)     |  |
|  | - SessionRepository, DeviceRepository, TransferRepository, SnippetRepo   |  |
|  | - Parameterized SQL queries (PreparedStatement, SQL-injection proof)      |  |
|  | - StatsService (real-time COUNT, SUM aggregate queries)                   |  |
|  +---------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------+
```

---

## 3. MCA Java Syllabus Implementation

| Syllabus Concept | ClassDrop Implementation |
|---|---|
| **Inheritance & Polymorphism** | Abstract class `SharedItem` extended by `ImageFile`, `DocumentFile`, and `CodeSnippet`. Polymorphic dispatch on `getKind()` and `getContentType()`. |
| **Interfaces** | `Transferable` implemented by `SharedItem`. `SessionListener` for event callbacks across server, services, and GUI. |
| **Multithreading** | JDK `HttpServer` configured with `Executors.newFixedThreadPool(16)`. Daemon scheduled executor in `CleanupTask` running every 30s. |
| **Synchronization** | Static `activeUserCount` on `RoomSession` with `synchronized` increment/decrement methods. Synchronized session collection access. |
| **Custom Exceptions** | Hierarchy under `ClassDropException`: `InvalidRoomCodeException`, `FileTooLargeException`, `TransferFailedException`, `SessionExpiredException`. |
| **Method Overloading** | Overloaded `FileService.send(Path)` and `FileService.send(Path, String roomCode)`. Multiple constructors across models and repositories. |
| **Collections & Generics** | `CopyOnWriteArrayList<Device>`, `ConcurrentHashMap<String, AtomicInteger>`, `List<SharedItem>`, `List<TransferRecord>`. |
| **File I/O & Streaming** | 64 KB buffer chunk-by-chunk streaming directly to network socket without loading entire files into memory (`readAllBytes()` banned). |
| **Java Object Serialization** | `SessionSnapshot implements Serializable` with `ObjectOutputStream` / `ObjectInputStream` for checkpoint recovery across app restarts. |
| **JDBC & SQL** | Parameterized `PreparedStatement` queries in `SessionRepository`, `DeviceRepository`, `TransferRepository`, and `SnippetRepository`. |
| **Swing GUI** | Dark-themed `JFrame`, `JTabbedPane`, `JTable` with custom table models (`TransferTableModel` with 50-sample EMA speed calculation, `DeviceTableModel`), `JTextField`, `JLabel`. |

---

## 4. Quick Start

### Prerequisites
- **JDK 17 or higher** (OpenJDK, Temurin, or Oracle JDK)
- **MySQL 8** (Optional; ClassDrop features **graceful degradation** and runs 100% in-memory if MySQL is offline)
- Windows 10/11 (with Mobile Hotspot capability for offline classroom environments)

### One-Click Launch (Windows)
Double click `launch-classdrop.bat` or run:
```cmd
launch-classdrop.bat
```

### Manual Maven Build & Run
```bash
# 1. Build and run all tests
mvn clean test

# 2. Package into self-contained fat JAR
mvn clean package

# 3. Launch application
java -jar target/classdrop-1.0.0.jar
```

---

## 5. Classroom Setup (Somaiya Campus Isolation Bypass)

1. On host laptop, click the Windows Action Center and enable **Mobile Hotspot**.
2. Connect mobile devices or other student laptops to the host's Wi-Fi hotspot.
3. Launch `launch-classdrop.bat`. ClassDrop automatically detects `192.168.137.1` and displays the 6-digit room code and QR code.
4. Have students open their phone cameras, scan the QR code (or visit `http://192.168.137.1:8080`), enter their name, and join.

### Students on Somaiya Wi-Fi / mobile data (Internet Share)

If phones are **not** on your hotspot, switch Swing Mode to **INTERNET**, set a passphrase, and share the public HTTPS QR/link. Requires host internet uplink + `cloudflared` (or `public.base.url`). Full steps: [docs/INTERNET_SHARE_GUIDE.md](docs/INTERNET_SHARE_GUIDE.md).

Offline strategy & readiness checklist: [docs/OFFLINE_SHARING_PLAN.md](docs/OFFLINE_SHARING_PLAN.md).  
If Windows blocks joins, run `scripts/allow-firewall-8080.bat` as Administrator.

---

## 6. Database Schema (`schema.sql`)

```sql
CREATE DATABASE IF NOT EXISTS classdrop;
USE classdrop;

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
  id CHAR(36) PRIMARY KEY,
  room_code CHAR(6) NOT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME NULL,
  mode VARCHAR(20) NOT NULL,
  host_ip VARCHAR(45) NOT NULL,
  INDEX idx_sessions_started (started_at)
);

-- Devices table
CREATE TABLE IF NOT EXISTS devices (
  id CHAR(36) PRIMARY KEY,
  session_id CHAR(36) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  ip_address VARCHAR(45) NULL,
  joined_at DATETIME NOT NULL,
  last_seen DATETIME NOT NULL,
  CONSTRAINT fk_devices_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

-- Transfers table
CREATE TABLE IF NOT EXISTS transfers (
  id CHAR(36) PRIMARY KEY,
  session_id CHAR(36) NOT NULL,
  device_id CHAR(36) NOT NULL,
  item_kind VARCHAR(20) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(80) NOT NULL,
  size_bytes BIGINT NOT NULL,
  content_type VARCHAR(120) NULL,
  direction VARCHAR(10) NOT NULL,
  status VARCHAR(20) NOT NULL,
  file_token CHAR(36) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_transfers_search (original_name, item_kind, created_at),
  CONSTRAINT fk_transfers_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);

-- Snippets table
CREATE TABLE IF NOT EXISTS snippets (
  id CHAR(36) PRIMARY KEY,
  session_id CHAR(36) NOT NULL,
  device_id CHAR(36) NOT NULL,
  title VARCHAR(120) NULL,
  language VARCHAR(40) NULL,
  body TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_snippets_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);
```

---

## 7. Viva Q&A Cheat Sheet

1. **Why not WebRTC like ToffeeShare?**
   *Answer*: ToffeeShare relies on WebRTC peer-to-peer data channels. WebRTC requires external STUN/TURN servers on the public Internet for NAT traversal, which fails under Somaiya's strict symmetric NAT/firewall. Furthermore, ClassDrop is designed for syllabus evaluation of client-server network architecture, socket streaming, thread pools, synchronized state, and JDBC persistence on the host laptop.
2. **How is large file upload handled without OutOfMemoryError?**
   *Answer*: ClassDrop never reads files completely into RAM. `MultipartParser` uses a continuous sliding boundary buffer that streams incoming chunks (64 KB) directly into a staged disk file. Downloads stream byte chunks directly from disk to the HTTP output stream.
3. **What happens if MySQL is not running during a classroom presentation?**
   *Answer*: `ConnectionFactory.isAvailable()` detects database connectivity. If MySQL is offline, all repository operations gracefully fall back to in-memory mode without throwing unhandled exceptions or crashing the GUI.
4. **How are mobile screens kept awake during file uploads?**
   *Answer*: The web client includes `NoSleep.min.js`, which activates when the student joins the room to prevent mobile OS screen sleep from interrupting transfer streams.

---

## 8. Known Limitations & Enhancement Track

**Shipped in MVP v1.0.0**
- Offline LAN/Hotspot hub, 6-digit rooms, QR, streaming uploads/downloads, clipboard, Swing host UI, MySQL with in-memory fallback, snapshots, JUnit suite.
- **Internet Share**: public HTTPS tunnel + mandatory room passphrase for outside-network joins.

**Enhancement Plan C delivered** (see [docs/ENHANCEMENT_PLAN.md](docs/ENHANCEMENT_PLAN.md))
- Host file share from Swing, `/me` + `/stats` HTTP APIs, remove/kick controls, extension blocklist, HTTP Range resume, ZIP download-all, image preview, SSE updates. **77 automated tests passing.**

**Deferred / out of scope**
- UDP/mDNS nearby discovery, optional HTTPS/TLS, cloud relay mode.

### Maven note
Apache Maven 3.8+ must be on `PATH` to rebuild (`mvn clean package`). If Maven is missing, run the prebuilt fat JAR:
```cmd
java -jar target\classdrop-1.0.0.jar
```
