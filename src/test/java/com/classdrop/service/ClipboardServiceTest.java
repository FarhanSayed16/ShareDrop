package com.classdrop.service;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardServiceTest {

    private RoomService roomService;
    private ClipboardService clipboardService;
    private Device device;

    @BeforeEach
    void setUp() throws Exception {
        RoomSession.resetActiveUsers();
        roomService = new RoomService();
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "127.0.0.1");
        device = roomService.join(session.getRoomCode(), "Alice", "127.0.0.1");

        clipboardService = new ClipboardService(roomService);
    }

    @Test
    void testPostAndListSnippets() throws Exception {
        ClipboardEntry entry = clipboardService.post(device, "Quick Sort", "java", "public static void sort() {}");

        assertNotNull(entry);
        assertEquals("Quick Sort", entry.getTitle());
        assertEquals("java", entry.getLanguage());
        assertEquals("public static void sort() {}", entry.getBody());
        assertEquals("Alice", entry.getDeviceName());

        List<ClipboardEntry> list = clipboardService.list();
        assertEquals(1, list.size());
        assertEquals(entry.getId(), list.get(0).getId());
    }

    @Test
    void testBodyCappedAt64KB() throws Exception {
        String longBody = "A".repeat(70000); // 70 KB
        ClipboardEntry entry = clipboardService.post(device, "Big Code", "text", longBody);

        assertEquals(65536, entry.getBody().length());
    }

    @Test
    void testSessionExpiredThrows() {
        roomService.endSession();

        assertThrows(SessionExpiredException.class, () ->
            clipboardService.post(device, "Test", "text", "body")
        );
        assertThrows(SessionExpiredException.class, () ->
            clipboardService.list()
        );
    }
}
