package com.classdrop;

import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.server.ClassDropServer;
import com.classdrop.service.NetworkInfoService;
import com.classdrop.service.RoomService;
import com.classdrop.service.TunnelService;
import com.classdrop.util.ShareLinks;
import com.classdrop.util.ShareUrlBuilder;

public final class App {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ClassDrop — Offline Classroom File Sharing Hub  ");
        System.out.println("==================================================");

        if (!java.awt.GraphicsEnvironment.isHeadless() && !isHeadlessArg(args)) {
            com.classdrop.ui.UiLookAndFeel.install();
        }

        NetworkInfoService networkInfo = new NetworkInfoService();
        String hostIp = networkInfo.getPreferredIP();
        NetworkMode mode = networkInfo.detectMode(hostIp);

        com.classdrop.db.ConnectionFactory connectionFactory = new com.classdrop.db.ConnectionFactory();
        if (connectionFactory.isAvailable()) {
            connectionFactory.initSchema();
            System.out.println("  Database:  CONNECTED (" + com.classdrop.db.ConnectionFactory.sanitizeUrl(connectionFactory.getUrl()) + ")");
        } else {
            System.out.println("  Database:  OFFLINE (In-memory fallback mode active)");
        }

        com.classdrop.db.SessionRepository sessionRepo = new com.classdrop.db.SessionRepository(connectionFactory);
        com.classdrop.db.DeviceRepository deviceRepo = new com.classdrop.db.DeviceRepository(connectionFactory);
        com.classdrop.db.TransferRepository transferRepo = new com.classdrop.db.TransferRepository(connectionFactory);
        com.classdrop.db.SnippetRepository snippetRepo = new com.classdrop.db.SnippetRepository(connectionFactory);
        com.classdrop.service.StatsService statsService = new com.classdrop.service.StatsService(connectionFactory);

        RoomService roomService = new RoomService(new com.classdrop.util.RoomCodeGenerator(), sessionRepo, deviceRepo);
        RoomSession session = roomService.createSession(mode, hostIp);
        com.classdrop.service.FileService fileService = new com.classdrop.service.FileService(
            java.nio.file.Paths.get("data"), roomService, com.classdrop.util.Config.getMaxFileSizeBytes(), transferRepo
        );
        com.classdrop.service.ClipboardService clipboardService = new com.classdrop.service.ClipboardService(roomService, snippetRepo);
        com.classdrop.service.QrService qrService = new com.classdrop.service.QrService();
        int port = com.classdrop.util.Config.getPort();
        TunnelService tunnelService = new TunnelService(port);
        com.classdrop.service.CleanupTask cleanupTask = new com.classdrop.service.CleanupTask(roomService, fileService, tunnelService);
        cleanupTask.start();

        if (hasArg(args, "--restore", "-r")) {
            com.classdrop.service.SnapshotService snapshotService =
                new com.classdrop.service.SnapshotService(roomService, fileService, clipboardService);
            if (snapshotService.restoreDefault()) {
                session = roomService.getCurrentSessionOrNull();
                if (session != null && session.isInternetShare()) {
                    // Snapshot may restore RELAY without a live tunnel — drop to offline mode
                    String offlineIp = networkInfo.resolveHostIp(
                        networkInfo.isHotspotAvailable() ? NetworkMode.HOTSPOT : NetworkMode.LAN);
                    NetworkMode offlineMode = networkInfo.detectMode(offlineIp);
                    String pass = session.requiresPassphrase() ? session.getPassphrase() : null;
                    session = roomService.createSession(offlineMode, offlineIp, pass);
                    System.out.println("  [ClassDrop] Restored RELAY snapshot remapped to offline " + offlineMode);
                }
                if (session != null) {
                    System.out.println("  [ClassDrop] Active session restored from snapshot: " + session.getRoomCode());
                }
            }
        }

        ClassDropServer server = new ClassDropServer(roomService, fileService, clipboardService, qrService, statsService, tunnelService);

        try {
            server.start(port);

            String shareUrl = ShareUrlBuilder.localRoomUrl(hostIp, port, session.getRoomCode());
            String whatsapp = ShareLinks.whatsappUrl(shareUrl);
            String email = ShareLinks.emailUrl(shareUrl);

            System.out.println("  Status:    Server RUNNING on 0.0.0.0:" + port);
            System.out.println("  Mode:      " + ShareUrlBuilder.displayModeName(mode));
            System.out.println("  Host IP:   " + hostIp);
            System.out.println("  Room Code: " + session.getRoomCode());
            System.out.println("  Host Token:" + session.getHostToken());
            System.out.println("  Share URL: " + shareUrl);
            System.out.println("  Tip:       Prefer HOTSPOT offline (no internet). INTERNET mode is optional.");
            System.out.println("--------------------------------------------------");
            System.out.println("  WhatsApp:  " + whatsapp);
            System.out.println("  Email:     " + email);
            System.out.println("==================================================");
            System.out.println("  Press Ctrl+C to terminate session and clean up. ");
            System.out.println("==================================================");

            if (!java.awt.GraphicsEnvironment.isHeadless() && !isHeadlessArg(args)) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        com.classdrop.ui.ClassDropFrame frame = new com.classdrop.ui.ClassDropFrame(
                            roomService, fileService, clipboardService, qrService, networkInfo, port,
                            statsService, transferRepo, tunnelService
                        );
                        frame.setVisible(true);
                    } catch (Exception e) {
                        System.err.println("[ClassDrop] Could not launch Swing GUI: " + e.getMessage());
                    }
                });
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[ClassDrop] Terminating session and stopping server...");
                try {
                    tunnelService.stopInternetShare();
                } catch (Exception ignored) {}
                try {
                    com.classdrop.service.SnapshotService snapshotService =
                        new com.classdrop.service.SnapshotService(roomService, fileService, clipboardService);
                    snapshotService.saveDefault();
                } catch (Exception ignored) {}
                cleanupTask.stop();
                roomService.endSession();
                server.stop();
                System.out.println("[ClassDrop] Clean shutdown complete.");
            }));

        } catch (Exception e) {
            System.err.println("Failed to start ClassDrop server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean isHeadlessArg(String[] args) {
        return hasArg(args, "--headless", "-h");
    }

    private static boolean hasArg(String[] args, String... flags) {
        if (args == null || flags == null) return false;
        for (String a : args) {
            for (String f : flags) {
                if (f.equalsIgnoreCase(a)) return true;
            }
        }
        return false;
    }
}
