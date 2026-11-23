package com.classdrop.server;

import com.classdrop.model.RoomSession;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.classdrop.util.ShareUrlBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/** Public metadata for join page (passphrase required? internet mode?). */
public final class SessionInfoHandler implements HttpHandler {
    private final RoomService roomService;

    public SessionInfoHandler(RoomService roomService) {
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
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed");
            return;
        }

        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null || !session.isActive()) {
            HandlerSupport.sendJson(exchange, 200, "{\"active\":false}");
            return;
        }

        String json = "{"
            + "\"active\":true,"
            + "\"roomCode\":" + JsonUtil.quote(session.getRoomCode()) + ","
            + "\"internetShare\":" + session.isInternetShare() + ","
            + "\"requiresPassphrase\":" + session.requiresPassphrase() + ","
            + "\"mode\":" + JsonUtil.quote(ShareUrlBuilder.displayModeName(session.getMode()))
            + "}";
        HandlerSupport.sendJson(exchange, 200, json);
    }
}
