package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.model.SharedItem;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

public final class FilesHandler implements HttpHandler {

    private final RoomService roomService;
    private final FileService fileService;

    public FilesHandler(RoomService roomService, FileService fileService) {
        this.roomService = roomService;
        this.fileService = fileService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET is allowed for /files");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        try {
            List<SharedItem> items = fileService.listFiles();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                SharedItem item = items.get(i);
                if (i > 0) json.append(",");
                json.append(String.format(
                    "{\"id\":\"%s\",\"originalName\":\"%s\",\"kind\":\"%s\",\"sizeBytes\":%d,\"contentType\":\"%s\",\"uploaderDeviceId\":\"%s\",\"createdAt\":\"%s\"}",
                    item.getId(),
                    escapeJson(item.getOriginalName()),
                    item.getKind(),
                    item.getSizeBytes(),
                    item.getContentType(),
                    item.getUploaderDeviceId(),
                    item.getCreatedAt()
                ));
            }
            json.append("]");

            HandlerSupport.sendJson(exchange, 200, json.toString());
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "INTERNAL_ERROR", e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
