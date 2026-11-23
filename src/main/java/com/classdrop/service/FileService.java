package com.classdrop.service;

import com.classdrop.exception.FileTooLargeException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.*;
import com.classdrop.util.Constants;
import com.classdrop.util.FilenameSanitizer;
import com.classdrop.util.Ids;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FileService {

    private final Path dataDir;
    private final RoomService roomService;
    private final FilenameSanitizer sanitizer = new FilenameSanitizer();
    private final long maxFileSizeBytes;
    private final com.classdrop.db.TransferRepository transferRepository;

    public FileService(RoomService roomService) {
        this(Paths.get("data"), roomService, Constants.MAX_FILE_SIZE, null);
    }

    public FileService(Path dataDir, RoomService roomService) {
        this(dataDir, roomService, Constants.MAX_FILE_SIZE, null);
    }

    public FileService(Path dataDir, RoomService roomService, long maxFileSizeBytes) {
        this(dataDir, roomService, maxFileSizeBytes, null);
    }

    public FileService(Path dataDir, RoomService roomService, long maxFileSizeBytes, com.classdrop.db.TransferRepository transferRepository) {
        this.dataDir = Objects.requireNonNull(dataDir);
        this.roomService = Objects.requireNonNull(roomService);
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.transferRepository = transferRepository;
    }

    public Path getDataDir() {
        return dataDir;
    }

    /**
     * Stores an uploaded file stream chunk by chunk directly to disk.
     */
    public SharedItem storeUpload(Device device, String originalName, String contentType, InputStream in, long declaredSize)
            throws FileTooLargeException, TransferFailedException, SessionExpiredException {

        RoomSession session = roomService.current();
        if (declaredSize > maxFileSizeBytes) {
            throw new FileTooLargeException("Declared file size " + declaredSize + " exceeds max cap of " + maxFileSizeBytes + " bytes");
        }

        sanitizer.rejectIfPathTraversal(originalName);
        sanitizer.rejectIfBlockedExtension(originalName);
        String cleanName = sanitizer.sanitize(originalName);
        Path sessionDir = dataDir.resolve(session.getId());

        try {
            Files.createDirectories(sessionDir);
        } catch (IOException e) {
            throw new TransferFailedException("Failed to create session storage folder: " + e.getMessage(), e);
        }

        String ext = "";
        int dot = cleanName.lastIndexOf('.');
        if (dot >= 0) {
            ext = cleanName.substring(dot);
        }

        String storedId = Ids.newId();
        String storedName = storedId + ext;
        Path targetPath = sessionDir.resolve(storedName);

        long totalBytes = 0;
        try (OutputStream os = Files.newOutputStream(targetPath)) {
            byte[] buf = new byte[Constants.CHUNK_BUFFER_SIZE];
            int read;
            while ((read = in.read(buf)) != -1) {
                totalBytes += read;
                if (totalBytes > maxFileSizeBytes) {
                    Files.deleteIfExists(targetPath);
                    throw new FileTooLargeException("File exceeded max allowed size of " + maxFileSizeBytes + " bytes");
                }
                os.write(buf, 0, read);
            }
        } catch (FileTooLargeException e) {
            throw e;
        } catch (IOException e) {
            try { Files.deleteIfExists(targetPath); } catch (Exception ignored) {}
            throw new TransferFailedException("Failed during file stream write: " + e.getMessage(), e);
        }

        // Create polymorphic SharedItem
        String kind = classifyKind(cleanName, contentType);
        SharedItem item;
        String deviceId = (device != null) ? device.getId() : "host";

        if ("IMAGE".equals(kind)) {
            item = new ImageFile(storedId, cleanName, totalBytes, deviceId, LocalDateTime.now(), contentType);
        } else if ("SNIPPET".equals(kind)) {
            item = new CodeSnippet(storedId, cleanName, totalBytes, deviceId, LocalDateTime.now(), "text", "");
        } else {
            item = new DocumentFile(storedId, cleanName, totalBytes, deviceId, LocalDateTime.now(), contentType);
        }

        item.setStoredPath(targetPath);
        session.addItem(item);

        // Notify transfer completed
        TransferRecord record = new TransferRecord(
            storedId, session.getId(), deviceId, kind, cleanName, storedName,
            totalBytes, contentType, "UPLOAD", "OK", null, LocalDateTime.now()
        );
        if (transferRepository != null) {
            transferRepository.insert(record);
        }
        notifyTransferCompleted(record);

        return item;
    }

    /**
     * Ingest an already existing temp file (from MultipartParser) into the session store.
     */
    public SharedItem ingestTempFile(Device device, String originalName, String contentType, Path tempFile)
            throws FileTooLargeException, TransferFailedException, SessionExpiredException {

        RoomSession session = roomService.current();
        sanitizer.rejectIfPathTraversal(originalName);
        sanitizer.rejectIfBlockedExtension(originalName);
        String cleanName = sanitizer.sanitize(originalName);
        Path sessionDir = dataDir.resolve(session.getId());

        try {
            Files.createDirectories(sessionDir);
            long size = Files.size(tempFile);
            if (size > maxFileSizeBytes) {
                Files.deleteIfExists(tempFile);
                throw new FileTooLargeException("File size " + size + " exceeds max cap of " + maxFileSizeBytes);
            }

            String ext = "";
            int dot = cleanName.lastIndexOf('.');
            if (dot >= 0) {
                ext = cleanName.substring(dot);
            }

            String storedId = Ids.newId();
            String storedName = storedId + ext;
            Path targetPath = sessionDir.resolve(storedName);

            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

            String kind = classifyKind(cleanName, contentType);
            SharedItem item;
            String deviceId = (device != null) ? device.getId() : "host";

            if ("IMAGE".equals(kind)) {
                item = new ImageFile(storedId, cleanName, size, deviceId, LocalDateTime.now(), contentType);
            } else if ("SNIPPET".equals(kind)) {
                item = new CodeSnippet(storedId, cleanName, size, deviceId, LocalDateTime.now(), "text", "");
            } else {
                item = new DocumentFile(storedId, cleanName, size, deviceId, LocalDateTime.now(), contentType);
            }

            item.setStoredPath(targetPath);
            session.addItem(item);

            TransferRecord record = new TransferRecord(
                storedId, session.getId(), deviceId, kind, cleanName, storedName,
                size, contentType, "UPLOAD", "OK", null, LocalDateTime.now()
            );
            if (transferRepository != null) {
                transferRepository.insert(record);
            }
            notifyTransferCompleted(record);

            return item;
        } catch (FileTooLargeException e) {
            throw e;
        } catch (IOException e) {
            throw new TransferFailedException("Failed to ingest uploaded file: " + e.getMessage(), e);
        }
    }

    // Method Overload 1 (Syllabus): Host adds local file
    public SharedItem send(Path localFile)
            throws FileTooLargeException, TransferFailedException, SessionExpiredException {
        if (!Files.exists(localFile)) {
            throw new TransferFailedException("Local file not found: " + localFile);
        }
        try (InputStream in = Files.newInputStream(localFile)) {
            String mime = Files.probeContentType(localFile);
            if (mime == null) mime = "application/octet-stream";
            return storeUpload(null, localFile.getFileName().toString(), mime, in, Files.size(localFile));
        } catch (IOException e) {
            throw new TransferFailedException("Failed to read local file: " + e.getMessage(), e);
        }
    }

    // Method Overload 2 (Syllabus): Host adds file with explicit roomCode check
    public SharedItem send(Path localFile, String roomCode)
            throws FileTooLargeException, TransferFailedException, SessionExpiredException {
        RoomSession session = roomService.current();
        if (roomCode == null || !roomCode.equals(session.getRoomCode())) {
            throw new TransferFailedException("Room code mismatch for send");
        }
        return send(localFile);
    }

    public List<SharedItem> listFiles() throws SessionExpiredException {
        return roomService.current().getItems();
    }

    public void recordDownload(Device device, SharedItem item) {
        if (transferRepository != null && item != null) {
            RoomSession session = roomService.getCurrentSessionOrNull();
            if (session != null) {
                String devId = (device != null) ? device.getId() : "host";
                TransferRecord rec = new TransferRecord(
                    session.getId(),
                    devId,
                    item.getKind(),
                    item.getOriginalName(),
                    item.getStoredName(),
                    item.getSizeBytes(),
                    item.getContentType(),
                    "DOWNLOAD"
                );
                transferRepository.insert(rec);
            }
        }
    }

    public SharedItem get(String id) throws TransferFailedException, SessionExpiredException {
        SharedItem item = roomService.current().findItemById(id);
        if (item == null) {
            throw new TransferFailedException("File not found with id: " + id);
        }
        return item;
    }

    public InputStream openDownloadStream(String id) throws TransferFailedException, SessionExpiredException {
        return openDownloadStream(id, 0L);
    }

    public InputStream openDownloadStream(String id, long startOffset)
            throws TransferFailedException, SessionExpiredException {
        SharedItem item = get(id);
        Path path = item.getStoredPath();
        if (path == null || !Files.exists(path)) {
            throw new TransferFailedException("Stored file missing on disk for id: " + id);
        }
        try {
            FileInputStream fis = new FileInputStream(path.toFile());
            if (startOffset > 0) {
                long skipped = fis.skip(startOffset);
                if (skipped < startOffset) {
                    fis.close();
                    throw new TransferFailedException("Unable to seek to requested Range offset");
                }
            }
            return fis;
        } catch (IOException e) {
            throw new TransferFailedException("Failed opening file stream: " + e.getMessage(), e);
        }
    }

    public SharedItem removeFile(String fileId) throws TransferFailedException, SessionExpiredException {
        RoomSession session = roomService.current();
        SharedItem item = session.removeItem(fileId);
        if (item == null) {
            throw new TransferFailedException("File not found with id: " + fileId);
        }
        Path path = item.getStoredPath();
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new TransferFailedException("Failed deleting file from disk: " + e.getMessage(), e);
            }
        }
        return item;
    }

    public Path createZipArchive(Path targetZip) throws TransferFailedException, SessionExpiredException {
        RoomSession session = roomService.current();
        List<SharedItem> items = session.getItems();
        if (items.isEmpty()) {
            throw new TransferFailedException("No files available to zip");
        }
        try {
            if (targetZip.getParent() != null) {
                Files.createDirectories(targetZip.getParent());
            }
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(targetZip))) {
                byte[] buf = new byte[Constants.CHUNK_BUFFER_SIZE];
                for (SharedItem item : items) {
                    Path path = item.getStoredPath();
                    if (path == null || !Files.exists(path)) continue;
                    String entryName = sanitizer.sanitize(item.getOriginalName());
                    zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                    try (InputStream in = Files.newInputStream(path)) {
                        int read;
                        while ((read = in.read(buf)) != -1) {
                            zos.write(buf, 0, read);
                        }
                    }
                    zos.closeEntry();
                }
            }
            return targetZip;
        } catch (IOException e) {
            try { Files.deleteIfExists(targetZip); } catch (Exception ignored) {}
            throw new TransferFailedException("Failed creating ZIP archive: " + e.getMessage(), e);
        }
    }

    public void cleanupSessionFiles(String sessionId) {
        if (sessionId == null) return;
        Path sessionDir = dataDir.resolve(sessionId);
        if (Files.exists(sessionDir)) {
            try {
                Files.walk(sessionDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
            } catch (Exception ignored) {}
        }
    }

    private String classifyKind(String filename, String contentType) {
        String lowerName = filename != null ? filename.toLowerCase() : "";
        String lowerCt = contentType != null ? contentType.toLowerCase() : "";

        if (lowerCt.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".webp")) {
            return "IMAGE";
        }
        if (lowerName.endsWith(".java") || lowerName.endsWith(".py") || lowerName.endsWith(".js")
                || lowerName.endsWith(".sql") || lowerName.endsWith(".c") || lowerName.endsWith(".cpp")
                || lowerName.endsWith(".html") || lowerName.endsWith(".css") || lowerName.endsWith(".json")) {
            return "SNIPPET";
        }
        return "DOCUMENT";
    }

    private void notifyTransferCompleted(TransferRecord record) {
        for (SessionListener l : roomService.getListeners()) {
            try { l.onTransferCompleted(record); } catch (Exception ignored) {}
        }
    }
}
