package com.classdrop.service;

import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.DocumentFile;
import com.classdrop.model.ImageFile;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotServiceTest {

    @TempDir
    Path tempDir;

    private RoomService roomService;
    private FileService fileService;
    private ClipboardService clipboardService;
    private SnapshotService snapshotService;

    @BeforeEach
    public void setUp() {
        RoomSession.resetActiveUsers();
        roomService = new RoomService();
        fileService = new FileService(tempDir.resolve("data"), roomService);
        clipboardService = new ClipboardService(roomService);
        snapshotService = new SnapshotService(roomService, fileService, clipboardService);
    }

    @AfterEach
    public void tearDown() {
        RoomSession.resetActiveUsers();
    }

    @Test
    public void testSnapshotRoundTrip() throws Exception {
        // 1. Create session
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        String originalCode = session.getRoomCode();

        // 2. Add devices
        Device dev1 = roomService.join(originalCode, "Farhan Laptop", "192.168.137.10");
        Device dev2 = roomService.join(originalCode, "Student Phone", "192.168.137.20");

        // 3. Store uploaded files
        byte[] imgBytes = "FAKE_JPEG_IMAGE_CONTENT".getBytes(StandardCharsets.UTF_8);
        fileService.storeUpload(dev1, "photo.jpg", "image/jpeg", new ByteArrayInputStream(imgBytes), imgBytes.length);

        byte[] docBytes = "MCA Project Report Content".getBytes(StandardCharsets.UTF_8);
        fileService.storeUpload(dev2, "report.pdf", "application/pdf", new ByteArrayInputStream(docBytes), docBytes.length);

        // 4. Post clipboard snippet
        clipboardService.post(dev1, "Hello World", "java", "System.out.println(\"Hello MCA\");");

        // 5. Save snapshot to file
        Path snapshotPath = tempDir.resolve("test_session.ser");
        boolean saved = snapshotService.saveSnapshot(snapshotPath);
        assertTrue(saved, "Snapshot save should succeed");
        assertTrue(Files.exists(snapshotPath), "Snapshot file must exist on disk");
        assertTrue(Files.size(snapshotPath) > 0, "Snapshot file must not be empty");

        // 6. Reset in-memory services to simulate app restart
        RoomSession.resetActiveUsers();
        RoomService newRoomService = new RoomService();
        FileService newFileService = new FileService(tempDir.resolve("data"), newRoomService);
        ClipboardService newClipboardService = new ClipboardService(newRoomService);
        SnapshotService newSnapshotService = new SnapshotService(newRoomService, newFileService, newClipboardService);

        assertNull(newRoomService.getCurrentSessionOrNull(), "New room service should start empty");

        // 7. Restore snapshot
        boolean restored = newSnapshotService.restoreSnapshot(snapshotPath);
        assertTrue(restored, "Snapshot restore should succeed");

        RoomSession restoredSession = newRoomService.getCurrentSessionOrNull();
        assertNotNull(restoredSession, "Restored session must be present");
        assertEquals(originalCode, restoredSession.getRoomCode(), "Room code should match");
        assertEquals("192.168.137.1", restoredSession.getHostIp());
        assertEquals(NetworkMode.HOTSPOT, restoredSession.getMode());

        // Verify devices
        List<Device> devices = restoredSession.getDevices();
        assertEquals(2, devices.size(), "Should restore both devices");
        assertTrue(devices.stream().anyMatch(d -> d.getDisplayName().equals("Farhan Laptop")));
        assertTrue(devices.stream().anyMatch(d -> d.getDisplayName().equals("Student Phone")));

        // Verify files
        assertEquals(2, restoredSession.getItems().size(), "Should restore both files");
        assertTrue(restoredSession.getItems().stream().anyMatch(i -> i.getOriginalName().equals("photo.jpg")));
        assertTrue(restoredSession.getItems().stream().anyMatch(i -> i.getOriginalName().equals("report.pdf")));

        // Verify disk files accessible
        for (var item : restoredSession.getItems()) {
            assertNotNull(item.getStoredPath());
            assertTrue(Files.exists(item.getStoredPath()), "File on disk must still exist");
        }

        // Verify clipboard
        List<ClipboardEntry> snippets = newClipboardService.list();
        assertEquals(1, snippets.size(), "Should restore 1 clipboard snippet");
        assertEquals("java", snippets.get(0).getLanguage());
        assertEquals("System.out.println(\"Hello MCA\");", snippets.get(0).getBody());
    }

    @Test
    public void testRestoreNonExistentFileReturnsFalse() throws Exception {
        Path missing = tempDir.resolve("missing_session.ser");
        assertFalse(snapshotService.restoreSnapshot(missing));
    }

    @Test
    public void testSaveWhenNoSessionReturnsFalse() throws Exception {
        Path target = tempDir.resolve("empty.ser");
        assertFalse(snapshotService.saveSnapshot(target));
    }
}
