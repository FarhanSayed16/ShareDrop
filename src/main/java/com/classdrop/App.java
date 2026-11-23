package com.classdrop;

import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.server.ClassDropServer;
import com.classdrop.service.NetworkInfoService;
import com.classdrop.service.RoomService;
import com.classdrop.util.Constants;
import com.classdrop.util.ShareLinks;

public final class App {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  ClassDrop — Offline Classroom File Sharing Hub  ");
        System.out.println("==================================================");

        NetworkInfoService networkInfo = new NetworkInfoService();
        String hostIp = networkInfo.getPreferredIP();
        NetworkMode mode = networkInfo.detectMode(hostIp);

        // Database layer with graceful degradation if offline
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
        com.classdrop.service.CleanupTask cleanupTask = new com.classdrop.service.CleanupTask(roomService, fileService);
        cleanupTask.start();

        if (hasArg(args, "--restore", "-r")) {
            com.classdrop.service.SnapshotService snapshotService = 
                new com.classdrop.service.SnapshotService(roomService, fileService, clipboardService);
            if (snapshotService.restoreDefault()) {
                session = roomService.getCurrentSessionOrNull();
                System.out.println("  [ClassDrop] Active session restored from snapshot: " + session.getRoomCode());
            }
        }

        ClassDropServer server = new ClassDropServer(roomService, fileService, clipboardService, qrService);

        int port = com.classdrop.util.Config.getPort();

        try {
            server.start(port);

            String shareUrl = String.format("http://%s:%d/r/%s", hostIp, port, session.getRoomCode());
            String whatsapp = ShareLinks.whatsappUrl(shareUrl);
            String email = ShareLinks.emailUrl(shareUrl);

            System.out.println("  Status:    Server RUNNING on 0.0.0.0:" + port);
            System.out.println("  Mode:      " + mode);
            System.out.println("  Host IP:   " + hostIp);
            System.out.println("  Room Code: " + session.getRoomCode());
            System.out.println("  Share URL: " + shareUrl);
            System.out.println("--------------------------------------------------");
            System.out.println("  WhatsApp:  " + whatsapp);
            System.out.println("  Email:     " + email);
            System.out.println("==================================================");
            System.out.println("  Press Ctrl+C to terminate session and clean up. ");
            System.out.println("==================================================");

            // Launch Swing Host Desktop GUI if display environment is available
            if (!java.awt.GraphicsEnvironment.isHeadless() && !isHeadlessArg(args)) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    try {
                        com.classdrop.ui.ClassDropFrame frame = new com.classdrop.ui.ClassDropFrame(
                            roomService, fileService, clipboardService, qrService, networkInfo, port, statsService, transferRepo
                        );
                        frame.setVisible(true);
                    } catch (Exception e) {
                        System.err.println("[ClassDrop] Could not launch Swing GUI: " + e.getMessage());
                    }
                });
            }

            // Shutdown hook to ensure clean exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[ClassDrop] Terminating session and stopping server...");
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
