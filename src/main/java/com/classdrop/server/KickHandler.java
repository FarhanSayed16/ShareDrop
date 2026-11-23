package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

public final class KickHandler implements HttpHandler {
    private final RoomService roomService;

    public KickHandler(RoomService roomService) {
        this.roomService = roomService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only POST allowed for /kick");
            return;
        }
        if (!HandlerSupport.requireHostToken(exchange, roomService)) {
            return;
        }

        Map<String, String> body = JsonUtil.parseObject(HandlerSupport.readBody(exchange));
        String deviceId = JsonUtil.getString(body, "deviceId");
        if (deviceId == null) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "deviceId is required");
            return;
        }

        try {
            Device kicked = roomService.kickDevice(deviceId);
            if (kicked == null) {
                HandlerSupport.sendError(exchange, 404, "DEVICE_NOT_FOUND", "No device with that id in the session");
                return;
            }
            String json = "{\"ok\":true,\"deviceId\":" + JsonUtil.quote(kicked.getId())
                + ",\"deviceName\":" + JsonUtil.quote(kicked.getDisplayName()) + "}";
            HandlerSupport.sendJson(exchange, 200, json);
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "KICK_FAILED", e.getMessage());
        }
    }
}
