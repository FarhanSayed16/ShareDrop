package com.classdrop.util;

import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareUrlBuilderTest {

    @Test
    void normalizeBaseUrl_stripsTrailingSlash() {
        assertEquals("https://abc.trycloudflare.com",
            ShareUrlBuilder.normalizeBaseUrl("https://abc.trycloudflare.com/"));
        assertNull(ShareUrlBuilder.normalizeBaseUrl("  "));
        assertNull(ShareUrlBuilder.normalizeBaseUrl(null));
    }

    @Test
    void localRoomUrl_usesHttpAndPort() {
        assertEquals("http://192.168.137.1:8080/r/123456",
            ShareUrlBuilder.localRoomUrl("192.168.137.1", 8080, "123456"));
    }

    @Test
    void shareUrlForSession_prefersPublicBaseInRelayMode() {
        RoomSession session = new RoomSession("482917", NetworkMode.RELAY, "10.0.0.5");
        String url = ShareUrlBuilder.shareUrlForSession(session, 8080, "https://demo.trycloudflare.com");
        assertEquals("https://demo.trycloudflare.com/r/482917", url);
    }

    @Test
    void shareUrlForSession_relayWithoutPublicUrl_returnsNull() {
        RoomSession session = new RoomSession("482917", NetworkMode.RELAY, "10.0.0.5");
        assertNull(ShareUrlBuilder.shareUrlForSession(session, 8080, null));
    }

    @Test
    void shareUrlForSession_lanIgnoresPublicBase() {
        RoomSession session = new RoomSession("482917", NetworkMode.LAN, "10.0.0.5");
        String url = ShareUrlBuilder.shareUrlForSession(session, 8080, "https://demo.trycloudflare.com");
        assertEquals("http://10.0.0.5:8080/r/482917", url);
    }

    @Test
    void displayModeName_mapsRelayToInternet() {
        assertEquals("INTERNET", ShareUrlBuilder.displayModeName(NetworkMode.RELAY));
        assertEquals("LAN", ShareUrlBuilder.displayModeName(NetworkMode.LAN));
        assertEquals("HOTSPOT", ShareUrlBuilder.displayModeName(NetworkMode.HOTSPOT));
    }
}
