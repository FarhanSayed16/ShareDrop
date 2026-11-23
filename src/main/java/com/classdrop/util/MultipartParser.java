package com.classdrop.util;

import com.classdrop.exception.FileTooLargeException;
import com.classdrop.exception.TransferFailedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zero-framework, streaming multipart/form-data parser.
 * Uses a single continuous buffer to avoid socket read deadlocks.
 */
public final class MultipartParser {

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("boundary=([^;\\s]+)");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("filename=\"([^\"]*)\"");
    private static final Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]*)\"");
    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("(?i)Content-Type:\\s*([^;\\r\\n]+)");

    public static final class Part {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final Path storedFile;
        private final String textValue;
        private final long sizeBytes;

        public Part(String name, String originalFilename, String contentType, Path storedFile, String textValue, long sizeBytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.storedFile = storedFile;
            this.textValue = textValue;
            this.sizeBytes = sizeBytes;
        }

        public boolean isFile() {
            return storedFile != null;
        }

        public String getName() { return name; }
        public String getOriginalFilename() { return originalFilename; }
        public String getContentType() { return contentType; }
        public Path getStoredFile() { return storedFile; }
        public String getTextValue() { return textValue; }
        public long getSizeBytes() { return sizeBytes; }
    }

    public static String extractBoundary(String contentTypeHeader) throws TransferFailedException {
        if (contentTypeHeader == null) {
            throw new TransferFailedException("Missing Content-Type header in multipart upload");
        }
        Matcher m = BOUNDARY_PATTERN.matcher(contentTypeHeader);
        if (m.find()) {
            String b = m.group(1).trim();
            if (b.startsWith("\"") && b.endsWith("\"")) {
                b = b.substring(1, b.length() - 1);
            }
            return b;
        }
        throw new TransferFailedException("No multipart boundary found in Content-Type: " + contentTypeHeader);
    }

    public List<Part> parse(InputStream in, String boundary, Path targetDir, long maxSizeBytes)
            throws FileTooLargeException, TransferFailedException {

        List<Part> parts = new ArrayList<>();
        List<Path> createdFiles = new ArrayList<>();

        try {
            Files.createDirectories(targetDir);
            BufferedMultipartStream stream = new BufferedMultipartStream(in, boundary);

            // Read initial boundary line
            String firstLine = stream.readLine();
            if (firstLine == null || !firstLine.startsWith("--" + boundary)) {
                throw new TransferFailedException("Malformed multipart stream: initial boundary missing");
            }

            boolean isLastPart = firstLine.endsWith("--");

            while (!isLastPart) {
                // 1. Read Part Headers
                String headerLine;
                String partName = null;
                String filename = null;
                String partContentType = "application/octet-stream";

                while ((headerLine = stream.readLine()) != null && !headerLine.isEmpty()) {
                    Matcher nameMatcher = NAME_PATTERN.matcher(headerLine);
                    if (nameMatcher.find()) {
                        partName = nameMatcher.group(1);
                    }
                    Matcher fnMatcher = FILENAME_PATTERN.matcher(headerLine);
                    if (fnMatcher.find()) {
                        filename = fnMatcher.group(1);
                    }
                    Matcher ctMatcher = CONTENT_TYPE_PATTERN.matcher(headerLine);
                    if (ctMatcher.find()) {
                        partContentType = ctMatcher.group(1).trim();
                    }
                }

                if (filename != null) {
                    // 2A. File Part -> Stream directly to disk using 64 KB chunks
                    Path tempFile = Files.createTempFile(targetDir, "upload_", ".part");
                    createdFiles.add(tempFile);

                    long bytesWritten;
                    try (OutputStream fos = Files.newOutputStream(tempFile)) {
                        bytesWritten = stream.streamPart(fos, maxSizeBytes);
                    } catch (FileTooLargeException e) {
                        cleanupFiles(createdFiles);
                        throw e;
                    }

                    isLastPart = stream.isLastPart();
                    parts.add(new Part(partName, filename, partContentType, tempFile, null, bytesWritten));
                } else {
                    // 2B. Form field text part
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    long bytesWritten = stream.streamPart(baos, 65536);
                    String value = baos.toString(StandardCharsets.UTF_8);

                    isLastPart = stream.isLastPart();
                    parts.add(new Part(partName, null, partContentType, null, value, bytesWritten));
                }
            }

            return parts;
        } catch (FileTooLargeException e) {
            cleanupFiles(createdFiles);
            throw e;
        } catch (IOException e) {
            cleanupFiles(createdFiles);
            throw new TransferFailedException("I/O error during multipart parsing: " + e.getMessage(), e);
        }
    }

    private static void cleanupFiles(List<Path> files) {
        for (Path p : files) {
            try {
                Files.deleteIfExists(p);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Internal stateful buffer that reads from InputStream and maintains unconsumed bytes.
     */
    private static final class BufferedMultipartStream {
        private final InputStream in;
        private final byte[] delimiter;
        private final byte[] buffer = new byte[Constants.CHUNK_BUFFER_SIZE * 2];
        private int head = 0; // Number of valid bytes in buffer
        private int tail = 0; // Current read position in buffer
        private boolean eof = false;
        private boolean lastPart = false;

        public BufferedMultipartStream(InputStream in, String boundary) {
            this.in = in;
            this.delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        }

        public boolean isLastPart() {
            return lastPart;
        }

        public String readLine() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (true) {
                while (tail < head) {
                    byte b = buffer[tail++];
                    if (b == '\n') {
                        // Found line ending
                        byte[] bytes = baos.toByteArray();
                        int len = bytes.length;
                        if (len > 0 && bytes[len - 1] == '\r') {
                            len--;
                        }
                        return new String(bytes, 0, len, StandardCharsets.UTF_8);
                    }
                    baos.write(b);
                }

                // Buffer exhausted: fill it
                if (!fillBuffer()) {
                    if (baos.size() == 0) {
                        return null;
                    }
                    byte[] bytes = baos.toByteArray();
                    int len = bytes.length;
                    if (len > 0 && bytes[len - 1] == '\r') {
                        len--;
                    }
                    return new String(bytes, 0, len, StandardCharsets.UTF_8);
                }
            }
        }

        public long streamPart(OutputStream out, long maxSizeBytes) throws IOException, FileTooLargeException {
            long totalBytes = 0;
            int dLen = delimiter.length;

            while (true) {
                int matchIndex = findDelimiter(buffer, tail, head, delimiter);
                if (matchIndex != -1) {
                    // Delimiter found!
                    int writeLen = matchIndex - tail;
                    if (writeLen >= 2 && buffer[matchIndex - 1] == '\n' && buffer[matchIndex - 2] == '\r') {
                        writeLen -= 2;
                    } else if (writeLen >= 1 && buffer[matchIndex - 1] == '\n') {
                        writeLen -= 1;
                    }

                    if (writeLen > 0) {
                        out.write(buffer, tail, writeLen);
                        totalBytes += writeLen;
                        if (totalBytes > maxSizeBytes) {
                            throw new FileTooLargeException("Upload exceeds maximum allowed size of " + maxSizeBytes + " bytes");
                        }
                    }

                    // Advance tail past delimiter
                    tail = matchIndex + dLen;

                    // Check for trailing --
                    checkTrailing();
                    return totalBytes;
                }

                // Delimiter not found in current buffer
                int available = head - tail;
                int safeWrite = available - (dLen + 2);

                if (safeWrite > 0) {
                    out.write(buffer, tail, safeWrite);
                    totalBytes += safeWrite;
                    if (totalBytes > maxSizeBytes) {
                        throw new FileTooLargeException("Upload exceeds maximum allowed size of " + maxSizeBytes + " bytes");
                    }
                    tail += safeWrite;
                }

                if (!fillBuffer()) {
                    // EOF reached
                    int remaining = head - tail;
                    if (remaining > 0) {
                        out.write(buffer, tail, remaining);
                        totalBytes += remaining;
                        tail = head;
                    }
                    lastPart = true;
                    return totalBytes;
                }
            }
        }

        private void checkTrailing() throws IOException {
            // Read until end of line to see if trailing -- is present
            String trail = readLine();
            if (trail != null && trail.startsWith("--")) {
                this.lastPart = true;
            }
        }

        private boolean fillBuffer() throws IOException {
            if (eof) return false;

            // Compact buffer: move unread bytes [tail..head] to start [0..(head-tail)]
            int unread = head - tail;
            if (unread > 0 && tail > 0) {
                System.arraycopy(buffer, tail, buffer, 0, unread);
            }
            tail = 0;
            head = unread;

            int read = in.read(buffer, head, buffer.length - head);
            if (read == -1) {
                eof = true;
                return false;
            }
            head += read;
            return true;
        }

        private int findDelimiter(byte[] buf, int start, int end, byte[] delim) {
            int dLen = delim.length;
            outer:
            for (int i = start; i <= end - dLen; i++) {
                for (int j = 0; j < dLen; j++) {
                    if (buf[i + j] != delim[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }
}
