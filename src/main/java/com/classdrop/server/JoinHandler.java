package com.classdrop.server;

import com.classdrop.exception.InvalidRoomCodeException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public final class JoinHandler implements HttpHandler {
    private final RoomService roomService;

    public JoinHandler(RoomService roomService) {
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
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only POST is allowed for /join");
            return;
        }

        String body = HandlerSupport.readBody(exchange);
        String clientIp = HandlerSupport.getClientIp(exchange);

        // Robust extraction for roomCode and deviceName (JSON or Form-URL-Encoded)
        String roomCode = extractParam(body, "roomCode");
        String deviceName = extractParam(body, "deviceName");

        if (roomCode == null || roomCode.trim().isEmpty()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "roomCode is required");
            return;
        }

        try {
            Device device = roomService.join(roomCode.trim(), deviceName, clientIp);
            String resp = String.format(
                "{\"token\":\"%s\",\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"sessionActive\":true}",
                device.getToken(), device.getId(), device.getDisplayName()
            );
            HandlerSupport.sendJson(exchange, 200, resp);
        } catch (InvalidRoomCodeException | SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private String extractParam(String body, String key) {
        if (body == null || body.trim().isEmpty()) return null;

        // Try JSON pattern: "key": "value" or "key": 123456
        String jsonPattern = "\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?";
        java.util.regex.Matcher jsonMatcher = java.util.regex.Pattern.compile(jsonPattern).matcher(body);
        if (jsonMatcher.find()) {
            String val = jsonMatcher.group(1).trim();
            if (val.startsWith("\"")) val = val.substring(1);
            if (val.endsWith("\"")) val = val.substring(0, val.length() - 1);
            return val;
        }

        // Try URL-encoded pattern: key=value
        String formPattern = "(?:^|&)" + key + "=([^&]*)";
        java.util.regex.Matcher formMatcher = java.util.regex.Pattern.compile(formPattern).matcher(body);
        if (formMatcher.find()) {
            try {
                return java.net.URLDecoder.decode(formMatcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return formMatcher.group(1);
            }
        }

        return null;
    }
}
