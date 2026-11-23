package com.classdrop.server;

import com.classdrop.exception.FileTooLargeException;
import com.classdrop.exception.SessionExpiredException;
import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.Device;
import com.classdrop.model.SharedItem;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.classdrop.util.Config;
import com.classdrop.util.MultipartParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UploadHandler implements HttpHandler {

    private final RoomService roomService;
    private final FileService fileService;
    private final MultipartParser parser = new MultipartParser();

    public UploadHandler(RoomService roomService, FileService fileService) {
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

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only POST is allowed for /upload");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "Content-Type must be multipart/form-data");
            return;
        }

        String boundary;
        try {
            boundary = MultipartParser.extractBoundary(contentType);
        } catch (TransferFailedException e) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", e.getMessage());
            return;
        }

        long maxBytes = Config.getMaxFileSizeBytes();
        Path sessionDir = fileService.getDataDir().resolve("tmp_" + System.currentTimeMillis());

        try (InputStream in = exchange.getRequestBody()) {
            List<MultipartParser.Part> parts = parser.parse(in, boundary, sessionDir, maxBytes);
            List<SharedItem> storedItems = new ArrayList<>();

            for (MultipartParser.Part part : parts) {
                if (part.isFile()) {
                    SharedItem item = fileService.ingestTempFile(
                        device,
                        part.getOriginalFilename(),
                        part.getContentType(),
                        part.getStoredFile()
                    );
                    storedItems.add(item);
                }
            }

            if (storedItems.isEmpty()) {
                HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "No files found in upload payload");
                return;
            }

            // Return JSON array of uploaded items
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < storedItems.size(); i++) {
                SharedItem item = storedItems.get(i);
                if (i > 0) json.append(",");
                json.append(String.format(
                    "{\"id\":\"%s\",\"originalName\":\"%s\",\"kind\":\"%s\",\"sizeBytes\":%d,\"contentType\":\"%s\",\"createdAt\":\"%s\"}",
                    item.getId(),
                    escapeJson(item.getOriginalName()),
                    item.getKind(),
                    item.getSizeBytes(),
                    item.getContentType(),
                    item.getCreatedAt()
                ));
            }
            json.append("]");

            HandlerSupport.sendJson(exchange, 200, json.toString());

        } catch (FileTooLargeException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (TransferFailedException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "TRANSFER_FAILED", e.getMessage());
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(sessionDir);
            } catch (Exception ignored) {}
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
