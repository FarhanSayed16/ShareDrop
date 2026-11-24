package com.classdrop.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Responds to OS captive-portal / connectivity probes so phones on an offline
 * hotspot get predictable answers instead of hanging. Also redirects common
 * "sign in to network" probes toward the ClassDrop join page.
 */
public final class CaptivePortalHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null) path = "/";
        String lower = path.toLowerCase();

        // Android connectivity check — 204 = "no captive login required"
        if (lower.contains("generate_204") || lower.contains("gen_204")) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        // Windows NCSI
        if (lower.contains("ncsi.txt") || lower.contains("connecttest.txt")) {
            byte[] body = "Microsoft Connect Test".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            return;
        }

        // Apple captive portal success page
        if (lower.contains("hotspot-detect") || lower.contains("success.html") || lower.contains("library/test")) {
            byte[] body = "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            return;
        }

        // Unknown probe under captive paths → send students to ClassDrop
        exchange.getResponseHeaders().set("Location", "/");
        HandlerSupport.setCorsHeaders(exchange);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }
}
