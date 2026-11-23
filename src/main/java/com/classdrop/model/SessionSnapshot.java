package com.classdrop.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class SessionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private final RoomSession session;
    private final List<Device> devices;
    private final List<SharedItem> items;
    private final List<ClipboardEntry> clipboard;

    public SessionSnapshot(RoomSession session, List<Device> devices, List<SharedItem> items, List<ClipboardEntry> clipboard) {
        this.session = session;
        this.devices = devices != null ? new ArrayList<>(devices) : new ArrayList<>();
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.clipboard = clipboard != null ? new ArrayList<>(clipboard) : new ArrayList<>();
    }

    public RoomSession getSession() { return session; }
    public List<Device> getDevices() { return devices; }
    public List<SharedItem> getItems() { return items; }
    public List<ClipboardEntry> getClipboard() { return clipboard; }
}
