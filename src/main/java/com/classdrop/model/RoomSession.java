package com.classdrop.model;

import com.classdrop.util.Ids;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RoomSession implements Serializable {
    private static final long serialVersionUID = 1L;

    // Academic requirement: static active user counter with synchronized updates
    private static volatile int activeUserCount = 0;

    public static synchronized void incrementActiveUsers() {
        activeUserCount++;
    }

    public static synchronized void decrementActiveUsers() {
        if (activeUserCount > 0) {
            activeUserCount--;
        }
    }

    public static synchronized void resetActiveUsers() {
        activeUserCount = 0;
    }

    public static int getActiveUserCount() {
        return activeUserCount;
    }

    private final String id;
    private final String roomCode;
    private final NetworkMode mode;
    private final String hostIp;
    private final String hostToken;
    private final LocalDateTime startedAt;
    private volatile LocalDateTime endedAt;
    private volatile boolean active;
    /** Optional join passphrase; required when mode is RELAY (Internet Share). */
    private volatile String passphrase;

    private final List<Device> devices = new CopyOnWriteArrayList<>();
    private final List<SharedItem> items = new CopyOnWriteArrayList<>();

    public RoomSession(String roomCode, NetworkMode mode, String hostIp) {
        this(Ids.newId(), roomCode, mode, hostIp, Ids.newId(), LocalDateTime.now(), null, true);
    }

    public RoomSession(String id, String roomCode, NetworkMode mode, String hostIp,
                       LocalDateTime startedAt, LocalDateTime endedAt, boolean active) {
        this(id, roomCode, mode, hostIp, Ids.newId(), startedAt, endedAt, active);
    }

    public RoomSession(String id, String roomCode, NetworkMode mode, String hostIp, String hostToken,
                       LocalDateTime startedAt, LocalDateTime endedAt, boolean active) {
        this.id = id;
        this.roomCode = roomCode;
        this.mode = mode != null ? mode : NetworkMode.HOTSPOT;
        this.hostIp = hostIp;
        this.hostToken = hostToken != null ? hostToken : Ids.newId();
        this.startedAt = startedAt != null ? startedAt : LocalDateTime.now();
        this.endedAt = endedAt;
        this.active = active;
    }

    public String getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public NetworkMode getMode() { return mode; }
    public String getHostIp() { return hostIp; }
    public String getHostToken() { return hostToken != null ? hostToken : ""; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public boolean isActive() { return active; }

    public boolean isInternetShare() {
        return mode == NetworkMode.RELAY;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        if (passphrase == null || passphrase.isBlank()) {
            this.passphrase = null;
        } else {
            this.passphrase = passphrase.trim();
        }
    }

    public boolean requiresPassphrase() {
        return passphrase != null && !passphrase.isBlank();
    }

    public boolean matchesPassphrase(String input) {
        if (!requiresPassphrase()) return true;
        return passphrase.equals(input != null ? input.trim() : "");
    }

    public synchronized void addDevice(Device device) {
        devices.add(device);
        incrementActiveUsers();
    }

    public synchronized void removeDevice(String deviceId) {
        devices.removeIf(d -> d.getId().equals(deviceId));
        decrementActiveUsers();
    }

    public List<Device> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices));
    }

    public Device findDeviceByToken(String token) {
        if (token == null) return null;
        for (Device d : devices) {
            if (token.equals(d.getToken())) {
                d.touch();
                return d;
            }
        }
        return null;
    }

    public synchronized void addItem(SharedItem item) {
        items.add(item);
    }

    public synchronized SharedItem removeItem(String itemId) {
        SharedItem found = findItemById(itemId);
        if (found != null) {
            items.removeIf(i -> itemId.equals(i.getId()));
        }
        return found;
    }

    public List<SharedItem> getItems() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public SharedItem findItemById(String itemId) {
        if (itemId == null) return null;
        for (SharedItem item : items) {
            if (itemId.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    public Device findDeviceById(String deviceId) {
        if (deviceId == null) return null;
        for (Device d : devices) {
            if (deviceId.equals(d.getId())) {
                return d;
            }
        }
        return null;
    }

    public synchronized void end() {
        this.active = false;
        this.endedAt = LocalDateTime.now();
        resetActiveUsers();
    }
}
