package com.classdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }

        URI uri = exchange.getRequestURI();
        String path = uri.getPath();

        String resourceName;
        if ("/".equals(path) || path.startsWith("/r/")) {
            resourceName = "/web/index.html";
        } else if ("/about".equals(path)) {
            resourceName = "/web/about.html";
        } else if ("/privacy".equals(path)) {
            resourceName = "/web/privacy.html";
        } else if (path.startsWith("/static/")) {
            String sub = path.substring("/static/".length());
            // Security check against path traversal
            if (sub.contains("..") || sub.contains("\\")) {
                HandlerSupport.sendError(exchange, 403, "FORBIDDEN", "Path traversal forbidden");
                return;
            }
            resourceName = "/web/" + sub;
        } else {
            HandlerSupport.sendError(exchange, 404, "NOT_FOUND", "Resource not found");
            return;
        }

        // Try reading from classpath resources first
        try (InputStream is = getClass().getResourceAsStream(resourceName)) {
            if (is == null) {
                // Fallback to local files if running in IDE without jar packaging
                Path localFile = Paths.get("src/main/resources" + resourceName);
                if (Files.exists(localFile) && !Files.isDirectory(localFile)) {
                    byte[] bytes = Files.readAllBytes(localFile);
                    serveBytes(exchange, resourceName, bytes);
                    return;
                }
                HandlerSupport.sendError(exchange, 404, "NOT_FOUND", "Resource not found");
                return;
            }

            byte[] bytes = is.readAllBytes();
            serveBytes(exchange, resourceName, bytes);
        }
    }

    private void serveBytes(HttpExchange exchange, String resourceName, byte[] bytes) throws IOException {
        String contentType = detectContentType(resourceName);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        HandlerSupport.setCorsHeaders(exchange);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String detectContentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
