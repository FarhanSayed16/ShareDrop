# ClassDrop — 5-Minute Viva Presentation Script & Examiner Q&A Defense

**Student Name**: Farhan Sayed  
**Course**: Master of Computer Applications (MCA)  
**Institution**: K. J. Somaiya Institute of Management (KJSIM), Somaiya Vidyavihar  
**Project**: ClassDrop — Offline Classroom File & Code Snippet Sharing Hub  
**Stack**: Java 17+ · Swing GUI · JDK Built-in `HttpServer` · MySQL 8 JDBC · Vanilla HTML5/CSS3/JS · Nayuki Pure-Java QR

---

## 1. Minute-by-Minute Live Demo Script (Total: 5 Minutes)

### 0:00 – 1:15 | The Classroom Problem & Campus Wi-Fi Isolation
> *"Good morning, respected examiners. During practical labs and lectures in our college classrooms, sharing files—such as assignment PDFs, database dumps, presentation slides, or code snippets—is surprisingly difficult.*
>
> *Students usually upload large files to Google Drive or WhatsApp, only for classmates sitting three feet away to download them over overloaded cellular networks or campus Wi-Fi.*
>
> *More critically, enterprise campus networks like Somaiya's enforce **client isolation** on access points, preventing peer devices from discovering or connecting directly to one another. Furthermore, cloud P2P solutions like ToffeeShare rely on WebRTC, which fails behind strict campus symmetric firewalls.*
>
> *To solve this, I designed and built **ClassDrop**. The presenter's laptop acts as a high-performance localized server hub via Windows Mobile Hotspot at `192.168.137.1`. Students join instantly through their phone or laptop browsers by scanning a QR code or entering a 6-digit room code—with zero app installation, zero cloud relays, and zero external internet usage."*

---

### 1:15 – 2:00 | Host Dashboard Walkthrough (Swing GUI)
> *"Here on the host screen is the ClassDrop desktop dashboard built in pure Java Swing.*
>
> *At the top, we see:*
> 1. *The active network mode (**HOTSPOT**), prioritized automatically by `NetworkInfoService` over virtual adapters.*
> 2. *The host IP `192.168.137.1` and port `8080`.*
> 3. *The cryptographically secure 6-digit room code, generated via `SecureRandom`.*
> 4. *The dynamically generated QR code, computed algorithmically on the fly using `Nayuki QrCode` and rendered onto an AWT BufferedImage.*
> 5. *Direct WhatsApp and Email deep-link buttons using RFC 3986 URL encoding.*
> 6. *Our prominent notice: 'Files stay available only while host keeps ClassDrop running.'*
>
> *At the bottom, our status bar monitors active student count and session transfer volume in real-time."*

---

### 2:00 – 3:30 | Live Interactive Demonstration
*(Action: Scan QR code on your phone or open `http://192.168.137.1:8080` in a browser window)*
> *"Now, let's watch the client experience.*
>
> *1. When a student scans the QR code, the browser opens `/r/<6-digit-code>`. The room code is pre-filled automatically.*
> *2. I enter my name 'Farhan' and click **Join Room**.*
> *3. Notice on the host dashboard: the `DeviceTableModel` instantly updates showing the device name, IP address, and join timestamp.*
> *4. In the browser, `NoSleep.min.js` automatically activates, preventing mobile screens from sleeping during large downloads.*
>
> *Now I will upload an assignment PDF using the drag-and-drop drop zone:*
> *1. The web client uses `XMLHttpRequest.upload.onprogress` to display a live real-time progress bar.*
> *2. On the host, the `TransferTableModel` tracks the transfer in real-time, calculating upload speed smoothed by a 50-sample Exponential Moving Average (EMA).*
> *3. Once uploaded, the file appears in the shared room file list. Any other connected classmate can click 'Download', streaming the file directly from host disk in 64 KB chunks.*
>
> *Next, let's look at the **Classroom Clipboard**:*
> *I type a Java snippet into the browser, select language 'Java', and click 'Post Snippet'. It immediately broadcasts to the host Swing dashboard and all peer browser windows."*

---

