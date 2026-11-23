package com.classdrop.server;

import com.classdrop.exception.FileTooLargeException;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SharedItem;
import com.classdrop.service.ClipboardService;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityAndConcurrencyTest {

    private static final int PORT = 18090;
    private static ClassDropServer server;
    private static RoomService roomService;
    private static FileService fileService;
    private static ClipboardService clipboardService;
    private static HttpClient client;
    private static String baseUrl;
    private static Path tempDir;
    private static Device testDevice;

    @BeforeAll
    public static void setUp() throws Exception {
        RoomSession.resetActiveUsers();
        tempDir = Files.createTempDirectory("classdrop_sec_test_");

        roomService = new RoomService();
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        fileService = new FileService(tempDir.resolve("data"), roomService, 15 * 1024 * 1024); // 15 MB cap for test
        clipboardService = new ClipboardService(roomService);

        server = new ClassDropServer(roomService, fileService, clipboardService, null);
        server.start(PORT);

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        baseUrl = "http://127.0.0.1:" + PORT;

        testDevice = roomService.join(session.getRoomCode(), "SecurityTester", "127.0.0.1");
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
        RoomSession.resetActiveUsers();
    }

    @Test
    public void testPathTraversalInStaticFilesReturns403() throws Exception {
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/static/../../etc/passwd"))
                .GET()
                .build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, resp1.statusCode());

        // Raw socket to test raw backslash path traversal HTTP request
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", PORT);
             java.io.OutputStream out = s.getOutputStream();
             java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()))) {
            out.write("GET /static/..\\..\\windows\\win.ini HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String statusLine = in.readLine();
            assertNotNull(statusLine);
            assertTrue(statusLine.contains("403") || statusLine.contains("400"), 
                "Backslash traversal must be rejected with 403 or 400: " + statusLine);
        }
    }

    @Test
    public void testInvalidAndMissingTokens() throws Exception {
        // Missing token
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/files"))
                .GET()
                .build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp1.statusCode());

        // Forged token
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/files"))
                .header("X-ClassDrop-Token", "forged-fake-token-1234")
                .GET()
                .build();
        HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp2.statusCode());
    }

    @Test
    public void testSessionEndReturns410() throws Exception {
        RoomService tempRoom = new RoomService();
        RoomSession tempSession = tempRoom.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        Device d = tempRoom.join(tempSession.getRoomCode(), "TempUser", "127.0.0.1");

        FileService tempFiles = new FileService(tempDir.resolve("temp_data"), tempRoom);
        ClassDropServer tempServer = new ClassDropServer(tempRoom, tempFiles);
        int tempPort = 18095;
        tempServer.start(tempPort);

        try {
            // First call while active -> 200
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + tempPort + "/files"))
                    .header("X-ClassDrop-Token", d.getToken())
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode());

            // End session
            tempRoom.endSession();

            // Next call after session end -> 410 Gone
            HttpResponse<String> respAfterEnd = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(410, respAfterEnd.statusCode());
        } finally {
            tempServer.stop();
        }
    }

    @Test
    public void testOversizeFileUploadThrowsExceptionAndPurgesDisk() {
        // Max cap is 15 MB on this service
        long oversize = 20 * 1024 * 1024;
        byte[] chunk = new byte[64 * 1024];

        InputStream dummyStream = new InputStream() {
            private long sent = 0;
            @Override
            public int read() {
                if (sent >= oversize) return -1;
                sent++;
                return 42;
            }
            @Override
            public int read(byte[] b, int off, int len) {
                if (sent >= oversize) return -1;
                int toRead = (int) Math.min(len, oversize - sent);
                sent += toRead;
                return toRead;
            }
        };

        assertThrows(FileTooLargeException.class, () -> {
            fileService.storeUpload(testDevice, "massive.bin", "application/octet-stream", dummyStream, oversize);
        });

        // Ensure no leftover file in session data folder
        Path sessionDir = tempDir.resolve("data").resolve(roomService.getCurrentSessionOrNull().getId());
        if (Files.exists(sessionDir)) {
            try (var stream = Files.list(sessionDir)) {
                assertFalse(stream.anyMatch(p -> p.getFileName().toString().contains("massive")));
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testBruteForceThrottleLockout() {
        RoomService throttleService = new RoomService();
        RoomSession session = throttleService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        String badCode = "000000";
        String attackerIp = "10.0.0.99";

        // 5 wrong attempts are allowed before lockout
        for (int i = 0; i < 5; i++) {
            assertThrows(Exception.class, () -> {
                throttleService.join(badCode, "Attacker", attackerIp);
            });
        }

        // 6th attempt must trigger throttle exception
        Exception locked = assertThrows(Exception.class, () -> {
            throttleService.join(badCode, "Attacker", attackerIp);
        });
        assertTrue(locked.getMessage().contains("Too many failed attempts"), 
            "Should be blocked by brute force throttle");
    }

    @Test
    public void testConcurrentUploadsAndDownloads() throws Exception {
        int threadCount = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            tasks.add(() -> {
                String boundary = "----BoundaryTest" + idx;
                String filename = "concurrent_file_" + idx + ".txt";
                String content = "Thread content test payload #" + idx;

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                bos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                bos.write("Content-Type: text/plain\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                bos.write(content.getBytes(StandardCharsets.UTF_8));
                bos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

                HttpRequest uploadReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/upload"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header("X-ClassDrop-Token", testDevice.getToken())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray()))
                        .build();

                HttpResponse<String> resp = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                return resp.statusCode() == 200 && resp.body().contains(filename);
            });
        }

        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        pool.shutdown();

        for (Future<Boolean> f : futures) {
            assertTrue(f.get(), "Each concurrent upload must succeed with 200 OK");
        }
    }

    @Test
    public void testStreamingLargeFileIntegrityWithoutOom() throws Exception {
        int size = 8 * 1024 * 1024; // 8 MB
        byte[] payload = new byte[size];
        for (int i = 0; i < size; i++) {
            payload[i] = (byte) (i % 256);
        }

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        String originalHash = HexFormat.of().formatHex(md5.digest(payload));

        // Upload through FileService with 64 KB streaming buffer
        SharedItem item = fileService.storeUpload(
            testDevice, "large_test.dat", "application/octet-stream",
            new ByteArrayInputStream(payload), size
        );

        assertNotNull(item);
        assertEquals(size, item.getSizeBytes());
        assertTrue(Files.exists(item.getStoredPath()));

        // Read back chunk by chunk and verify MD5 checksum
        MessageDigest readMd5 = MessageDigest.getInstance("MD5");
        try (InputStream in = item.openStream()) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) != -1) {
                readMd5.update(buf, 0, r);
            }
        }
        String readHash = HexFormat.of().formatHex(readMd5.digest());

        assertEquals(originalHash, readHash, "Streaming file hash must match exact original bytes");
    }
}
