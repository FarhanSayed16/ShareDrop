# CLASSDROP: OFFLINE CLASSROOM FILE AND CODE SNIPPET SHARING SYSTEM

**A Mini-Project Report Submitted in Partial Fulfillment of the Requirements for the Degree of**  
**Master of Computer Applications (MCA)**

**By:**  
**Farhan Sayed**  
*(Roll No: 2026-MCA-042)*

**Under the Guidance of:**  
Faculty Guide, Department of Computer Applications  
**K. J. Somaiya Institute of Management (KJSIM)**  
Somaiya Vidyavihar University, Mumbai – 400077  
**Academic Year**: 2026 – 2027

---

## CERTIFICATE

This is to certify that the project entitled **"ClassDrop: Offline Classroom File and Code Snippet Sharing System"** is a bonafide work carried out by **Farhan Sayed** in partial fulfillment of the requirements for the award of the degree of **Master of Computer Applications (MCA)** from **K. J. Somaiya Institute of Management, Somaiya Vidyavihar University**, during the academic year 2026–2027.

\
\
__________________________  
**Project Guide**  
Department of Computer Applications  
KJSIM, Mumbai  

\
\
__________________________  
**Head of Department**  
Department of Computer Applications  
KJSIM, Mumbai  

---

## ACKNOWLEDGEMENTS

I would like to express my profound gratitude to our project guide and the faculty members of the Department of Computer Applications at K. J. Somaiya Institute of Management for their invaluable guidance, encouragement, and technical suggestions throughout the development of this project.

I also extend my sincere thanks to the IT Infrastructure team at Somaiya Vidyavihar for their insights regarding campus network topologies and access point isolation protocols, which inspired the offline architecture of ClassDrop.

Finally, I thank my fellow classmates for participating in multi-device network testing and load verification.

**— Farhan Sayed**

---

## ABSTRACT

In modern higher education environments, seamless peer-to-peer sharing of academic digital assets—such as large assignment PDFs, programming datasets, presentation slide decks, and formatted code snippets—is frequently hindered by enterprise network restrictions. Campus Wi-Fi networks typically enforce **access point client isolation**, blocking direct socket communication between student devices. Consequently, students depend on public cloud storage lockers (e.g., Google Drive, WhatsApp), consuming external cellular data and suffering latency across congested uplinks for classmates sitting in the same physical room. Furthermore, existing browser-based P2P tools such as ToffeeShare rely on WebRTC signaling, which fails under strict campus symmetric NAT firewalls.

This project presents **ClassDrop**, a localized, zero-cloud classroom file and code snippet sharing application. Developed in native Java 17+, ClassDrop enables a presenter’s laptop to act as a high-performance in-room server hub over Windows Mobile Hotspot (`192.168.137.1`) or local LAN. Students join instantaneously via any standard mobile or desktop browser by scanning a pure-Java generated QR code or entering a 6-digit room code, requiring **zero client software installation**. 

The system implements memory-safe streaming I/O in 64 KB sliding buffers (banning `readAllBytes()` to prevent `OutOfMemoryError` on 100 MB files), a bounded 16-thread pool using JDK `HttpServer`, atomic thread synchronization, a dark-themed Swing host dashboard with Exponential Moving Average (EMA) transfer speed smoothing, parameterized MySQL 8 JDBC audit persistence with graceful in-memory degradation, and Java Object Serialization for crash recovery. Comprehensive automated testing with JUnit 5 confirms 100% pass rates across security, concurrency, and streaming benchmarks.

---

## CHAPTER 1: INTRODUCTION

### 1.1 Background
The transition toward digital classroom learning has increased the frequency with which students and instructors exchange digital media during lectures and practical lab sessions. A typical MCA classroom session involves transferring compiler outputs, database dump scripts, lecture notes, and screenshots.

