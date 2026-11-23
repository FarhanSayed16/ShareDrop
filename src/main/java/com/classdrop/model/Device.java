package com.classdrop.model;

import com.classdrop.util.Ids;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Device implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String displayName;
    private final String ipAddress;
    private final String token;
    private final LocalDateTime joinedAt;
    private volatile LocalDateTime lastSeen;

    public Device(String displayName, String ipAddress) {
        this(Ids.newId(), displayName, ipAddress, Ids.newId(), LocalDateTime.now(), LocalDateTime.now());
    }

    public Device(String id, String displayName, String ipAddress, String token, LocalDateTime joinedAt, LocalDateTime lastSeen) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.ipAddress = ipAddress;
        this.token = Objects.requireNonNull(token);
        this.joinedAt = Objects.requireNonNull(joinedAt);
        this.lastSeen = Objects.requireNonNull(lastSeen);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public void touch() {
        this.lastSeen = LocalDateTime.now();
    }
}
