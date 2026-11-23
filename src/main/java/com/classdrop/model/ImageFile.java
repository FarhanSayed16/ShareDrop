package com.classdrop.model;

import java.time.LocalDateTime;

public final class ImageFile extends SharedItem {
    private static final long serialVersionUID = 1L;
    private final String contentType;

    public ImageFile(String originalName, long sizeBytes, String uploaderDeviceId, String contentType) {
        super(originalName, sizeBytes, uploaderDeviceId);
        this.contentType = contentType != null ? contentType : "image/jpeg";
    }

    public ImageFile(String id, String originalName, long sizeBytes, String uploaderDeviceId, LocalDateTime createdAt, String contentType) {
        super(id, originalName, sizeBytes, uploaderDeviceId, createdAt);
        this.contentType = contentType != null ? contentType : "image/jpeg";
    }

    @Override
    public String getKind() {
        return "IMAGE";
    }

    @Override
    public String getContentType() {
        return contentType;
    }
}
