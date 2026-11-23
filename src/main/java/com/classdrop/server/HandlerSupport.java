package com.classdrop.server;

import com.classdrop.exception.ClassDropException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.service.RoomService;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HandlerSupport {
    private HandlerSupport() {}

    public static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        setCorsHeaders(exchange);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String errorCode, String message) throws IOException {
        String safeMsg = message != null ? message.replace("\"", "\\\"") : "An error occurred";
        // Include both "error" and "code" so web clients can match either field
        String json = String.format("{\"error\":\"%s\",\"code\":\"%s\",\"message\":\"%s\"}", errorCode, errorCode, safeMsg);
        sendJson(exchange, statusCode, json);
    }

    public static void sendClassDropException(HttpExchange exchange, ClassDropException ex) throws IOException {
        sendError(exchange, ex.getHttpStatusCode(), ex.getErrorCode(), ex.getMessage());
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    public static String getClientIp(HttpExchange exchange) {
        String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            return xff.split(",")[0].trim();
        }
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    public static String extractToken(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-ClassDrop-Token");
        if (token == null || token.trim().isEmpty()) {
            token = exchange.getRequestHeaders().getFirst("X-ClassDrop-Host-Token");
        }
        if (token == null || token.trim().isEmpty()) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        break;
                    }
                    if (param.startsWith("hostToken=")) {
                        token = param.substring(10);
                        break;
                    }
                }
            }
        }
        return token != null ? token.trim() : null;
    }

    public static Device requireToken(HttpExchange exchange, RoomService roomService) throws IOException {
        String token = extractToken(exchange);
        if (token == null || token.isEmpty()) {
            sendError(exchange, 401, "UNAUTHORIZED", "Missing authentication token. Please join the room.");
            return null;
        }

        try {
            Device device = roomService.validateToken(token);
            if (device == null) {
                sendError(exchange, 401, "UNAUTHORIZED", "Invalid token or device not recognized.");
                return null;
            }
            return device;
        } catch (SessionExpiredException e) {
            sendClassDropException(exchange, e);
            return null;
        }
    }

    /** Accepts either a student device token or the session host token. */
    public static boolean requireHostOrDevice(HttpExchange exchange, RoomService roomService) throws IOException {
        String token = extractToken(exchange);
        if (token == null || token.isEmpty()) {
            sendError(exchange, 401, "UNAUTHORIZED", "Missing authentication token.");
            return false;
        }
        try {
            if (roomService.isHostToken(token)) {
                return true;
            }
            Device device = roomService.validateToken(token);
            if (device == null) {
                sendError(exchange, 401, "UNAUTHORIZED", "Invalid token.");
                return false;
            }
            return true;
        } catch (SessionExpiredException e) {
            sendClassDropException(exchange, e);
            return false;
        }
    }

    public static boolean requireHostToken(HttpExchange exchange, RoomService roomService) throws IOException {
        String token = extractToken(exchange);
        if (token == null || token.isEmpty()) {
            sendError(exchange, 401, "UNAUTHORIZED", "Missing host token.");
            return false;
        }
        try {
            if (!roomService.isHostToken(token)) {
                sendError(exchange, 403, "FORBIDDEN", "Host token required for this action.");
                return false;
            }
            return true;
        } catch (SessionExpiredException e) {
            sendClassDropException(exchange, e);
            return false;
        }
    }

    public static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
            "Content-Type, X-ClassDrop-Token, X-ClassDrop-Host-Token, Range");
        exchange.getResponseHeaders().set("Access-Control-Expose-Headers",
            "Content-Disposition, Content-Range, Accept-Ranges, Content-Length");
    }
}
