package com.classdrop.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WifiQrPayloadTest {

    @Test
    void wpa_buildsEscapedPayload() {
        String payload = WifiQrPayload.wpa("ClassDrop-Hub", "pass;word");
        assertNotNull(payload);
        assertTrue(payload.startsWith("WIFI:T:WPA;S:ClassDrop-Hub;"));
        assertTrue(payload.contains("P:pass\\;word;"));
        assertTrue(payload.endsWith(";;") || payload.endsWith(";"));
    }

    @Test
    void blankSsid_returnsNull() {
        assertNull(WifiQrPayload.wpa("  ", "x"));
    }
}
