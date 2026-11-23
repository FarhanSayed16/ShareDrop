package com.classdrop.server;

import com.classdrop.exception.InvalidPassphraseException;
import com.classdrop.exception.InvalidRoomCodeException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.classdrop.util.ShareUrlBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        Map<String, String> json = JsonUtil.parseObject(body);
        String roomCode = JsonUtil.getString(json, "roomCode");
        String deviceName = JsonUtil.getString(json, "deviceName");
        if (deviceName == null) {
            deviceName = JsonUtil.getString(json, "displayName");
        }
        String passphrase = JsonUtil.getString(json, "passphrase");

        if (roomCode == null || roomCode.isEmpty()) {
            roomCode = extractFormParam(body, "roomCode");
        }
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = extractFormParam(body, "deviceName");
        }
        if (passphrase == null) {
            passphrase = extractFormParam(body, "passphrase");
        }

        if (roomCode == null || roomCode.trim().isEmpty()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "roomCode is required");
            return;
        }

        try {
            Device device = roomService.join(roomCode.trim(), deviceName, clientIp, passphrase);
            RoomSession session = roomService.getCurrentSessionOrNull();
            String resp = "{"
                + "\"token\":" + JsonUtil.quote(device.getToken()) + ","
                + "\"deviceId\":" + JsonUtil.quote(device.getId()) + ","
                + "\"deviceName\":" + JsonUtil.quote(device.getDisplayName()) + ","
                + "\"sessionActive\":true,"
                + "\"internetShare\":" + (session != null && session.isInternetShare()) + ","
                + "\"mode\":" + JsonUtil.quote(session != null ? ShareUrlBuilder.displayModeName(session.getMode()) : "LAN")
                + "}";
            HandlerSupport.sendJson(exchange, 200, resp);
        } catch (InvalidRoomCodeException | InvalidPassphraseException | SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private String extractFormParam(String body, String key) {
        if (body == null || body.isBlank()) return null;
        Pattern formPattern = Pattern.compile("(?:^|&)" + Pattern.quote(key) + "=([^&]*)");
        Matcher formMatcher = formPattern.matcher(body);
        if (formMatcher.find()) {
            return URLDecoder.decode(formMatcher.group(1), StandardCharsets.UTF_8);
        }
        return null;
    }
}
