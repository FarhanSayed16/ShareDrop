package com.classdrop.service;

import com.classdrop.exception.FileTooLargeException;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SharedItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private Path tempDir;
    private RoomService roomService;
    private FileService fileService;
    private Device device;

    @BeforeEach
    void setUp() throws Exception {
        RoomSession.resetActiveUsers();
        tempDir = Files.createTempDirectory("classdrop_fileservice_test_");
        roomService = new RoomService();
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        device = roomService.join(session.getRoomCode(), "Alice", "127.0.0.1");

        fileService = new FileService(tempDir, roomService, 5000); // 5 KB cap for test
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void testStoreUploadAndDownloadStream() throws Exception {
        String content = "Lecture 1 slides content in PDF format.";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(bytes);

        SharedItem item = fileService.storeUpload(device, "slides.pdf", "application/pdf", in, bytes.length);

        assertNotNull(item);
        assertEquals("slides.pdf", item.getOriginalName());
        assertEquals("DOCUMENT", item.getKind());
        assertEquals(bytes.length, item.getSizeBytes());

        // Test list
        List<SharedItem> items = fileService.listFiles();
        assertEquals(1, items.size());
        assertEquals(item.getId(), items.get(0).getId());

        // Test download stream
        try (InputStream downloadStream = fileService.openDownloadStream(item.getId())) {
            byte[] downloaded = downloadStream.readAllBytes();
            assertArrayEquals(bytes, downloaded);
        }
    }

    @Test
    void testImageClassification() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        SharedItem item = fileService.storeUpload(device, "avatar.png", "image/png", new ByteArrayInputStream(data), 3);
        assertEquals("IMAGE", item.getKind());
    }

    @Test
    void testSnippetClassification() throws Exception {
        byte[] data = "System.out.println();".getBytes(StandardCharsets.UTF_8);
        SharedItem item = fileService.storeUpload(device, "Main.java", "text/plain", new ByteArrayInputStream(data), data.length);
        assertEquals("SNIPPET", item.getKind());
    }

    @Test
    void testOversizedUploadThrows() {
        byte[] bigData = new byte[6000]; // Exceeds 5000 byte cap
        assertThrows(FileTooLargeException.class, () ->
            fileService.storeUpload(device, "big.zip", "application/zip", new ByteArrayInputStream(bigData), 6000)
        );
    }
}
