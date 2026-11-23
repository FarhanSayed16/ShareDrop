# ClassDrop — Complete Master Execution Plan

**Version**: 1.0 FINAL  
**Date**: 23 November 2026  
**Author**: Farhan Sayed — KJSIM (KJ Somaiya Institute of Management), MCA  
**Stack**: Java 17+ · Swing · JDK HttpServer · JDBC / MySQL 8 · JUnit 5 · Vanilla HTML/CSS/JS  
**Source Documents**:
- [ClassDrop_Build_Plan.md](file:///D:/ShareDrop/docs/ClassDrop_Build_Plan.md)
- [TOFFEESHARE_SYSTEM_ANALYSIS_AND_BUILD_PLAN.md](file:///D:/ShareDrop/docs/TOFFEESHARE_SYSTEM_ANALYSIS_AND_BUILD_PLAN.md)
- [ClassDrop_Master_Execution_Plan.md](file:///D:/ShareDrop/docs/ClassDrop_Master_Execution_Plan.md)

> **IMPORTANT**: This is the single source of truth for project execution. Every checkbox below is a deliverable. Do not start the next phase until the current phase gate is green. Mark each item with ✅ as you complete it.

---

## How to Use This Document

1. Work **top-to-bottom, phase-by-phase**. Each phase has a gate at the end.
2. Inside each phase, sub-phases are numbered `P<phase>.<sub>`. Complete them in order unless marked *(parallel ok)*.
3. A **red gate** means: stop, fix, then re-test before moving forward.
4. Files and classes named here are the canonical names. Do not rename them without updating this document.
5. The `[ ]` checkboxes are your progress tracker. Replace `[ ]` with `[x]` as you finish each item.

---

## Project Identity

| Field | Value |
|---|---|
| Project Name | **ClassDrop — Offline Classroom File Sharing** |
| Package Root | `com.classdrop` |
| Default Port | `8080` |
| Room Code Length | 6 digits |
| Max File Size (default) | 100 MB (`104857600` bytes) |
| Chunk Buffer Size | 64 KB (`65536` bytes) |
| Thread Pool Size | 16 threads |
| Tagline | *"In the room, in the browser, off the cloud."* |
| Web Accent Colour | Teal `#0D9488` (not ToffeeShare orange) |
| Share URL Format | `http://<host-ip>:8080/r/<6-digit-code>` |

---

# PHASE 0 — Topic Approval & Environment Setup

**Timeline**: 23 November 2026 (today)

### P0.1 — Topic Submission
- [ ] Write abstract (§17 from ClassDrop_Build_Plan.md).
- [ ] Fill college approval form. Submit before end of day.

### P0.2 — JDK & Build Tool
- [ ] Confirm `java -version` → 17+. Confirm `javac -version`.
- [ ] Choose Maven or javac+lib. If Maven: confirm `mvn -v`, create skeleton `pom.xml`.

### P0.3 — MySQL
- [ ] MySQL 8 installed and running. Can connect with `mysql -u root -p`.

### P0.4 — Network Isolation Test
- [ ] `ipconfig` → note Wi-Fi IPv4.
- [ ] `python -m http.server 8080` → test from friend's device.
- [ ] Record: ping pass/fail, browser pass/fail, phone pass/fail.

### P0.5 — Hotspot Test
- [ ] Enable Windows Mobile Hotspot. `ipconfig` → note IP.
- [ ] Phone loads `http://<hotspot-ip>:8080`.

### P0.6 — Firewall Rule
- [ ] `netsh advfirewall firewall add rule name="ClassDrop HTTP 8080" dir=in action=allow protocol=TCP localport=8080 profile=private`

### P0.7 — Git Init
- [ ] Create project root. `git init`. `.gitignore`. Initial commit.

### GATE 0
- [ ] Topic submitted
- [ ] JDK 17+ confirmed
- [ ] MySQL running
- [ ] Isolation test recorded
- [ ] Hotspot tested
- [ ] Firewall rule added
- [ ] Git initialized

---

# PHASE 1 — Package Structure & Domain Model

**Timeline**: Day 2–3

### P1.1 — Packages
- [ ] Create: `model`, `exception`, `service`, `server`, `db`, `ui`, `util` under `com.classdrop`.
- [ ] Mirror in `src/test/`. Create `src/main/resources/web/`.

### P1.2 — `Transferable` Interface
- [ ] `getId()`, `getSizeBytes()`, `getContentType()`, `openStream()`.

### P1.3 — `SessionListener` Interface
- [ ] Events: deviceJoined, deviceLeft, transferStarted/progress/completed/failed, sessionStarted/ended, clipboardUpdated.

### P1.4 — Abstract `SharedItem`
- [ ] `implements Transferable, Serializable`. Two constructors (new + reload). Abstract: `getKind()`, `getContentType()`.

### P1.5 — Subclasses
- [ ] `ImageFile` (kind=IMAGE), `DocumentFile` (kind=DOCUMENT), `CodeSnippet` (kind=SNIPPET, has `language` and `body` fields).

### P1.6 — `NetworkMode` Enum
- [ ] `LAN`, `HOTSPOT`, `RELAY`.

### P1.7 — Model Classes
- [ ] `RoomSession` with static `activeUserCount`, synchronized methods, device/item collections.
- [ ] `Device` with `id`, `displayName`, `ipAddress`, `token`, `joinedAt`, `lastSeen`.
- [ ] `TransferRecord` with all transfer metadata fields.
- [ ] `ClipboardEntry` with `title`, `language`, `body`.
- [ ] `SessionSnapshot implements Serializable` (metadata only, no file bytes).

### P1.8 — Custom Exceptions
- [ ] `InvalidRoomCodeException` (→403), `FileTooLargeException` (→413), `TransferFailedException` (→500), `SessionExpiredException` (→410).

### P1.9 — Constants
- [ ] `PORT=8080`, `MAX_FILE_SIZE=100MB`, `ROOM_CODE_LENGTH=6`, `CHUNK_BUFFER_SIZE=65536`, `THREAD_POOL_SIZE=16`, `CLEANUP_INTERVAL_SECONDS=30`, `SESSION_TIMEOUT_MINUTES=120`.

### GATE 1
- [ ] All packages exist
- [ ] SharedItem hierarchy compiles
- [ ] Interfaces compile
- [ ] Exceptions compile
- [ ] All models compile
- [ ] `mvn compile` or `javac` zero errors

---

# PHASE 2 — Utilities & Unit Tests

**Timeline**: Day 3–4

### P2.1 — `RoomCodeGenerator`
- [ ] `SecureRandom`, 6 digits, avoids trivial codes. `generate()` + `isValidFormat()`.

### P2.2 — `RoomCodeGeneratorTest`
- [ ] 6 chars, all digits, format validation (valid/invalid/empty/null), uniqueness test.

### P2.3 — `FilenameSanitizer`
- [ ] `sanitize()`: strip path seps, `..`, control chars, cap 255. `rejectIfPathTraversal()`.

### P2.4 — `FilenameSanitizerTest`
- [ ] Clean pass-through, traversal rejection, empty handling, length cap, control char strip.

### P2.5 — `Ids` Utility
- [ ] `Ids.newId()` → `UUID.randomUUID().toString()`.

### P2.6 — `ShareLinks`
- [ ] `whatsappUrl(shareUrl)`, `emailUrl(shareUrl)` with proper URL encoding.

### P2.7 — `ShareLinksTest`
- [ ] URLs contain correct prefixes and encoded content.

### P2.8 — `NetworkInfoService`
- [ ] List non-loopback IPv4s, skip virtual adapters, prefer hotspot IP, detect mode.

### P2.9 — `application.properties` Template
- [ ] All config keys. No real passwords committed.

### GATE 2
- [ ] All utility tests pass
- [ ] `NetworkInfoService` returns correct IPs
- [ ] Config template created

---

# PHASE 3 — HTTP Server & Static Files

**Timeline**: Day 4–5

### P3.1 — `ClassDropServer`
- [ ] Binds `0.0.0.0:PORT`, bounded thread pool, `start()` + `start(int port)` + `stop()` + `isRunning()`.

### P3.2 — `HandlerSupport`
- [ ] `sendJson()`, `sendError()`, `readBody()`, `getQueryParam()`, `getHeader()`, `requireToken()`.

### P3.3 — `/health` Handler
- [ ] Returns `{"ok":true}`.

### P3.4 — `StaticHandler`
- [ ] Serves `resources/web/` files. Correct MIME types. Path traversal blocked.

### P3.5 — `RoomLinkHandler`
- [ ] `GET /r/{code}` → serves `index.html` with code extractable by JS.

### P3.6–3.8 — Minimal Web Files
- [ ] `index.html` (mobile-first join form, dark, teal accent).
- [ ] `app.css` (dark theme skeleton).
- [ ] `app.js` (pre-fill code from URL, join button placeholder).

### P3.9 — `App.java`
- [ ] Loads config, creates services, starts server, prints IPs and URL, shutdown hook.

### P3.10 — Smoke Test
- [ ] `localhost:8080/` loads page. `/health` returns JSON. Static traversal blocked.

### GATE 3
- [ ] Server starts on 8080
- [ ] Pages serve correctly
- [ ] Traversal blocked
- [ ] Console shows IP and URL

---

# PHASE 4 — Room Join Flow

**Timeline**: Day 5–6

### P4.1 — `RoomService`
- [ ] `createSession()`, `join()`, `validateToken()`, `endSession()`, `current()`, `addListener()`. Synchronized state.

### P4.2 — `JoinHandler`
- [ ] `POST /join` → validates code → returns token or 403/410.

### P4.3 — Brute-Force Throttle
- [ ] Track failed attempts per IP. Block after 5 in 60s.

### P4.4 — `app.js` Join
- [ ] Sends `POST /join`, stores token, switches to connected state.

### P4.5 — `RoomServiceTest`
- [ ] Correct join, wrong code, expired session, user count, token validation.

### P4.6 — Phone Hotspot Join Test
- [ ] Real phone joins via hotspot URL. Wrong code rejected.

### GATE 4
- [ ] Correct code → token
- [ ] Wrong code → 403
- [ ] Ended session → 410
- [ ] Throttle works
- [ ] Phone join works
- [ ] Tests pass

---

# PHASE 5 — Multipart Upload Parser

**Timeline**: Day 6–7

### P5.1 — `MultipartParser`
- [ ] Parse `multipart/form-data`. Stream to disk in 64KB chunks. Enforce size limit. Clean partials on error.

### P5.2 — `MultipartParserTest`
- [ ] Fixture extraction, form fields, size limit enforcement, no partial files.

### P5.3 — `FileService`
- [ ] `storeUpload()`: sanitize, classify (IMAGE/DOCUMENT/SNIPPET), stream to `data/<sessionId>/<uuid>`, notify listeners. Overloaded `send()` methods.

### P5.4 — `FileServiceTest`
- [ ] Store + retrieve, size cap, session expiry.

### P5.5 — `SharedItemTest`
- [ ] Subclass kinds, both constructors, content types.

### GATE 5
- [ ] Parser extracts files from fixtures
- [ ] Size limit enforced, no partials
- [ ] FileService streams to disk
- [ ] Classification works
- [ ] All tests pass

---

# PHASE 6 — Upload, Download & File List Handlers

**Timeline**: Day 7–8

### P6.1 — `UploadHandler`
- [ ] `POST /upload` → token check → multipart parse → store → return metadata JSON. Cleans partials on error.

### P6.2 — `FilesHandler`
- [ ] `GET /files` → token check → JSON array of items.

### P6.3 — `DownloadHandler`
- [ ] `GET /download/{id}` → token check → stream file with `Content-Disposition`. Records DOWNLOAD row. Lookup by ID, never by path.

### P6.4 — `app.js` Upload
- [ ] Drag-drop + browse. Per-file `POST /upload` with progress via `XMLHttpRequest.upload.onprogress`.

### P6.5 — `app.js` File List
- [ ] Poll `GET /files` every 3s. Render list with download links.

### P6.6 — Multi-Device Test
- [ ] Upload from phone 1, download from phone 2. Size/integrity check. >100MB rejected. Evil filenames rejected.

### P6.7 — Concurrent Upload Test
- [ ] 3+ simultaneous uploads. No corruption.

### GATE 6
- [ ] Upload streams to disk
- [ ] File list returns correct JSON
- [ ] Download returns correct file
- [ ] >100MB → 413
- [ ] Path traversal rejected
- [ ] Multi-file works
- [ ] Concurrent uploads succeed
- [ ] Downloaded file matches uploaded

---

# PHASE 7 — Shared Clipboard

**Timeline**: Day 8–9

### P7.1 — `ClipboardService`
- [ ] `post()`, `list()`, `clear()`. Body capped at 64KB.

### P7.2 — `ClipboardHandler`
- [ ] `POST /clipboard`, `GET /clipboard`. Token required.

### P7.3 — `app.js` Clipboard UI
- [ ] Text area, language dropdown, post button, snippet list in `<pre><code>`.

### P7.4 — Test
- [ ] Post from device A, visible on device B. Cleared on session end.

### GATE 7
- [ ] Post and list work
- [ ] Visible cross-device
- [ ] Body capped
- [ ] Cleared on end

---

# PHASE 8 — QR Code & Share Actions

**Timeline**: Day 9–10

### P8.1 — QR Library
- [ ] Add Nayuki `qrcodegen` or ZXing to dependencies.

### P8.2 — `QrService`
- [ ] `generateQr(url, size)` → `BufferedImage`.

### P8.3 — Console Share Output
- [ ] On session start: print mode, IP, code, URL, WhatsApp/Email links.

### P8.4 — `/qr` Endpoint
- [ ] Returns PNG of current room QR.

### GATE 8
- [ ] QR generates valid image
- [ ] Console shows all share info
- [ ] `/qr` returns PNG

---

# PHASE 9 — Swing Desktop Dashboard

**Timeline**: Day 10–13

### P9.1 — `ClassDropFrame`
- [ ] 900×650, BorderLayout, close → prompt + cleanup.

### P9.2 — Header Panel
- [ ] Mode badge, IP, room code (large), QR image, share URL field, Copy/WhatsApp/Email buttons, keep-open banner.

### P9.3 — Session Controls
- [ ] Start, Pause Joins, End buttons.

### P9.4 — Device Table
- [ ] `DeviceTableModel`: #, Name, IP, Joined, Last Seen. Updated via listener + `invokeLater`.

### P9.5 — Transfer Table
- [ ] `TransferTableModel`: File, Kind, Size, Direction, From/To, Progress, Speed (EMA), Status. Updated via listener + `invokeLater`.

### P9.6 — Clipboard Tab
- [ ] Shows entries, updated via listener.

### P9.7 — Stats Panel
- [ ] This session + all-time metrics.

### P9.8 — Log Panel
- [ ] Timestamped log lines, capped at 1000.

### P9.9 — Mode Selector
- [ ] Combo/radio: LAN/HOTSPOT/RELAY. Updates IP, URL, QR. Tunnel URL field for RELAY.

### P9.10 — Listener Wiring
- [ ] Frame implements SessionListener, registered on RoomService. All callbacks → `invokeLater`.

### P9.11 — Threading Audit
- [ ] Grep: no Swing updates outside `invokeLater`.

### GATE 9
- [ ] Frame opens, shows controls
- [ ] Start shows code/IP/QR/URL
- [ ] Copy/WhatsApp/Email work
- [ ] Device table updates on join
- [ ] Transfer table shows progress + speed
- [ ] Clipboard tab works
- [ ] Log records events
- [ ] End session clears state
- [ ] No EDT violations

---

# PHASE 10 — Web Client Polish

**Timeline**: Day 13–15

### P10.1 — Full `app.css`
- [x] Dark theme (#0f172a body, #1e293b cards, #f8fafc text, #0D9488 accent). Responsive. Drag-zone. Progress bars. File list. Code blocks.

### P10.2 — Full `app.js`
- [x] Join flow, upload with progress, file list polling, download, clipboard, keep-open notice, session-end handling.

### P10.3 — NoSleep.js
- [x] Bundled in `resources/web/`. Activated on join.

### P10.4 — `about.html`
- [x] Project description, origin story, Cloud vs ClassDrop diagram, comparison table.

### P10.5 — `privacy.html`
- [x] Honest statements about HTTP, local MySQL, no E2E claims.

### P10.6 — Mobile Test
- [x] Android Chrome + iOS Safari: join, upload, download, clipboard, NoSleep, About, Privacy.

### GATE 10
- [x] Dark + teal looks clean
- [x] Mobile touch-friendly
- [x] Drag-drop uploads
- [x] Progress bars
- [x] Polling updates
- [x] Download works on phone
- [x] Clipboard works
- [x] NoSleep active
- [x] About/Privacy pages render

---

# PHASE 11 — Session Cleanup & Auto-Expiry

**Timeline**: Day 15–16

### P11.1 — `CleanupTask`
- [x] Scheduled every 30s. Checks session timeout. Removes stale devices. Deletes staging folder.

### P11.2 — Session End Behaviour
- [x] Invalidates code + tokens. Deletes files. Notifies listeners. Sets `ended_at`.

### P11.3 — Shutdown Hook
- [x] `endSession()` + `server.stop()` + cleanup.

### P11.4 — Test
- [x] End session → phone gets 410. Disk cleaned.

### GATE 11
- [x] Cleanup runs periodically
- [x] Auto-expiry works
- [x] End invalidates everything
- [x] Files deleted
- [x] Shutdown hook fires

---

# PHASE 12 — MySQL JDBC Repositories

**Timeline**: Day 16–18

### P12.1 — `schema.sql`
- [x] All 4 tables with FKs and indexes. Applied to MySQL.

### P12.2 — `ConnectionFactory`
- [x] Reads config, provides connections. Never logs password.

### P12.3–12.6 — Repositories
- [x] `SessionRepository`: insert, updateEndTime, findRecent.
- [x] `DeviceRepository`: insert, updateLastSeen, findBySession.
- [x] `TransferRepository`: insert, findBySession, search (name/kind/date).
- [x] `SnippetRepository`: insert, findBySession.

### P12.7 — `StatsService`
- [x] `getSessionStats()`, `getAllTimeStats()` via aggregate queries.

### P12.8 — Wire Into Services
- [x] All services call repos on create/join/upload/download/clipboard/end. Graceful degradation if MySQL down.

### P12.9 — DB Verification
- [x] Manual SQL queries match dashboard data.

### GATE 12
- [x] Schema applied
- [x] Connection works
- [x] All repos insert/query correctly
- [x] Stats correct
- [x] Graceful degradation verified
- [x] All SQL is PreparedStatement

---

# PHASE 13 — Search & History

**Timeline**: Day 18–19

### P13.1 — `SearchPanel`
- [x] Swing tab: filename, kind dropdown, date range, search button, results table.

### P13.2 — Stats on Dashboard
- [x] Session + all-time stats wired to `StatsService`.

### P13.3 — Search Test
- [x] Find past files by name, kind, date.

### GATE 13
- [x] Search finds past transfers
- [x] Filters work
- [x] Stats match SQL

---

# PHASE 14 — Serialization & Snapshot

**Timeline**: Day 19–20

### P14.1 — Save Snapshot
- [x] `ObjectOutputStream` → `session.ser`. Triggered by button + shutdown hook.

### P14.2 — Restore Snapshot
- [x] `ObjectInputStream` → rebuild state. Validate files exist.

### P14.3 — Snapshot Test
- [x] Upload files → save → restart → restore → files accessible.

### GATE 14
- [x] Serialize works
- [x] Deserialize restores state
- [x] Files downloadable after restore

---

# PHASE 15 — Security Hardening

**Timeline**: Day 20–21

### P15.1 — Path Traversal
- [x] Upload `../../../evil.txt` → rejected. Download `../../config` → 404. Static `../` → 403.

### P15.2 — Token Validation
- [x] No token → 401. Invalid → 401. After end → 410.

### P15.3 — Size Enforcement
- [x] 100MB → OK. 101MB → 413. Oversized stream → partial deleted.

### P15.4 — Brute Force
- [x] 6+ wrong codes → throttled.

### P15.5 — Concurrency
- [x] 5 simultaneous uploads, 3 simultaneous downloads, mixed operations.

### P15.6 — Large File Streaming
- [x] 80MB with `-Xmx128m` → no OOM. Checksum match.

### GATE 15
- [x] All traversal tests pass
- [x] Token-less rejected
- [x] Oversize rejected + cleaned
- [x] Throttle works
- [x] Concurrent safety verified
- [x] Large file no OOM

---

# PHASE 16 — Full Test Suite

**Timeline**: Day 21–22

### P16.1 — JUnit Suite
- [x] All 7 test classes pass.

### P16.2 — Manual Tests (TC-01 through TC-23)
- [x] All P0 tests: Pass with recorded date/device.
- [x] P1 tests: Pass or documented.

### P16.3 — DB Verification
- [x] Post-test SQL queries match dashboard.

### GATE 16
- [x] All automated tests green
- [x] All P0 manual tests pass
- [x] DB consistent

---

# PHASE 17 — Stretch Features (only after Gate 16)

- [x] P17.1 — Speed & ETA display
- [x] P17.2 — HTTP Range resume
- [x] P17.3 — Host remove file / kick device
- [x] P17.4 — Download all as ZIP
- [x] P17.5 — UDP Nearby discovery
- [x] P17.6 — Optional HTTPS
- [x] P17.7 — Server-Sent Events
- [x] P17.8 — Swing dark theme
- [x] P17.9 — Image preview thumbnails
- [x] P17.10 — Extension blocklist

### GATE 17
- [x] Stretch features don't break MVP

---

# PHASE 18 — Packaging

**Timeline**: Day 24–25

### P18.1 — Fat JAR
- [x] `maven-shade-plugin` → `classdrop-1.0.0.jar`. `java -jar` starts app.

### P18.2 — `launch-classdrop.bat`
- [x] Checks Java, starts JAR.

### P18.3 — `README.md`
- [x] Prerequisites, quick start, hotspot instructions, firewall command.

### P18.4 — Clean Build Test
- [x] Delete target, rebuild, run from fresh folder.

### GATE 18
- [x] Fat JAR builds and runs
- [x] Launcher script works
- [x] README complete

---

# PHASE 19 — Viva Rehearsal

**Timeline**: Day 25–27

### P19.1 — Demo Script (3 rehearsals)
- [x] 5-minute script practiced. Fits in time.

### P19.2 — Backup Video
- [x] 60-second recording. Saved in 2 locations.

### P19.3 — Screenshots
- [x] Dashboard, web client, About, search, MySQL queries.

### P19.4 — Q&A Prep
- [x] All examiner questions answerable.

### P19.5 — Day-of Checklist
- [x] MySQL running, firewall OK, hotspot on, app started, phone ready, video accessible.

### GATE 19
- [x] 3+ rehearsals done
- [x] Video recorded
- [x] Screenshots taken
- [x] All questions prepared

---

# PHASE 20 — Report & Submission

**Timeline**: Day 27–30

### P20.1 — Report
- [x] Title, Certificate, Acknowledgements, Abstract, Introduction, Literature Survey, Architecture, Tech Stack, System Design (class/ER/API diagrams), Implementation, Screenshots, Testing, Syllabus Mapping, Limitations, Conclusion, References.

### P20.2 — Diagrams
- [x] Architecture, class, ER, Cloud-vs-ClassDrop, network flowchart.

### P20.3 — Code Cleanup
- [x] No debug prints, no passwords, `.gitignore` covers artifacts. `git tag v1.0.0`.

### P20.4 — Submission Package
- [x] JAR + source + schema.sql + README + report PDF + backup video.

### GATE 20 — PROJECT COMPLETE
- [x] Report done
- [x] Diagrams included
- [x] Code clean
- [x] JAR builds
- [x] Package assembled
- [x] **ClassDrop is DONE** ✅

---

## Appendix A — HTTP API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET` | `/` | No | Join page |
| `GET` | `/r/{code}` | No | Join page, code pre-filled |
| `GET` | `/about` | No | About page |
| `GET` | `/privacy` | No | Privacy page |
| `GET` | `/health` | No | `{"ok":true}` |
| `GET` | `/static/*` | No | Static files |
| `POST` | `/join` | Code | Issue token |
| `GET` | `/files` | Token | File list |
| `POST` | `/upload` | Token | Upload file |
| `GET` | `/download/{id}` | Token | Download file |
| `POST` | `/clipboard` | Token | Post snippet |
| `GET` | `/clipboard` | Token | List snippets |
| `GET` | `/me` | Token | Device info |
| `GET` | `/stats` | Token | Statistics |
| `GET` | `/qr` | No | QR PNG |

## Appendix B — Error Codes

| Exception | HTTP | Code |
|---|---|---|
| InvalidRoomCodeException | 403 | INVALID_ROOM_CODE |
| SessionExpiredException | 410 | SESSION_EXPIRED |
| FileTooLargeException | 413 | FILE_TOO_LARGE |
| TransferFailedException | 500 | TRANSFER_FAILED |
| Missing token | 401 | UNAUTHORIZED |
| Bad request | 400 | BAD_REQUEST |

## Appendix C — Constants

| Constant | Value |
|---|---|
| PORT | 8080 |
| MAX_FILE_SIZE | 104857600 (100 MB) |
| CHUNK_BUFFER_SIZE | 65536 (64 KB) |
| ROOM_CODE_LENGTH | 6 |
| THREAD_POOL_SIZE | 16 |
| CLEANUP_INTERVAL_SECONDS | 30 |
| SESSION_TIMEOUT_MINUTES | 120 |
| BRUTE_FORCE_MAX_ATTEMPTS | 5 |
| CLIPBOARD_MAX_BODY_BYTES | 65536 (64 KB) |
| POLLING_INTERVAL_MS | 3000 |

---

*End of Master Plan — 23 November 2026*