### 1.2 Problem Statement
In institutional campus networks such as Somaiya Vidyavihar:
1. **AP Client Isolation**: Enterprise Wi-Fi controllers isolate connected wireless clients from communicating with each other at Layer 2/Layer 3 to mitigate broadcast storms and internal malware spread.
2. **Bandwidth Wastage & Cellular Drain**: Using external cloud storage requires uploading to external data centers and redownloading across the same bottlenecked internet pipeline.
3. **P2P WebRTC Limitations**: WebRTC data channels require public STUN/TURN servers to facilitate NAT hole-punching. Symmetric campus firewalls frequently drop non-standard UDP traffic.
4. **Academic Syllabus Compliance**: Many existing tools are written in JavaScript/Node.js or Python, offering limited alignment with university Object-Oriented Java syllabus requirements.

### 1.3 Project Objectives
- Build a 100% Java host application running on the presenter's laptop that serves as an offline classroom hub.
- Create a zero-install, mobile-first browser client accessible to any phone or laptop on the local network.
- Eliminate dependency on the public internet by supporting Windows Mobile Hotspot (`192.168.137.1`).
- Enforce strict memory safety via chunk-based streaming to handle up to 100 MB files under tight JVM heap constraints.
- Provide real-time host monitoring via a Swing GUI and audit persistence via parameterized MySQL JDBC.

---

## CHAPTER 2: LITERATURE SURVEY & COMPARATIVE ANALYSIS

| Feature / Metric | Public Cloud Lockers (Drive / WhatsApp) | WebRTC P2P (ToffeeShare) | ClassDrop (Proposed System) |
|---|---|---|---|
| **Internet Requirement** | Mandatory (High bandwidth) | Mandatory (STUN/Signaling) | **Zero (100% Offline)** |
| **Campus Wi-Fi Isolation Bypass** | No (Cellular fallback) | Fails on Symmetric NAT | **Yes (Windows Mobile Hotspot)** |
| **Client Software Requirement** | Native App + Account Login | Modern Browser | **Zero-Install Web Browser** |
| **Host Architecture** | Cloud Datacenters | Ephemeral Browser Tab | **Java Host Server Hub** |
| **Host Dashboard** | None | Limited Web Stats | **Full Swing GUI with EMA Speed** |
| **Clipboard / Code Snippet Hub** | Separate app needed | File transfers only | **Integrated syntax clipboard** |
| **Data Privacy & Expiry** | Stored on 3rd-party servers | Ephemeral | **Host-only; purged on exit** |
| **Persistence & Audit** | Cloud account history | None | **Parameterized MySQL 8 JDBC** |
| **Crash State Recovery** | Server managed | None | **Java Object Serialization** |

---

## CHAPTER 3: SYSTEM ARCHITECTURE & DESIGN

### 3.1 High-Level Architectural Diagram

```
+---------------------------------------------------------------------------------+
|                                Student Devices                                  |
|            (Chrome / Safari / Edge on Android, iOS, Windows, macOS)             |
+---------------------------------------+-----------------------------------------+
                                        |
                                        | HTTP (Port 8080) / Zero-Install
                                        v
+---------------------------------------------------------------------------------+
|                        Host Laptop (ClassDrop Application)                      |
|                                                                                 |
|   +----------------------------+       +------------------------------------+   |
|   |     Swing Desktop GUI      |       |      JDK Built-in HttpServer       |   |
|   | - ClassDropFrame           |       | - 16-Thread Worker Pool            |   |
|   | - TransferTableModel (EMA) |       | - Streaming Chunk Buffers (64 KB)  |   |
|   | - DeviceTableModel         |<----->| - Token Auth & Rate Limiting       |   |
|   | - SearchPanel (History)    |       | - Nayuki Algorithmic QR Engine     |   |
|   | - Classroom Clipboard Tab  |       +-----------------+------------------+   |
|   +----------------------------+                         |                      |
|                                                          v                      |
|   +-------------------------------------------------------------------------+   |
|   |                               Service Layer                             |   |
|   | - RoomService: Session lifecycle, 6-digit codes, brute-force throttle   |   |
|   | - FileService: 64 KB chunk streaming to disk, 100 MB size enforcement   |   |
|   | - ClipboardService: 64 KB snippet body cap, language tagging, broadcast |   |
|   | - SnapshotService: Java Object Serialization (session.ser)              |   |
|   | - CleanupTask: Scheduled daemon (30s) auto-expiring & purging staging   |   |
|   +--------------------------------------+----------------------------------+   |
|                                          |                                      |
|                                          v                                      |
|   +-------------------------------------------------------------------------+   |
|   |                     Persistence Layer (MySQL 8 JDBC)                    |   |
|   | - ConnectionFactory: Auto-DDL init, credential masking, fallback        |   |
|   | - Repositories: Session, Device, Transfer, Snippet (PreparedStatement)  |   |
|   | - StatsService: Real-time SQL aggregations (COUNT, SUM)                 |   |
|   +-------------------------------------------------------------------------+   |
+---------------------------------------------------------------------------------+
```

