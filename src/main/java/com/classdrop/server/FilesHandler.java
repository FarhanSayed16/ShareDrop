package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.model.Device;
import com.classdrop.model.SharedItem;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

public final class FilesHandler implements HttpHandler {

    private final RoomService roomService;
    private final FileService fileService;
    private final RemoveFileHandler removeDelegate;

    public FilesHandler(RoomService roomService, FileService fileService) {
        this.roomService = roomService;
        this.fileService = fileService;
        this.removeDelegate = new RemoveFileHandler(roomService, fileService);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String method = exchange.getRequestMethod().toUpperCase();
        String path = exchange.getRequestURI().getPath();

        // DELETE /files/{id} or POST /files/{id}/remove
        if ("DELETE".equals(method) || ("POST".equals(method) && path.contains("/remove"))) {
            removeDelegate.handle(exchange);
            return;
        }

        if (!"GET".equals(method)) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "GET or DELETE allowed for /files");
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
                boolean previewable = "IMAGE".equals(item.getKind());
                json.append("{")
                    .append("\"id\":").append(JsonUtil.quote(item.getId())).append(",")
                    .append("\"originalName\":").append(JsonUtil.quote(item.getOriginalName())).append(",")
                    .append("\"kind\":").append(JsonUtil.quote(item.getKind())).append(",")
                    .append("\"sizeBytes\":").append(item.getSizeBytes()).append(",")
                    .append("\"contentType\":").append(JsonUtil.quote(item.getContentType())).append(",")
                    .append("\"uploaderDeviceId\":").append(JsonUtil.quote(item.getUploaderDeviceId())).append(",")
                    .append("\"createdAt\":").append(JsonUtil.quote(String.valueOf(item.getCreatedAt()))).append(",")
                    .append("\"previewable\":").append(previewable)
                    .append("}");
            }
            json.append("]");

            HandlerSupport.sendJson(exchange, 200, json.toString());
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "INTERNAL_ERROR", e.getMessage());
        }
    }
}
