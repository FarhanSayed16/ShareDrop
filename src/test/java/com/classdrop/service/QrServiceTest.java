package com.classdrop.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class QrServiceTest {

    private QrService qrService;

    @BeforeEach
    void setUp() {
        qrService = new QrService();
    }

    @Test
    void testGenerateQrImage() {
        String url = "http://192.168.137.1:8080/r/482917";
        BufferedImage img = qrService.generateQr(url, 250);

        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    void testGenerateQrPngBytes() throws Exception {
        String url = "http://192.168.137.1:8080/r/482917";
        byte[] bytes = qrService.generateQrPngBytes(url, 200);

        assertNotNull(bytes);
        assertTrue(bytes.length > 50);

        // Verify PNG magic header bytes: 0x89 'P' 'N' 'G'
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
    }
}
