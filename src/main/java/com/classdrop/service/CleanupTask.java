package com.classdrop.service;

import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.util.Constants;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CleanupTask implements Runnable {

    private final RoomService roomService;
    private final FileService fileService;
    private final TunnelService tunnelService;
    private final long timeoutMinutes;
    private final long staleDeviceMinutes;
    private ScheduledExecutorService scheduler;

    public CleanupTask(RoomService roomService, FileService fileService) {
        this(roomService, fileService, null, Constants.SESSION_TIMEOUT_MINUTES, 5);
    }

    public CleanupTask(RoomService roomService, FileService fileService, TunnelService tunnelService) {
        this(roomService, fileService, tunnelService, Constants.SESSION_TIMEOUT_MINUTES, 5);
    }

    public CleanupTask(RoomService roomService, FileService fileService, long timeoutMinutes, long staleDeviceMinutes) {
        this(roomService, fileService, null, timeoutMinutes, staleDeviceMinutes);
    }

    public CleanupTask(RoomService roomService, FileService fileService, TunnelService tunnelService,
                       long timeoutMinutes, long staleDeviceMinutes) {
        this.roomService = Objects.requireNonNull(roomService);
        this.fileService = Objects.requireNonNull(fileService);
        this.tunnelService = tunnelService;
        this.timeoutMinutes = timeoutMinutes;
        this.staleDeviceMinutes = staleDeviceMinutes;
    }

    public synchronized void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ClassDrop-Cleanup-Daemon");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this, Constants.CLEANUP_INTERVAL_SECONDS, Constants.CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    @Override
    public void run() {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null) return;

        LocalDateTime now = LocalDateTime.now();

        // 1. Check auto-expiry of active session (shorter for Internet Share)
        if (session.isActive()) {
            Duration duration = Duration.between(session.getStartedAt(), now);
            long limit = session.isInternetShare()
                ? com.classdrop.util.Config.getInternetSessionTimeoutMinutes()
                : timeoutMinutes;
            if (duration.toMinutes() >= limit) {
                System.out.println("[ClassDrop-Cleanup] Session timeout reached (" + limit + " mins). Auto-expiring session: " + session.getRoomCode());
                if (tunnelService != null && session.isInternetShare()) {
                    try {
                        tunnelService.stopInternetShare();
                    } catch (Exception ignored) {}
                }
                roomService.endSession();
            } else {
                // 2. Check for stale devices (silent for staleDeviceMinutes)
                for (Device d : session.getDevices()) {
                    if (d.getLastSeen() != null && Duration.between(d.getLastSeen(), now).toMinutes() >= staleDeviceMinutes) {
                        session.removeDevice(d.getId());
                        notifyDeviceLeft(d);
                    }
                }
            }
        }

        // 3. Purge files if session has ended
        if (!session.isActive()) {
            fileService.cleanupSessionFiles(session.getId());
        }
    }

    private void notifyDeviceLeft(Device d) {
        for (SessionListener l : roomService.getListeners()) {
            try { l.onDeviceLeft(d); } catch (Exception ignored) {}
        }
    }
}