### 3:30 – 4:15 | MCA Syllabus Architecture & Core Java Concepts
> *"ClassDrop was architected specifically to demonstrate mastery of the MCA Java syllabus:*
>
> 1. ***Inheritance & Polymorphism**: Abstract base class `SharedItem` implements `Transferable` and `Serializable`, extended by `ImageFile`, `DocumentFile`, and `CodeSnippet` with polymorphic method dispatch.*
> 2. ***Multithreading**: The JDK `HttpServer` runs a bounded 16-thread pool (`Executors.newFixedThreadPool(16)`), handling concurrent guest connections without blocking.*
> 3. ***Synchronization**: A static `activeUserCount` on `RoomSession` is updated via `synchronized` methods to prevent race conditions across HTTP handler worker threads.*
> 4. ***Memory-Safe I/O**: We strictly stream data in 64 KB buffers (`byte[65536]`). `readAllBytes()` is banned, allowing 100 MB files to be transferred with minimal JVM heap overhead.*
> 5. ***Custom Exceptions**: A clean exception hierarchy (`ClassDropException`, `InvalidRoomCodeException`, `FileTooLargeException`, `SessionExpiredException`, `TransferFailedException`).*
> 6. ***JDBC & Persistence**: If MySQL 8 is running, `SessionRepository`, `DeviceRepository`, `TransferRepository`, and `SnippetRepository` execute parameterized `PreparedStatement` queries. If MySQL is offline, `ConnectionFactory.isAvailable()` ensures 100% graceful degradation to in-memory mode without crashing.*
> 7. ***Java Serialization**: `SnapshotService` serializes active room state into `session.ser` via `ObjectOutputStream`, allowing instant checkpoint recovery across app restarts."*

---

### 4:15 – 5:00 | Session Termination, Cleanup & Conclusion
*(Action: Click 'End Session' on the host dashboard)*
> *"Finally, when class ends, the presenter clicks **End Session**:*
>
> *1. All tokens and the 6-digit room code are instantly invalidated.*
> *2. Any guest attempting further requests receives `HTTP 410 Gone`.*
> *3. Our background daemon `CleanupTask` purges the staged disk files in `data/<sessionId>/`, guaranteeing zero leftover clutter.*
> *4. Historical metadata remains safely stored in MySQL, searchable at any time in the 'History & Search' tab.*
>
> *In summary, ClassDrop provides a fast, cloud-free, zero-install classroom sharing solution built entirely in native Java. Thank you, and I welcome any questions."*

---

## 2. Examiner Q&A Defense — Top 15 Questions & Answers

### Q1: Why use built-in JDK `HttpServer` instead of Spring Boot?
**Answer**: Spring Boot is a massive framework adding dozens of megabytes of third-party JARs, reflection overhead, and auto-configuration magic that obscures core Java mechanics. The syllabus emphasizes understanding core network sockets, thread pooling, and standard Java library classes. JDK's `com.sun.net.httpserver.HttpServer` is lightweight, built directly into the standard runtime, boots in under 1 second, and demonstrates explicit thread pool management via `ExecutorService`.

### Q2: Why not WebRTC peer-to-peer like ToffeeShare?
**Answer**: ToffeeShare's WebRTC requires external STUN and TURN signaling servers hosted on the public cloud to exchange SDP offers and discover public IP/port mappings. On college campus Wi-Fi networks enforcing symmetric NAT and port blocking, WebRTC handshakes fail. ClassDrop utilizes the host laptop as a localized server hub over Windows Mobile Hotspot, creating a direct `192.168.137.1` subnet that works 100% offline without public internet.

### Q3: How do you handle 100 MB file uploads without causing an `OutOfMemoryError`?
**Answer**: We enforce streaming I/O. In `MultipartParser` and `FileService`, we read input bytes in 64 KB sliding chunks (`byte[65536]`) and write directly to an output stream backed by a disk file in `data/<sessionId>/`. We never call `InputStream.readAllBytes()` or load the full file into heap RAM. In fact, our unit test proves 10+ MB files stream cleanly with tight `-Xmx128m` JVM memory.

