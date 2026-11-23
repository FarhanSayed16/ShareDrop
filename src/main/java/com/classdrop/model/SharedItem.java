package com.classdrop.model;

import com.classdrop.exception.TransferFailedException;
import com.classdrop.util.Ids;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

public abstract class SharedItem implements Transferable, Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String originalName;
    private final long sizeBytes;
    private final String uploaderDeviceId;
    private final LocalDateTime createdAt;
    private transient Path storedPath;
    private String storedPathString;

    // Overload 1: New upload creation
    protected SharedItem(String originalName, long sizeBytes, String uploaderDeviceId) {
        this(Ids.newId(), originalName, sizeBytes, uploaderDeviceId, LocalDateTime.now());
    }

    // Overload 2: Rehydration from DB or Snapshot
    protected SharedItem(String id, String originalName, long sizeBytes, String uploaderDeviceId, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.originalName = Objects.requireNonNull(originalName, "originalName cannot be null");
        this.sizeBytes = sizeBytes;
        this.uploaderDeviceId = uploaderDeviceId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public abstract String getKind();

    @Override
    public final String getId() {
        return id;
    }

    public final String getOriginalName() {
        return originalName;
    }

    @Override
    public final long getSizeBytes() {
        return sizeBytes;
    }

    public final String getUploaderDeviceId() {
        return uploaderDeviceId;
    }

    public final LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public final Path getStoredPath() {
        if (storedPath == null && storedPathString != null) {
            storedPath = java.nio.file.Paths.get(storedPathString);
        }
        return storedPath;
    }

    public final String getStoredName() {
        Path p = getStoredPath();
        return p != null ? p.getFileName().toString() : id;
    }

    public final void setStoredPath(Path storedPath) {
        this.storedPath = storedPath;
        this.storedPathString = storedPath != null ? storedPath.toString() : null;
    }

    @Override
    public InputStream openStream() throws TransferFailedException {
        if (storedPath == null || !Files.exists(storedPath)) {
            throw new TransferFailedException("Stored file not found for item: " + id);
        }
        try {
            return new FileInputStream(storedPath.toFile());
        } catch (IOException e) {
            throw new TransferFailedException("Failed to open stream for item: " + id, e);
        }
    }
}
