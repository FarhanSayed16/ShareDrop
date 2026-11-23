package com.classdrop.service;

import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class CleanupTaskTest {

    private Path tempDir;
    private RoomService roomService;
    private FileService fileService;
    private CleanupTask cleanupTask;

    @BeforeEach
    void setUp() throws Exception {
        RoomSession.resetActiveUsers();
        tempDir = Files.createTempDirectory("classdrop_cleanup_test_");
        roomService = new RoomService();
        fileService = new FileService(tempDir, roomService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cleanupTask != null) {
            cleanupTask.stop();
        }
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void testAutoExpireSessionAfterTimeout() throws Exception {
        // Create session with 0 minute timeout (instant expire)
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        cleanupTask = new CleanupTask(roomService, fileService, 0, 5);

        assertTrue(session.isActive());

        // Run cleanup cycle
        cleanupTask.run();

        // Should now be expired
        assertFalse(session.isActive());
        assertNotNull(session.getEndedAt());
    }

    @Test
    void testPurgeFilesAfterSessionEnded() throws Exception {
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        Device device = roomService.join(session.getRoomCode(), "Alice", "127.0.0.1");

        // Upload a file to disk
        byte[] content = "Assignment notes to be cleaned".getBytes(StandardCharsets.UTF_8);
        fileService.storeUpload(device, "notes.txt", "text/plain", new ByteArrayInputStream(content), content.length);

        Path sessionFolder = tempDir.resolve(session.getId());
        assertTrue(Files.exists(sessionFolder));

        // End session and run cleanup
        roomService.endSession();
        cleanupTask = new CleanupTask(roomService, fileService, 120, 5);
        cleanupTask.run();

        // Session folder on disk should now be deleted
        assertFalse(Files.exists(sessionFolder));
    }
}