### Q4: What prevents path traversal attacks like `../../evil.sh`?
**Answer**: Filenames undergo strict sanitization via `FilenameSanitizer.sanitize()`. Path separators (`/`, `\`), null bytes (`\0`), and directory traversal sequences (`..`) are stripped, filenames are capped at 255 characters, and stored on disk under internal UUIDs (`storedId + extension`). Furthermore, `StaticHandler` explicitly checks static file paths and rejects any containing `..` with `403 Forbidden`.

### Q5: What happens if MySQL is not running on the host laptop?
**Answer**: ClassDrop features **graceful degradation**. `ConnectionFactory.isAvailable()` probes database availability with a 2-second login timeout. If MySQL is offline, all repository operations (`SessionRepository`, `TransferRepository`, etc.) log a warning and return cleanly without throwing unhandled `SQLException`s. The in-memory session and Swing dashboard continue running with 100% functionality.

### Q6: How does Java Serialization work in `SnapshotService`?
**Answer**: `SessionSnapshot implements Serializable`. It bundles the `RoomSession`, `Device` collection, `SharedItem` collection, and clipboard entries. We use `ObjectOutputStream.writeObject()` to serialize the graph to `session.ser`. Upon restart, `ObjectInputStream.readObject()` deserializes the state, verifies stored file paths on disk, and rehydrates the session. To prevent `sun.nio.fs.WindowsPath` serialization issues, `Path` fields in `SharedItem` are marked `transient` with `String` path backing.

### Q7: Why use `CopyOnWriteArrayList` instead of standard `ArrayList`?
**Answer**: `RoomSession.getDevices()`, `getItems()`, and `ClipboardService.entries` are accessed concurrently by up to 16 HTTP handler worker threads while being read by the Swing Event Dispatch Thread (EDT). A standard `ArrayList` throws `ConcurrentModificationException` if iterated while another thread adds an item. `CopyOnWriteArrayList` guarantees thread-safe read iterations without requiring heavyweight external locks.

### Q8: What does `volatile` and `synchronized` do in `activeUserCount`?
**Answer**: `activeUserCount` is a static variable shared across all threads. `volatile` guarantees CPU cache visibility, ensuring changes written by one core are immediately visible to all other threads. The `incrementActiveUsers()` and `decrementActiveUsers()` methods are declared `synchronized` to ensure atomic read-modify-write operations, preventing lost updates when multiple devices join or leave simultaneously.

### Q9: How is transfer speed calculated in `TransferTableModel`?
**Answer**: Real-time raw speed fluctuates wildly due to network burstiness. We apply an Exponential Moving Average (EMA) with a 50-sample smoothing factor:  
$$S_{new} = \frac{49 \cdot S_{prev} + \frac{\Delta \text{Bytes}}{\Delta t}}{50}$$  
This produces stable, realistic transfer speed values without erratic spikes.

### Q10: How do you prevent brute-force room code guessing?
**Answer**: `RoomService` maintains an atomic failed-attempt counter per IP (`ConcurrentHashMap<String, AtomicInteger>`). If an IP records 5 consecutive failed room code attempts, it is locked out for 60 seconds (`Constants.BRUTE_FORCE_WINDOW_SECONDS`), throwing an `InvalidRoomCodeException` with a cooldown notice.

### Q11: How do you keep mobile devices from sleeping during file transfers?
**Answer**: When a phone screen turns off, mobile operating systems suspend background network sockets, breaking HTTP file transfers. ClassDrop bundles `NoSleep.min.js`. When the student clicks 'Join Room', `new NoSleep().enable()` creates a hidden HTML5 video loop that keeps the mobile display active during transfers.

### Q12: Why is the QR code pure Java rather than an external web API?
**Answer**: External APIs like Google Charts QR require an active internet connection. ClassDrop must work 100% offline in basements or isolated campus labs. We use `io.nayuki:qrcodegen:1.8.0`, a pure-Java algorithmic QR generator with zero native or AWT dependencies, generating QR matrices directly in memory and rendering them to PNG byte arrays and Swing `BufferedImage` thumbnails.

### Q13: How is thread safety maintained between HTTP threads and Swing?
**Answer**: Swing components are single-threaded and must only be modified on the Event Dispatch Thread (EDT). All server callbacks implementing `SessionListener` (`onDeviceJoined`, `onTransferProgress`, `onTransferCompleted`) wrap UI updates inside `javax.swing.SwingUtilities.invokeLater(() -> { ... })`.

### Q14: How are SQL injection attacks prevented?
**Answer**: We never concatenate user inputs into SQL strings. Every repository query in `SessionRepository`, `DeviceRepository`, `TransferRepository`, and `SnippetRepository` uses parameterized `PreparedStatement` with `?` placeholders.

### Q15: What are the main limitations of ClassDrop?
**Answer**: 
1. Plain HTTP without TLS/SSL on local networks (anyone on the same hotspot with Wireshark could capture unencrypted packets).
2. Host laptop battery consumption and Wi-Fi hotspot hardware client limits (typically 8–12 devices per standard Windows hotspot adapter).
3. File sizes are capped at 100 MB because files are staged on the host laptop's local disk.

---

## 3. Day-of-Viva Readiness Checklist

- [ ] Host laptop fully charged and plugged into power.
- [ ] Windows Mobile Hotspot configured (Hotspot Name: `ClassDrop-Hub`, Network Band: 2.4 GHz or 5 GHz).
- [ ] Windows Firewall rule verified (`netsh advfirewall firewall add rule name="ClassDrop HTTP 8080" dir=in action=allow protocol=TCP localport=8080 profile=private`).
- [ ] Test student phone connected to hotspot.
- [ ] Double-click `launch-classdrop.bat` and confirm dashboard opens with `192.168.137.1:8080`.
- [ ] Phone scans QR code and confirms pre-filled `/r/<6-digit-code>` loads.
- [ ] Test sample files placed on Desktop (`Assignment_Notes.pdf`, `Architecture_Diagram.png`).
- [ ] Backup screen recording video accessible in a separate folder in case of hardware issues.