### 3.2 Database Entity-Relationship (ER) Design

```
+-----------------------------------+          +-----------------------------------+
|             SESSIONS              | 1      * |              DEVICES              |
+-----------------------------------+----------+-----------------------------------+
| PK  id          CHAR(36)          |          | PK  id           CHAR(36)         |
|     room_code   CHAR(6)           |          | FK  session_id   CHAR(36)         |
|     started_at  DATETIME          |          |     display_name VARCHAR(80)      |
|     ended_at    DATETIME          |          |     ip_address   VARCHAR(45)      |
|     mode        VARCHAR(20)       |          |     joined_at    DATETIME         |
|     host_ip     VARCHAR(45)       |          |     last_seen    DATETIME         |
+-----------------+-----------------+          +-----------------+-----------------+
                  | 1                                            | 1
                  |                                              |
                  | *                                            | *
+-----------------+-----------------+          +-----------------+-----------------+
|            TRANSFERS              |          |              SNIPPETS             |
+-----------------------------------+          +-----------------------------------+
| PK  id            CHAR(36)        |          | PK  id          CHAR(36)          |
| FK  session_id    CHAR(36)        |          | FK  session_id  CHAR(36)          |
| FK  device_id     CHAR(36)        |          | FK  device_id   CHAR(36)          |
|     item_kind     VARCHAR(20)     |          |     title       VARCHAR(120)      |
|     original_name VARCHAR(255)    |          |     language    VARCHAR(40)       |
|     stored_name   VARCHAR(80)     |          |     body        TEXT              |
|     size_bytes    BIGINT          |          |     created_at  DATETIME          |
|     content_type  VARCHAR(120)    |          +-----------------------------------+
|     direction     VARCHAR(10)     |
|     status        VARCHAR(20)     |
|     file_token    CHAR(36)        |
|     created_at    DATETIME        |
+-----------------------------------+
```

---

## CHAPTER 4: IMPLEMENTATION METHODOLOGY

### 4.1 Bounded Multithreading & Memory-Safe Chunk Streaming
To satisfy strict resource constraints on student laptops, ClassDrop uses `com.sun.net.httpserver.HttpServer` backed by:
```java
server.setExecutor(Executors.newFixedThreadPool(16));
```
Incoming file streams are parsed via a zero-dependency `MultipartParser` that utilizes a sliding memory buffer. Bytes are written directly to staged disk files in 64 KB chunks (`byte[65536]`). Memory allocation remains flat regardless of whether the uploaded file is 10 KB or 100 MB.

### 4.2 Algorithmic Pure-Java QR Code Engine
Rather than relying on internet-dependent cloud QR APIs (such as Google Charts or QuickChart), ClassDrop embeds `io.nayuki:qrcodegen:1.8.0`. The QR matrix is generated algorithmically and rendered directly into:
1. An AWT `BufferedImage` for the Swing host UI thumbnail.
2. A PNG byte stream served over `GET /qr` for connected peers.

