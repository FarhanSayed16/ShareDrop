package com.classdrop.server;

import com.classdrop.service.NetworkInfoService;
import com.classdrop.service.RoomService;
import com.classdrop.service.StatsService;
import com.classdrop.util.Constants;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClassDropServer {
    private final RoomService roomService;
    private final com.classdrop.service.FileService fileService;
    private final com.classdrop.service.ClipboardService clipboardService;
    private final com.classdrop.service.QrService qrService;
    private final StatsService statsService;
    private final com.classdrop.service.TunnelService tunnelService;
    private final NetworkInfoService networkInfoService;
    private final com.classdrop.service.MdnsService mdnsService;
    private HttpServer server;
    private ExecutorService executor;
    private volatile boolean running = false;
    private int boundPort;

    public ClassDropServer(RoomService roomService, com.classdrop.service.FileService fileService,
                           com.classdrop.service.ClipboardService clipboardService, com.classdrop.service.QrService qrService) {
        this(roomService, fileService, clipboardService, qrService, null, null, null);
    }

    public ClassDropServer(RoomService roomService, com.classdrop.service.FileService fileService,
                           com.classdrop.service.ClipboardService clipboardService, com.classdrop.service.QrService qrService,
                           StatsService statsService) {
        this(roomService, fileService, clipboardService, qrService, statsService, null, null);
    }

    public ClassDropServer(RoomService roomService, com.classdrop.service.FileService fileService,
                           com.classdrop.service.ClipboardService clipboardService, com.classdrop.service.QrService qrService,
                           StatsService statsService, com.classdrop.service.TunnelService tunnelService) {
        this(roomService, fileService, clipboardService, qrService, statsService, tunnelService, null);
    }

    public ClassDropServer(RoomService roomService, com.classdrop.service.FileService fileService,
                           com.classdrop.service.ClipboardService clipboardService, com.classdrop.service.QrService qrService,
                           StatsService statsService, com.classdrop.service.TunnelService tunnelService,
                           NetworkInfoService networkInfoService) {
        this.roomService = roomService;
        this.fileService = fileService;
        this.clipboardService = clipboardService;
        this.qrService = qrService;
        this.statsService = statsService;
        this.tunnelService = tunnelService;
        this.networkInfoService = networkInfoService != null ? networkInfoService : new NetworkInfoService();
        this.mdnsService = new com.classdrop.service.MdnsService();
    }

    public ClassDropServer(RoomService roomService, com.classdrop.service.FileService fileService, com.classdrop.service.ClipboardService clipboardService) {
        this(roomService, fileService, clipboardService, new com.classdrop.service.QrService(), null, null);
    }

    public ClassDropServer(RoomService roomService, com.classdrop.service.FileService fileService) {
        this(roomService, fileService, new com.classdrop.service.ClipboardService(roomService), new com.classdrop.service.QrService(), null, null);
    }

    public ClassDropServer(RoomService roomService) {
        this(roomService, new com.classdrop.service.FileService(roomService), new com.classdrop.service.ClipboardService(roomService), new com.classdrop.service.QrService(), null, null);
    }

    public synchronized void start() throws IOException {
        start(Constants.PORT);
    }

    public synchronized void start(int port) throws IOException {
        if (running) return;

        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        // Expanded pool so SSE /events clients do not starve uploads/downloads
        executor = Executors.newFixedThreadPool(Constants.HTTP_EXECUTOR_SIZE, r -> {
            Thread t = new Thread(r, "ClassDrop-HTTP");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);

        server.createContext("/health", exchange -> {
            HandlerSupport.sendJson(exchange, 200, "{\"ok\":true,\"service\":\"ClassDrop\"}");
        });

        CaptivePortalHandler captive = new CaptivePortalHandler();
        server.createContext("/generate_204", captive);
        server.createContext("/gen_204", captive);
        server.createContext("/connecttest.txt", captive);
        server.createContext("/ncsi.txt", captive);
        server.createContext("/hotspot-detect.html", captive);
        server.createContext("/library/test/success.html", captive);
        server.createContext("/success.html", captive);

        StaticHandler staticHandler = new StaticHandler();
        server.createContext("/", staticHandler);
        server.createContext("/about", staticHandler);
        server.createContext("/privacy", staticHandler);
        server.createContext("/static", staticHandler);
        server.createContext("/connect", staticHandler);

        server.createContext("/r/", new RoomLinkHandler());
        server.createContext("/join", new JoinHandler(roomService));
        server.createContext("/upload", new UploadHandler(roomService, fileService));
        server.createContext("/files", new FilesHandler(roomService, fileService));
        server.createContext("/download", new DownloadHandler(roomService, fileService));
        server.createContext("/download-all.zip", new ZipDownloadHandler(roomService, fileService));
        server.createContext("/preview", new PreviewHandler(roomService, fileService));
        server.createContext("/clipboard", new ClipboardHandler(roomService, clipboardService));
        server.createContext("/qr", new QrHandler(roomService, qrService, port, tunnelService));
        server.createContext("/session-info", new SessionInfoHandler(roomService));
        server.createContext("/network-status", new NetworkStatusHandler(roomService, networkInfoService, port));
        server.createContext("/me", new MeHandler(roomService));
        server.createContext("/stats", new StatsHandler(roomService, statsService));
        server.createContext("/kick", new KickHandler(roomService));
        server.createContext("/events", new EventsHandler(roomService));
        server.createContext("/remove-file", new RemoveFileHandler(roomService, fileService));

        server.start();
        this.boundPort = port;
        this.running = true;

        try {
            String ip = networkInfoService.getPreferredIP();
            mdnsService.start(port, ip);
        } catch (Exception ignored) {
        }
    }

    public synchronized void stop() {
        if (!running) return;
        try {
            mdnsService.stop();
        } catch (Exception ignored) {
        }
        if (server != null) {
            server.stop(1);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        this.running = false;
    }

    public com.classdrop.service.MdnsService getMdnsService() {
        return mdnsService;
    }

    public boolean isRunning() {
        return running;
    }

    public int getBoundPort() {
        return boundPort;
    }

    public HttpServer getHttpServer() {
        return server;
    }
}
