package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.service.ClipboardService;
import com.classdrop.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

public final class ClipboardHandler implements HttpHandler {

    private final RoomService roomService;
    private final ClipboardService clipboardService;

    public ClipboardHandler(RoomService roomService, ClipboardService clipboardService) {
        this.roomService = roomService;
        this.clipboardService = clipboardService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();
        try {
            if ("GET".equals(method)) {
                handleGet(exchange);
            } else if ("POST".equals(method)) {
                handlePost(exchange, device);
            } else {
                HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET and POST are allowed for /clipboard");
            }
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "CLIPBOARD_ERROR", e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException, SessionExpiredException {
        List<ClipboardEntry> list = clipboardService.list();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            ClipboardEntry e = list.get(i);
            if (i > 0) json.append(",");
            json.append(formatEntryJson(e));
        }
        json.append("]");

        HandlerSupport.sendJson(exchange, 200, json.toString());
    }

    private void handlePost(HttpExchange exchange, Device device) throws IOException, SessionExpiredException {
        String bodyText = HandlerSupport.readBody(exchange);
        if (bodyText == null || bodyText.trim().isEmpty()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "Clipboard body is required");
            return;
        }

        java.util.Map<String, String> parsed = com.classdrop.util.JsonUtil.parseObject(bodyText);
        String snippet = com.classdrop.util.JsonUtil.getString(parsed, "body");
        String title = com.classdrop.util.JsonUtil.getString(parsed, "title");
        String language = com.classdrop.util.JsonUtil.getString(parsed, "language");

        if (snippet == null || snippet.trim().isEmpty()) {
            // Fallback: if body is raw plain text
            snippet = bodyText;
        }

        ClipboardEntry entry = clipboardService.post(device, title, language, snippet);
        HandlerSupport.sendJson(exchange, 200, formatEntryJson(entry));
    }

    private String formatEntryJson(ClipboardEntry e) {
        return String.format(
            "{\"id\":\"%s\",\"sessionId\":\"%s\",\"deviceId\":\"%s\",\"deviceName\":\"%s\",\"title\":\"%s\",\"language\":\"%s\",\"body\":\"%s\",\"createdAt\":\"%s\"}",
            e.getId(),
            e.getSessionId(),
            e.getDeviceId(),
            escapeJson(e.getDeviceName()),
            escapeJson(e.getTitle() != null ? e.getTitle() : ""),
            escapeJson(e.getLanguage()),
            escapeJson(e.getBody()),
            e.getCreatedAt()
        );
    }

    private String extractJsonField(String json, String key) {
        if (json == null) return null;
        // Match JSON string value allowing escaped quotes \"
        String pattern = "\"" + key + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
