package com.classdrop.server;

import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SharedItem;
import com.classdrop.model.TransferRecord;
import com.classdrop.service.RoomService;
import com.classdrop.service.SessionListener;
import com.classdrop.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events stream for live file/clipboard/session updates.
 */
public final class EventsHandler implements HttpHandler {
    private final RoomService roomService;
    private final List<SseClient> clients = new CopyOnWriteArrayList<>();

    public EventsHandler(RoomService roomService) {
        this.roomService = roomService;
        this.roomService.addListener(new SessionListener() {
            @Override public void onTransferCompleted(TransferRecord record) { broadcast("files", "{\"reason\":\"transfer\"}"); }
            @Override public void onClipboardUpdated(ClipboardEntry entry) { broadcast("clipboard", "{\"reason\":\"clipboard\"}"); }
            @Override public void onDeviceJoined(Device device) { broadcast("devices", "{\"reason\":\"joined\"}"); }
            @Override public void onDeviceLeft(Device device) { broadcast("devices", "{\"reason\":\"left\"}"); }
            @Override public void onSessionEnded(RoomSession session) { broadcast("session", "{\"active\":false}"); }
        });
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed for /events");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        HandlerSupport.setCorsHeaders(exchange);
        exchange.sendResponseHeaders(200, 0);

        SseClient client = new SseClient(exchange.getResponseBody());
        clients.add(client);
        try {
            client.send("hello", "{\"ok\":true,\"device\":" + JsonUtil.quote(device.getDisplayName()) + "}");
            // Keep connection open with heartbeat until client disconnects
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(15000);
                    client.send("ping", "{\"t\":" + System.currentTimeMillis() + "}");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException io) {
                    break;
                }
            }
        } finally {
            clients.remove(client);
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }

    private void broadcast(String event, String data) {
        for (SseClient client : clients) {
            try {
                client.send(event, data);
            } catch (IOException e) {
                clients.remove(client);
            }
        }
    }

    private static final class SseClient {
        private final OutputStream out;

        SseClient(OutputStream out) {
            this.out = out;
        }

        synchronized void send(String event, String data) throws IOException {
            String payload = "event: " + event + "\ndata: " + data + "\n\n";
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
