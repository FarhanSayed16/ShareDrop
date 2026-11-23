package com.classdrop.model;

import com.classdrop.util.Ids;
import java.io.Serializable;
import java.time.LocalDateTime;

public final class ClipboardEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String sessionId;
    private final String deviceId;
    private final String deviceName;
    private final String title;
    private final String language;
    private final String body;
    private final LocalDateTime createdAt;

    public ClipboardEntry(String sessionId, String deviceId, String deviceName, String title, String language, String body) {
        this(Ids.newId(), sessionId, deviceId, deviceName, title, language, body, LocalDateTime.now());
    }

    public ClipboardEntry(String id, String sessionId, String deviceId, String deviceName, String title, String language, String body, LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.title = title;
        this.language = language != null ? language : "text";
        this.body = body != null ? body : "";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getTitle() { return title; }
    public String getLanguage() { return language; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
