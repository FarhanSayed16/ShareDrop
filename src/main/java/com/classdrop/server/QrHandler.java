package com.classdrop.server;

import com.classdrop.model.RoomSession;
import com.classdrop.service.QrService;
import com.classdrop.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public final class QrHandler implements HttpHandler {

    private final RoomService roomService;
    private final QrService qrService;
    private final int port;

    public QrHandler(RoomService roomService, QrService qrService, int port) {
        this.roomService = roomService;
        this.qrService = qrService;
        this.port = port;
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

        String shareUrl = String.format("http://%s:%d/r/%s", session.getHostIp(), port, session.getRoomCode());
        byte[] png = qrService.generateQrPngBytes(shareUrl, 300);

        exchange.getResponseHeaders().set("Content-Type", "image/png");
        HandlerSupport.setCorsHeaders(exchange);
        exchange.sendResponseHeaders(200, png.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(png);
        }
    }
}
