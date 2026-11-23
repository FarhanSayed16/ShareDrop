package com.classdrop.model;

import java.time.LocalDateTime;

public final class DocumentFile extends SharedItem {
    private static final long serialVersionUID = 1L;
    private final String contentType;

    public DocumentFile(String originalName, long sizeBytes, String uploaderDeviceId, String contentType) {
        super(originalName, sizeBytes, uploaderDeviceId);
        this.contentType = contentType != null ? contentType : "application/octet-stream";
    }

    public DocumentFile(String id, String originalName, long sizeBytes, String uploaderDeviceId, LocalDateTime createdAt, String contentType) {
        super(id, originalName, sizeBytes, uploaderDeviceId, createdAt);
        this.contentType = contentType != null ? contentType : "application/octet-stream";
    }

    @Override
    public String getKind() {
        return "DOCUMENT";
    }

    @Override
    public String getContentType() {
        return contentType;
    }
}
