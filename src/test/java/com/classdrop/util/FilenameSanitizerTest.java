package com.classdrop.util;

import com.classdrop.exception.TransferFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilenameSanitizerTest {

    private FilenameSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new FilenameSanitizer();
    }

    @Test
    void testSanitizeNormalNames() {
        assertEquals("notes.pdf", sanitizer.sanitize("notes.pdf"));
        assertEquals("Lecture_1.pptx", sanitizer.sanitize("Lecture_1.pptx"));
    }

    @Test
    void testSanitizePathTraversal() {
        assertEquals("system.ini", sanitizer.sanitize("..\\..\\windows\\system.ini"));
        assertEquals("passwd", sanitizer.sanitize("../../etc/passwd"));
        assertEquals("c.txt", sanitizer.sanitize("a/b/c.txt"));
    }

    @Test
    void testSanitizeEmptyOrDots() {
        assertEquals("unnamed_file", sanitizer.sanitize(null));
        assertEquals("unnamed_file", sanitizer.sanitize(""));
        assertEquals("unnamed_file", sanitizer.sanitize("   "));
        assertEquals("unnamed_file", sanitizer.sanitize("."));
        assertEquals("unnamed_file", sanitizer.sanitize(".."));
        assertEquals("unnamed_file", sanitizer.sanitize("..."));
    }

    @Test
    void testSanitizeLengthCap() {
        String longName = "a".repeat(300) + ".pdf";
        String sanitized = sanitizer.sanitize(longName);
        assertTrue(sanitized.length() <= 255);
        assertTrue(sanitized.endsWith(".pdf"));
    }

    @Test
    void testRejectIfPathTraversal() {
        assertDoesNotThrow(() -> sanitizer.rejectIfPathTraversal("clean_file.txt"));
        assertThrows(TransferFailedException.class, () -> sanitizer.rejectIfPathTraversal("../clean_file.txt"));
        assertThrows(TransferFailedException.class, () -> sanitizer.rejectIfPathTraversal("a/b.txt"));
        assertThrows(TransferFailedException.class, () -> sanitizer.rejectIfPathTraversal("a\\b.txt"));
    }

    @Test
    void testRejectBlockedExtensions() {
        assertDoesNotThrow(() -> sanitizer.rejectIfBlockedExtension("notes.pdf"));
        assertDoesNotThrow(() -> sanitizer.rejectIfBlockedExtension("Main.java"));
        assertThrows(TransferFailedException.class, () -> sanitizer.rejectIfBlockedExtension("malware.exe"));
        assertThrows(TransferFailedException.class, () -> sanitizer.rejectIfBlockedExtension("script.bat"));
        assertThrows(TransferFailedException.class, () -> sanitizer.rejectIfBlockedExtension("payload.ps1"));
    }
}
