package com.classdrop.service;

import com.classdrop.exception.InvalidRoomCodeException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomServiceTest {

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        RoomSession.resetActiveUsers();
        roomService = new RoomService();
    }

    @Test
    void testCreateSession() {
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        assertNotNull(session);
        assertEquals(6, session.getRoomCode().length());
        assertTrue(session.isActive());
        assertEquals("192.168.137.1", session.getHostIp());
    }

    @Test
    void testJoinSuccess() throws Exception {
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        Device device = roomService.join(session.getRoomCode(), "Farhan", "192.168.137.50");

        assertNotNull(device);
        assertEquals("Farhan", device.getDisplayName());
        assertEquals("192.168.137.50", device.getIpAddress());
        assertNotNull(device.getToken());
        assertEquals(1, RoomSession.getActiveUserCount());

        // Validate token
        Device validated = roomService.validateToken(device.getToken());
        assertNotNull(validated);
        assertEquals(device.getId(), validated.getId());
    }

    @Test
    void testJoinWrongCodeThrows() {
        roomService.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        assertThrows(InvalidRoomCodeException.class, () -> 
            roomService.join("999999", "Farhan", "192.168.137.50")
        );
    }

    @Test
    void testJoinExpiredSessionThrows() {
        RoomSession session = roomService.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        roomService.endSession();

        assertThrows(SessionExpiredException.class, () -> 
            roomService.join(session.getRoomCode(), "Farhan", "192.168.137.50")
        );
    }

    @Test
    void testBruteForceThrottle() {
        roomService.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        String badIp = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            assertThrows(InvalidRoomCodeException.class, () ->
                roomService.join("000000", "Attacker", badIp)
            );
        }

        // 6th attempt should state it's throttled
        InvalidRoomCodeException ex = assertThrows(InvalidRoomCodeException.class, () ->
            roomService.join("000000", "Attacker", badIp)
        );
        assertTrue(ex.getMessage().contains("Too many failed attempts"));
    }
}
