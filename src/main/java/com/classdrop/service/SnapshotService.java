package com.classdrop.service;

import com.classdrop.exception.ClassDropException;
import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SessionSnapshot;
import com.classdrop.model.SharedItem;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnapshotService {
    private static final Logger LOGGER = Logger.getLogger(SnapshotService.class.getName());
    public static final Path DEFAULT_SNAPSHOT_PATH = Paths.get("data", "session.ser");

    private final RoomService roomService;
    private final FileService fileService;
    private final ClipboardService clipboardService;

    public SnapshotService(RoomService roomService, FileService fileService, ClipboardService clipboardService) {
        this.roomService = Objects.requireNonNull(roomService);
        this.fileService = Objects.requireNonNull(fileService);
        this.clipboardService = Objects.requireNonNull(clipboardService);
    }

    public synchronized boolean saveSnapshot(Path targetPath) throws IOException {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null) {
            return false;
        }

        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<Device> devices = session.getDevices();
        List<SharedItem> items = session.getItems();
        List<ClipboardEntry> clipboard;
        try {
            clipboard = clipboardService.list();
        } catch (Exception e) {
            clipboard = List.of();
        }

        SessionSnapshot snapshot = new SessionSnapshot(session, devices, items, clipboard);

        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(targetPath))) {
            oos.writeObject(snapshot);
            oos.flush();
            LOGGER.info("[ClassDrop-Snapshot] Session snapshot successfully saved to " + targetPath.toAbsolutePath());
            return true;
        }
    }

    public synchronized boolean restoreSnapshot(Path sourcePath) throws IOException, ClassNotFoundException {
        if (!Files.exists(sourcePath)) {
            return false;
        }

        SessionSnapshot snapshot;
        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(sourcePath))) {
            snapshot = (SessionSnapshot) ois.readObject();
        }

        if (snapshot == null || snapshot.getSession() == null) {
            return false;
        }

        RoomSession restoredSession = snapshot.getSession();

        // Restore devices and items into session
        for (Device d : snapshot.getDevices()) {
            if (restoredSession.findDeviceByToken(d.getToken()) == null) {
                restoredSession.addDevice(d);
            }
        }

        for (SharedItem item : snapshot.getItems()) {
            // Verify stored path on disk
            Path p = item.getStoredPath();
            if (p != null && Files.exists(p)) {
                if (restoredSession.findItemById(item.getId()) == null) {
                    restoredSession.addItem(item);
                }
            } else {
                LOGGER.warning("[ClassDrop-Snapshot] Item file missing on disk: " + item.getOriginalName());
            }
        }

        // Restore clipboard entries
        clipboardService.restoreEntries(snapshot.getClipboard());

        // Set as active current session
        roomService.restoreSession(restoredSession);

        LOGGER.info("[ClassDrop-Snapshot] Session snapshot successfully restored: room code " + restoredSession.getRoomCode());
        return true;
    }

    public boolean saveDefault() {
        try {
            return saveSnapshot(DEFAULT_SNAPSHOT_PATH);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to save default snapshot: {0}", e.getMessage());
            return false;
        }
    }

    public boolean restoreDefault() {
        try {
            return restoreSnapshot(DEFAULT_SNAPSHOT_PATH);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to restore default snapshot: {0}", e.getMessage());
            return false;
        }
    }
}
