package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.SharedItem;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public final class RemoveFileHandler implements HttpHandler {
    private final RoomService roomService;
    private final FileService fileService;

    public RemoveFileHandler(RoomService roomService, FileService fileService) {
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

        String method = exchange.getRequestMethod().toUpperCase();
        if (!"DELETE".equals(method) && !"POST".equals(method)) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Use DELETE or POST for file removal");
            return;
        }

        if (!HandlerSupport.requireHostToken(exchange, roomService)) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String fileId = null;
        if (path.startsWith("/files/") && path.length() > "/files/".length()) {
            fileId = path.substring("/files/".length());
            if (fileId.contains("/")) {
                fileId = fileId.substring(0, fileId.indexOf('/'));
            }
        }
        if ((fileId == null || fileId.isBlank()) && "POST".equals(method)) {
            var body = JsonUtil.parseObject(HandlerSupport.readBody(exchange));
            fileId = JsonUtil.getString(body, "fileId");
        }
        if (fileId == null || fileId.isBlank()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "Missing file id");
            return;
        }

        try {
            SharedItem removed = fileService.removeFile(fileId.trim());
            String json = "{\"ok\":true,\"removedId\":" + JsonUtil.quote(removed.getId())
                + ",\"originalName\":" + JsonUtil.quote(removed.getOriginalName()) + "}";
            HandlerSupport.sendJson(exchange, 200, json);
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (TransferFailedException e) {
            HandlerSupport.sendError(exchange, 404, "FILE_NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "REMOVE_FAILED", e.getMessage());
        }
    }
}
