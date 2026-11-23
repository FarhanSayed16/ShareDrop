package com.classdrop.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SharedItemTest {

    @Test
    void testImageFile() {
        ImageFile img = new ImageFile("photo.jpg", 1024, "dev-1", "image/jpeg");
        assertEquals("IMAGE", img.getKind());
        assertEquals("image/jpeg", img.getContentType());
        assertEquals("photo.jpg", img.getOriginalName());
        assertEquals(1024, img.getSizeBytes());
        assertEquals("dev-1", img.getUploaderDeviceId());
        assertNotNull(img.getId());
        assertNotNull(img.getCreatedAt());

        // Overload constructor
        LocalDateTime now = LocalDateTime.now();
        ImageFile reloaded = new ImageFile("id-123", "photo.jpg", 1024, "dev-1", now, "image/png");
        assertEquals("id-123", reloaded.getId());
        assertEquals(now, reloaded.getCreatedAt());
        assertEquals("image/png", reloaded.getContentType());
    }

    @Test
    void testDocumentFile() {
        DocumentFile doc = new DocumentFile("syllabus.pdf", 2048, "dev-2", "application/pdf");
        assertEquals("DOCUMENT", doc.getKind());
        assertEquals("application/pdf", doc.getContentType());
    }

    @Test
    void testCodeSnippet() {
        CodeSnippet snippet = new CodeSnippet("Solution.java", 512, "dev-3", "java", "public class Solution {}");
        assertEquals("SNIPPET", snippet.getKind());
        assertEquals("java", snippet.getLanguage());
        assertEquals("public class Solution {}", snippet.getBody());
        assertTrue(snippet.getContentType().startsWith("text/plain"));
    }
}
