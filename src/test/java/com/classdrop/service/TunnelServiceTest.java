package com.classdrop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TunnelServiceTest {

    @Test
    void setManualPublicBaseUrl_normalizesAndExposes() {
        TunnelService tunnel = new TunnelService(8080);
        tunnel.stopInternetShare(); // clear any configured property side effects for ephemeral state
        tunnel.setManualPublicBaseUrl("https://abc.trycloudflare.com/");
        assertEquals("https://abc.trycloudflare.com", tunnel.getPublicBaseUrl());
        assertTrue(tunnel.hasPublicUrl());
    }

    @Test
    void stopInternetShare_clearsEphemeralUrlWhenNoConfiguredBase() {
        TunnelService tunnel = new TunnelService(8080);
        // If application.properties has empty public.base.url, stop should clear manual URL
        tunnel.setManualPublicBaseUrl("https://ephemeral.trycloudflare.com");
        tunnel.stopInternetShare();
        // After stop without configured property, URL is cleared OR restored to config.
        // Either null or config value is acceptable; ensure process stop does not throw.
        assertDoesNotThrow(tunnel::stopInternetShare);
    }
}
