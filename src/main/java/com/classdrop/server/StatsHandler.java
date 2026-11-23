package com.classdrop.server;

import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SharedItem;
import com.classdrop.service.RoomService;
import com.classdrop.service.StatsService;
import com.classdrop.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public final class StatsHandler implements HttpHandler {
    private final RoomService roomService;
    private final StatsService statsService;

    public StatsHandler(RoomService roomService, StatsService statsService) {
        this.roomService = roomService;
        this.statsService = statsService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed for /stats");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        try {
            RoomSession session = roomService.current();
            StatsService.SessionStats dbStats = statsService != null
                ? statsService.getSessionStats(session.getId())
                : new StatsService.SessionStats(session.getId(), 0, 0, 0, 0, 0);

            long deviceCount = Math.max(dbStats.deviceCount(), session.getDevices().size());
            long fileCount = session.getItems().size();
            long liveBytes = 0;
            for (SharedItem item : session.getItems()) {
                liveBytes += item.getSizeBytes();
            }
            long transferCount = Math.max(dbStats.transferCount(), fileCount);
            long totalBytes = Math.max(dbStats.totalBytes(), liveBytes);

            String json = "{"
                + "\"sessionId\":" + JsonUtil.quote(session.getId()) + ","
                + "\"roomCode\":" + JsonUtil.quote(session.getRoomCode()) + ","
                + "\"deviceCount\":" + deviceCount + ","
                + "\"fileCount\":" + fileCount + ","
                + "\"transferCount\":" + transferCount + ","
                + "\"totalBytes\":" + totalBytes + ","
                + "\"uploadCount\":" + dbStats.uploadCount() + ","
                + "\"downloadCount\":" + dbStats.downloadCount() + ","
                + "\"activeUsers\":" + RoomSession.getActiveUserCount()
                + "}";
            HandlerSupport.sendJson(exchange, 200, json);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "STATS_ERROR", e.getMessage());
        }
    }
}
