package com.classdrop.service;

import com.classdrop.exception.InvalidPassphraseException;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InternetSharePassphraseTest {

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        RoomSession.resetActiveUsers();
        roomService = new RoomService();
    }

    @Test
    void internetJoin_rejectsMissingPassphrase() {
        RoomSession session = roomService.createSession(NetworkMode.RELAY, "10.0.0.1", "class2026");

        assertThrows(InvalidPassphraseException.class, () ->
            roomService.join(session.getRoomCode(), "Student", "1.2.3.4", null)
        );
        assertThrows(InvalidPassphraseException.class, () ->
            roomService.join(session.getRoomCode(), "Student", "1.2.3.4", "")
        );
    }

    @Test
    void internetJoin_rejectsWrongPassphrase() {
        RoomSession session = roomService.createSession(NetworkMode.RELAY, "10.0.0.1", "class2026");

        InvalidPassphraseException ex = assertThrows(InvalidPassphraseException.class, () ->
            roomService.join(session.getRoomCode(), "Student", "1.2.3.4", "wrong")
        );
        assertEquals("INVALID_PASSPHRASE", ex.getErrorCode());
    }

    @Test
    void internetJoin_acceptsCorrectPassphrase() throws Exception {
        RoomSession session = roomService.createSession(NetworkMode.RELAY, "10.0.0.1", "class2026");

        Device device = roomService.join(session.getRoomCode(), "Student", "1.2.3.4", "class2026");
        assertNotNull(device.getToken());
        assertTrue(session.isInternetShare());
        assertTrue(session.requiresPassphrase());
    }

    @Test
    void createInternetSession_requiresPassphrase() {
        assertThrows(IllegalArgumentException.class, () ->
            roomService.createSession(NetworkMode.RELAY, "10.0.0.1")
        );
        assertThrows(IllegalArgumentException.class, () ->
            roomService.createSession(NetworkMode.RELAY, "10.0.0.1", "  ")
        );
    }

    @Test
    void lanJoin_allowsOptionalPassphraseWhenUnset() throws Exception {
        RoomSession session = roomService.createSession(NetworkMode.LAN, "10.0.0.1");
        Device device = roomService.join(session.getRoomCode(), "Student", "10.0.0.2");
        assertNotNull(device);
    }

    @Test
    void lanJoin_enforcesPassphraseWhenHostSetsOne() {
        RoomSession session = roomService.createSession(NetworkMode.LAN, "10.0.0.1");
        session.setPassphrase("local-pin");

        assertThrows(InvalidPassphraseException.class, () ->
            roomService.join(session.getRoomCode(), "Student", "10.0.0.2", "bad")
        );
    }
}
