package com.classdrop.server;

import com.classdrop.model.RoomSession;
import com.classdrop.service.QrService;
import com.classdrop.service.RoomService;
import com.classdrop.service.TunnelService;
import com.classdrop.util.ShareUrlBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public final class QrHandler implements HttpHandler {

    private final RoomService roomService;
    private final QrService qrService;
    private final int port;
    private final TunnelService tunnelService;

    public QrHandler(RoomService roomService, QrService qrService, int port) {
        this(roomService, qrService, port, null);
    }

    public QrHandler(RoomService roomService, QrService qrService, int port, TunnelService tunnelService) {
        this.roomService = roomService;
        this.qrService = qrService;
        this.port = port;
        this.tunnelService = tunnelService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null || !session.isActive()) {
            HandlerSupport.sendError(exchange, 410, "SESSION_EXPIRED", "No active room session");
            return;
        }

        String publicBase = tunnelService != null ? tunnelService.getPublicBaseUrl() : null;
        String shareUrl = ShareUrlBuilder.shareUrlForSession(session, port, publicBase);
        if (shareUrl == null) {
            // RELAY without public URL — fall back to local so QR endpoint is never empty
            shareUrl = ShareUrlBuilder.localRoomUrl(session.getHostIp(), port, session.getRoomCode());
        }
        byte[] png = qrService.generateQrPngBytes(shareUrl, 300);

        exchange.getResponseHeaders().set("Content-Type", "image/png");
        HandlerSupport.setCorsHeaders(exchange);
        exchange.sendResponseHeaders(200, png.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(png);
        }
    }
}