### 4.3 Exponential Moving Average (EMA) Speed Calculation
Raw instantaneous network transfer speed fluctuates wildly. In `TransferTableModel`, transfer speed is smoothed using a 50-sample Exponential Moving Average:
$$S_{new} = \frac{49 \cdot S_{prev} + \frac{\Delta \text{Bytes}}{\Delta t}}{50}$$

### 4.4 Mobile Screen Sleep Prevention (`NoSleep.js`)
Mobile operating systems suspend background network sockets when the screen dims or locks, severing active HTTP upload/download connections. ClassDrop bundles `NoSleep.min.js`. Upon room join submission, `noSleep.enable()` initializes an invisible HTML5 video playback loop, maintaining active display wake-locks during data transfers.

### 4.5 Persistence with Graceful Degradation
All database transactions utilize JDBC `PreparedStatement` to ensure SQL-injection immunity. If MySQL is offline during a classroom presentation, `ConnectionFactory.isAvailable()` logs an informational warning and safely falls back to in-memory mode, ensuring zero GUI crashes or unhandled runtime exceptions.

### 4.6 Java Object Serialization (`SessionSnapshot`)
ClassDrop provides crash resilience through standard Java Object Serialization:
- `SessionSnapshot implements Serializable` aggregates the active `RoomSession`, `Device` roster, `SharedItem` metadata, and clipboard entries.
- Transient string-backed paths prevent Windows-specific `sun.nio.fs.WindowsPath` serialization failures.
- State is serialized via `ObjectOutputStream` to `data/session.ser` on shutdown and can be reloaded on restart via the `--restore` command-line flag or the GUI **Restore Snapshot** button.

---

## CHAPTER 5: MCA SYLLABUS CONCEPT MAPPING

| Syllabus Topic | Practical ClassDrop Implementation |
|---|---|
| **Classes & Objects** | Encapsulated entity models: `Device`, `RoomSession`, `TransferRecord`, `ClipboardEntry`. |
| **Inheritance** | Base abstract class `SharedItem` extended by `ImageFile`, `DocumentFile`, and `CodeSnippet`. |
| **Interfaces** | `Transferable` implemented by `SharedItem`. `SessionListener` for decoupled event broadcasting. |
| **Method Overloading** | Multiple constructors across models and repositories; overloaded `FileService.send(Path)` methods. |
| **Exception Handling** | Custom hierarchy rooted at `ClassDropException`: `InvalidRoomCodeException`, `FileTooLargeException`, `SessionExpiredException`, `TransferFailedException`. |
| **Multithreading** | 16-thread fixed executor in `HttpServer`; daemon scheduled executor in `CleanupTask`. |
| **Synchronization** | Static `activeUserCount` updated atomically via `synchronized` methods; `CopyOnWriteArrayList` for thread-safe iteration. |
| **Collections Framework** | `ConcurrentHashMap`, `CopyOnWriteArrayList`, `List<T>`, `DefaultTableModel`, `DefaultListModel`. |
| **File I/O & Streams** | 64 KB chunked `InputStream` and `OutputStream` processing directly to network sockets without memory buffering. |
| **Serialization** | Object graph persistence using `ObjectOutputStream` and `ObjectInputStream` (`SessionSnapshot`). |
| **Database Connectivity (JDBC)** | Parameterized CRUD operations, connection pooling factory, SQL aggregate queries (`COUNT`, `SUM`). |
| **GUI Programming (Swing)** | Responsive desktop UI utilizing `JFrame`, `JTabbedPane`, `JTable`, `JTextField`, `JLabel`, `JButton`, and `JOptionPane`. |

---

## CHAPTER 6: TESTING & VERIFICATION RESULTS

### 6.1 Automated JUnit 5 Test Suite Summary
The project contains 66 automated tests across 17 test suites, achieving a **100% pass rate** in under 6 seconds:

