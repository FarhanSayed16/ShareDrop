package com.classdrop.server;

import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class ClassDropServerIntegrationTest {

    private static final int TEST_PORT = 18080;
    private static ClassDropServer server;
    private static RoomService roomService;
    private static FileService fileService;
    private static RoomSession session;
    private static HttpClient client;
    private static String baseUrl;
    private static Path dataTempDir;
    private static Device joinedDevice;

    @BeforeAll
    static void startServer() throws Exception {
        RoomSession.resetActiveUsers();
        dataTempDir = Files.createTempDirectory("classdrop_int_test_");

        roomService = new RoomService();
        session = roomService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        fileService = new FileService(dataTempDir, roomService);

        server = new ClassDropServer(roomService, fileService);
        server.start(TEST_PORT);

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        baseUrl = "http://127.0.0.1:" + TEST_PORT;

        // Pre-join a device for testing authenticated endpoints
        joinedDevice = roomService.join(session.getRoomCode(), "Alice", "127.0.0.1");
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
        RoomSession.resetActiveUsers();
        if (Files.exists(dataTempDir)) {
            Files.walk(dataTempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void testHealthEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"ok\":true"));
    }

    @Test
    void testRootHtml() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("ClassDrop"));
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/html"));
    }

    @Test
    void testRoomLinkHtml() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/r/" + session.getRoomCode()))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("ClassDrop"));
    }

    @Test
    void testAboutAndPrivacyHtml() throws Exception {
        HttpRequest reqAbout = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/about"))
                .GET()
                .build();
        HttpResponse<String> respAbout = client.send(reqAbout, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, respAbout.statusCode());
        assertTrue(respAbout.body().contains("About ClassDrop"));

        HttpRequest reqPrivacy = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/privacy"))
                .GET()
                .build();
        HttpResponse<String> respPrivacy = client.send(reqPrivacy, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, respPrivacy.statusCode());
        assertTrue(respPrivacy.body().contains("ClassDrop Privacy"));
    }

    @Test
    void testStaticCss() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/static/app.css"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("--accent"));
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("text/css"));
    }

    @Test
    void testPathTraversalBlockedOnStatic() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/static/../application.properties"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    @Test
    void testJoinSuccessAndFailure() throws Exception {
        String jsonPayload = String.format("{\"roomCode\":\"%s\",\"deviceName\":\"Bob\"}", session.getRoomCode());
        HttpRequest reqSuccess = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/join"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> respSuccess = client.send(reqSuccess, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, respSuccess.statusCode());
        assertTrue(respSuccess.body().contains("\"token\":"));
        assertTrue(respSuccess.body().contains("\"sessionActive\":true"));

        String wrongPayload = "{\"roomCode\":\"999999\",\"deviceName\":\"Charlie\"}";
        HttpRequest reqFail = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/join"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(wrongPayload))
                .build();

        HttpResponse<String> respFail = client.send(reqFail, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, respFail.statusCode());
        assertTrue(respFail.body().contains("INVALID_ROOM_CODE"));
    }

    @Test
    void testUploadListAndDownloadFlow() throws Exception {
        String token = joinedDevice.getToken();
        String boundary = "----TestBoundary987654321";
        String fileContent = "This is a real homework assignment file stream.";
        String originalFilename = "homework_solution.txt";

        // Build multipart body
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + originalFilename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write("Content-Type: text/plain\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        baos.write(fileContent.getBytes(StandardCharsets.UTF_8));
        baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] bodyBytes = baos.toByteArray();

        // 1. POST /upload
        HttpRequest uploadReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-ClassDrop-Token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        HttpResponse<String> uploadResp = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, uploadResp.statusCode());
        assertTrue(uploadResp.body().contains(originalFilename));

        // Extract ID from JSON: "id":"<uuid>"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(uploadResp.body());
        assertTrue(m.find(), "Upload response must contain file id");
        String fileId = m.group(1);

        // 2. GET /files
        HttpRequest listReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/files"))
                .header("X-ClassDrop-Token", token)
                .GET()
                .build();

        HttpResponse<String> listResp = client.send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listResp.statusCode());
        assertTrue(listResp.body().contains(fileId));
        assertTrue(listResp.body().contains(originalFilename));

        // 3. GET /download/{id} with token query param
        HttpRequest downloadReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/download/" + fileId + "?token=" + token))
                .GET()
                .build();

        HttpResponse<byte[]> downloadResp = client.send(downloadReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, downloadResp.statusCode());
        assertEquals(fileContent, new String(downloadResp.body(), StandardCharsets.UTF_8));
        assertEquals("text/plain", downloadResp.headers().firstValue("Content-Type").orElse(""));
        assertTrue(downloadResp.headers().firstValue("Content-Disposition").orElse("").contains(originalFilename));

        // 4. Download without token returns 401
        HttpRequest unauthorizedReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/download/" + fileId))
                .GET()
                .build();
        HttpResponse<String> unauthResp = client.send(unauthorizedReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthResp.statusCode());
    }

    @Test
    void testClipboardPostAndGetFlow() throws Exception {
        String token = joinedDevice.getToken();

        // 1. POST /clipboard
        String snippetBody = "{\"body\":\"System.out.println(\\\"Hello KJSIM!\\\");\",\"title\":\"Quick Print\",\"language\":\"java\"}";
        HttpRequest postReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/clipboard"))
                .header("Content-Type", "application/json")
                .header("X-ClassDrop-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(snippetBody))
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, postResp.statusCode());
        assertTrue(postResp.body().contains("Hello KJSIM!"));
        assertTrue(postResp.body().contains("Quick Print"));
        assertTrue(postResp.body().contains("java"));

        // 2. GET /clipboard
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/clipboard"))
                .header("X-ClassDrop-Token", token)
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getResp.statusCode());
        assertTrue(getResp.body().contains("Hello KJSIM!"));

        // 3. GET /clipboard without token returns 401
        HttpRequest unauthReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/clipboard"))
                .GET()
                .build();
        HttpResponse<String> unauthResp = client.send(unauthReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthResp.statusCode());
    }

    @Test
    void testQrCodeEndpoint() throws Exception {
        HttpRequest qrReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/qr"))
                .GET()
                .build();

        HttpResponse<byte[]> qrResp = client.send(qrReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, qrResp.statusCode());
        assertEquals("image/png", qrResp.headers().firstValue("Content-Type").orElse(""));
        byte[] bytes = qrResp.body();
        assertTrue(bytes.length > 50);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
    }
}
