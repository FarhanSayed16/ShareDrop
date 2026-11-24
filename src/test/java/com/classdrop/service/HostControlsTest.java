package com.classdrop.service;

import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SharedItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class HostControlsTest {

    private Path dataDir;
    private RoomService roomService;
    private FileService fileService;

    @BeforeEach
    void setUp() throws Exception {
        RoomSession.resetActiveUsers();
        dataDir = Files.createTempDirectory("classdrop_host_ctrl_");
        roomService = new RoomService();
        roomService.createSession(NetworkMode.LAN, "127.0.0.1");
        fileService = new FileService(dataDir, roomService);
    }

    @AfterEach
    void tearDown() throws Exception {
        RoomSession.resetActiveUsers();
        if (dataDir != null && Files.exists(dataDir)) {
            Files.walk(dataDir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }

    @Test
    void kickDeviceInvalidatesToken() throws Exception {
        Device device = roomService.join(roomService.current().getRoomCode(), "Bob", "10.0.0.2");
        assertNotNull(roomService.validateToken(device.getToken()));
        roomService.kickDevice(device.getId());
        assertNull(roomService.validateToken(device.getToken()));
    }

    @Test
    void removeFileDeletesDiskCopy() throws Exception {
        Path src = dataDir.resolve("lecture.pdf");
        Files.writeString(src, "pdf-bytes");
        SharedItem item = fileService.send(src);
        assertTrue(Files.exists(item.getStoredPath()));
        fileService.removeFile(item.getId());
        assertThrows(TransferFailedException.class, () -> fileService.get(item.getId()));
        assertFalse(Files.exists(item.getStoredPath()));
    }

    @Test
    void blockedExtensionRejected() throws Exception {
        Path exe = dataDir.resolve("virus.exe");
        Files.write(exe, new byte[]{1, 2, 3, 4});
        assertThrows(TransferFailedException.class, () -> fileService.send(exe));
    }

    @Test
    void hostTokenMatchesSession() throws Exception {
        String hostToken = roomService.current().getHostToken();
        assertTrue(roomService.isHostToken(hostToken));
        assertFalse(roomService.isHostToken("not-a-host-token"));
    }
}
