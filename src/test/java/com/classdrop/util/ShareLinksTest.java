package com.classdrop.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareLinksTest {

    @Test
    void testWhatsappUrl() {
        String url = ShareLinks.whatsappUrl("http://192.168.137.1:8080/r/482917");
        assertTrue(url.startsWith("https://wa.me/?text="));
        assertTrue(url.contains("482917"));
    }

    @Test
    void testEmailUrl() {
        String url = ShareLinks.emailUrl("http://192.168.137.1:8080/r/482917");
        assertTrue(url.startsWith("mailto:?subject="));
        assertTrue(url.contains("&body="));
        assertTrue(url.contains("482917"));
    }
}
