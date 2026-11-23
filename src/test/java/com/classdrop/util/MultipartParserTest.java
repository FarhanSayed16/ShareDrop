package com.classdrop.util;

import com.classdrop.exception.FileTooLargeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultipartParserTest {

    private Path tempDir;
    private MultipartParser parser;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("classdrop_parser_test_");
        parser = new MultipartParser();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    @Test
    void testExtractBoundary() throws Exception {
        String header = "multipart/form-data; boundary=---------------------------974767299852498929531610575";
        String boundary = MultipartParser.extractBoundary(header);
        assertEquals("---------------------------974767299852498929531610575", boundary);
    }

    @Test
    void testParseFilePart() throws Exception {
        String boundary = "----WebKitFormBoundaryX123";
        String fileContent = "Hello ClassDrop! This is a test file for streaming upload.";
        
        String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"test_notes.txt\"\r\n" +
                "Content-Type: text/plain\r\n\r\n" +
                fileContent + "\r\n" +
                "--" + boundary + "--\r\n";

        InputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        List<MultipartParser.Part> parts = parser.parse(in, boundary, tempDir, 1024 * 1024);

        assertEquals(1, parts.size());
        MultipartParser.Part part = parts.get(0);

        assertTrue(part.isFile());
        assertEquals("test_notes.txt", part.getOriginalFilename());
        assertEquals("text/plain", part.getContentType());
        assertEquals(fileContent.length(), part.getSizeBytes());

        String savedContent = Files.readString(part.getStoredFile());
        assertEquals(fileContent, savedContent);
    }

    @Test
    void testExceedingMaxCapThrowsAndCleansUp() throws Exception {
        String boundary = "----WebKitFormBoundaryOverflow";
        String fileContent = "1234567890".repeat(500); // 5,000 bytes

        String body = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"large.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n" +
                fileContent + "\r\n" +
                "--" + boundary + "--\r\n";

        InputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));

        // Set max cap strictly to 1,000 bytes
        assertThrows(FileTooLargeException.class, () ->
            parser.parse(in, boundary, tempDir, 1000)
        );

        // Verify temp files were deleted
        try (var s = Files.list(tempDir)) {
            assertEquals(0, s.count());
        }
    }
}
