package com.classdrop.model;

import com.classdrop.util.Ids;
import java.io.Serializable;
import java.time.LocalDateTime;

public final class TransferRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String sessionId;
    private final String deviceId;
    private final String itemKind;
    private final String originalName;
    private final String storedName;
    private final long sizeBytes;
    private final String contentType;
    private final String direction; // UPLOAD or DOWNLOAD
    private String status;          // OK, FAILED, EXPIRED
    private final String fileToken;
    private final LocalDateTime createdAt;

    public TransferRecord(String sessionId, String deviceId, String itemKind, String originalName,
                          String storedName, long sizeBytes, String contentType, String direction) {
        this(Ids.newId(), sessionId, deviceId, itemKind, originalName, storedName, sizeBytes,
             contentType, direction, "OK", null, LocalDateTime.now());
    }

    public TransferRecord(String id, String sessionId, String deviceId, String itemKind, String originalName,
                          String storedName, long sizeBytes, String contentType, String direction,
                          String status, String fileToken, LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.itemKind = itemKind;
        this.originalName = originalName;
        this.storedName = storedName;
        this.sizeBytes = sizeBytes;
        this.contentType = contentType;
        this.direction = direction;
        this.status = status;
        this.fileToken = fileToken;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getDeviceId() { return deviceId; }
    public String getItemKind() { return itemKind; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public long getSizeBytes() { return sizeBytes; }
    public String getContentType() { return contentType; }
    public String getDirection() { return direction; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFileToken() { return fileToken; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
