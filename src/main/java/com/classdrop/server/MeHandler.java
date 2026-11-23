package com.classdrop.server;

import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public final class MeHandler implements HttpHandler {
    private final RoomService roomService;

    public MeHandler(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed for /me");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        try {
            RoomSession session = roomService.current();
            String json = "{"
                + "\"deviceId\":" + JsonUtil.quote(device.getId()) + ","
                + "\"deviceName\":" + JsonUtil.quote(device.getDisplayName()) + ","
                + "\"ipAddress\":" + JsonUtil.quote(device.getIpAddress() != null ? device.getIpAddress() : "") + ","
                + "\"roomCode\":" + JsonUtil.quote(session.getRoomCode()) + ","
                + "\"sessionId\":" + JsonUtil.quote(session.getId()) + ","
                + "\"sessionActive\":true"
                + "}";
            HandlerSupport.sendJson(exchange, 200, json);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "ME_ERROR", e.getMessage());
        }
    }
}