| Test Suite Class | Tests Run | Result | Key Coverage |
|---|---|---|---|
| `RoomCodeGeneratorTest` | 3 | Pass | Entropy, length, format validation |
| `FilenameSanitizerTest` | 5 | Pass | Path traversal, null bytes, length caps |
| `ShareLinksTest` | 2 | Pass | WhatsApp & Email RFC 3986 deep-links |
| `RoomServiceTest` | 5 | Pass | Room lifecycle, token auth, brute-force |
| `FileServiceTest` | 4 | Pass | 64 KB chunking, 100 MB caps, purge |
| `ClipboardServiceTest` | 3 | Pass | 64 KB body caps, language tagging |
| `QrServiceTest` | 2 | Pass | Nayuki algorithm, PNG bytes, BufferedImage |
| `CleanupTaskTest` | 2 | Pass | Auto-expiry, stale device purge, disk cleanup |
| `DeviceTableModelTest` | 2 | Pass | Thread-safe row additions & updates |
| `TransferTableModelTest` | 3 | Pass | Progress, ETA, 50-sample EMA speed |
| `SearchPanelTest` | 2 | Pass | Parameterized LIKE search & filter rendering |
| `JdbcRepositoriesTest` | 7 | Pass | Session, Device, Transfer, Snippet, Aggregates, Fallback |
| `SnapshotServiceTest` | 3 | Pass | Full serialization round-trip across restarts |
| `ClassDropServerIntegrationTest` | 10 | Pass | Full HTTP workflow (join, upload, list, download) |
| `SecurityAndConcurrencyTest` | 7 | Pass | 403 traversal, 401 tokens, 410 end, 5 threads concurrent, 8 MB stream |
| `SharedItemTest` | 3 | Pass | Polymorphic kinds and MIME dispatch |
| **TOTAL** | **66** | **ALL PASS** | **Zero Failures, Zero Errors, Zero Skipped** |

---

## CHAPTER 7: LIMITATIONS & FUTURE ENHANCEMENTS

### 7.1 Current Limitations
1. **Local Transport Security**: Transmissions over local Hotspot/LAN utilize unencrypted HTTP. Devices on the same Wi-Fi subnet running promiscuous packet analyzers could capture raw packets.
2. **Hotspot Client Caps**: Typical consumer Windows Wi-Fi chipset hotspot adapters support a maximum of 8 to 12 concurrently connected client devices.
3. **Storage Caps**: Files are staged on the host laptop's local disk; transfers are bounded by host disk capacity and the configurable 100 MB default per-file cap.

### 7.2 Future Scope
- Implementation of local self-signed TLS/SSL encryption certificates for HTTPS.
- Automatic compression of multiple selected files into dynamic on-the-fly ZIP archives (`ZipOutputStream`).
- Multicast DNS (mDNS) / UDP broadcast beaconing for automatic room discovery without entering codes.
- HTTP `Range` header support for resumable chunk transfers on flaky Wi-Fi connections.

---

## CHAPTER 8: CONCLUSION

ClassDrop successfully bridges the gap between campus network security restrictions and classroom collaboration needs. By repurposing the presenter's laptop as a self-contained, offline HTTP server hub, it bypasses access point client isolation without requiring cloud infrastructure or client application installs. 

The application fulfills all criteria set forth in the MCA Java syllabus, proving that standard Java SE components—when paired with disciplined memory-safe streaming, multithreaded pooling, and robust exception handling—can deliver a modern, resilient, high-speed classroom file and code snippet sharing platform.

---

## REFERENCES

1. Schildt, Herbert. *Java: The Complete Reference*, 12th Edition. McGraw-Hill Education, 2021.
2. Oracle Corporation. *Class HttpServer Documentation*, Java Platform Standard Edition 17 API Specification, 2021.
3. Nayuki. *QR Code Generator Library (Java)*, Version 1.8.0, Project Nayuki, 2024.
4. Somaiya Vidyavihar University. *Master of Computer Applications (MCA) Syllabus & Regulations*, Department of Computer Applications, 2026.
5. Fielding, Roy T., et al. *Hypertext Transfer Protocol -- HTTP/1.1*, RFC 2616 / RFC 7230, Internet Engineering Task Force (IETF).
