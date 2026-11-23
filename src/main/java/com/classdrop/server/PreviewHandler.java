package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.Device;
import com.classdrop.model.SharedItem;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.classdrop.util.Constants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Inline image preview (Content-Disposition: inline). */
public final class PreviewHandler implements HttpHandler {
    private final RoomService roomService;
    private final FileService fileService;

    public PreviewHandler(RoomService roomService, FileService fileService) {
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
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed for /preview");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) return;

        String path = exchange.getRequestURI().getPath();
        String prefix = "/preview/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "Missing file id");
            return;
        }
        String fileId = path.substring(prefix.length());
        if (fileId.contains("/")) fileId = fileId.substring(0, fileId.indexOf('/'));

        try {
            SharedItem item = fileService.get(fileId);
            if (!"IMAGE".equals(item.getKind())) {
                HandlerSupport.sendError(exchange, 400, "NOT_IMAGE", "Preview is only available for images");
                return;
            }
            long size = item.getSizeBytes();
            exchange.getResponseHeaders().set("Content-Type", item.getContentType() != null ? item.getContentType() : "image/jpeg");
            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + item.getOriginalName() + "\"");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(size));
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(200, size);

            try (InputStream in = fileService.openDownloadStream(fileId);
                 OutputStream out = exchange.getResponseBody()) {
                byte[] buffer = new byte[Constants.CHUNK_BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (TransferFailedException e) {
            HandlerSupport.sendError(exchange, 404, "FILE_NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "PREVIEW_FAILED", e.getMessage());
        }
    }
}
