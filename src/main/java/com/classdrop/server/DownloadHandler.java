package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.Device;
import com.classdrop.model.RoomSession;
import com.classdrop.model.SharedItem;
import com.classdrop.model.TransferRecord;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.classdrop.service.SessionListener;
import com.classdrop.util.Constants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;

public final class DownloadHandler implements HttpHandler {

    private final RoomService roomService;
    private final FileService fileService;

    public DownloadHandler(RoomService roomService, FileService fileService) {
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

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET/HEAD allowed for download");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        // Path format: /download/{id}
        String path = exchange.getRequestURI().getPath();
        String prefix = "/download/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "Missing file ID in download path");
            return;
        }

        String fileId = path.substring(prefix.length());
        // Clean any trailing slash or query
        if (fileId.contains("/")) {
            fileId = fileId.substring(0, fileId.indexOf('/'));
        }

        try {
            SharedItem item = fileService.get(fileId);
            long size = item.getSizeBytes();
            String contentType = item.getContentType();
            String cleanName = item.getOriginalName();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + cleanName + "\"");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(size));
            HandlerSupport.setCorsHeaders(exchange);

            exchange.sendResponseHeaders(200, size);

            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.close();
                return;
            }

            // Stream file directly from disk to network in 64 KB chunks
            try (InputStream in = fileService.openDownloadStream(fileId);
                 OutputStream out = exchange.getResponseBody()) {

                byte[] buffer = new byte[Constants.CHUNK_BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            // Record download completion
            fileService.recordDownload(device, item);
            RoomSession session = roomService.current();
            TransferRecord record = new TransferRecord(
                session.getId(),
                device.getId(),
                item.getKind(),
                cleanName,
                fileId,
                size,
                contentType,
                "DOWNLOAD"
            );
            notifyTransferCompleted(record);

        } catch (SessionExpiredException e) {
            HandlerSupport.sendClassDropException(exchange, e);
        } catch (TransferFailedException e) {
            HandlerSupport.sendError(exchange, 404, "FILE_NOT_FOUND", e.getMessage());
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "DOWNLOAD_FAILED", e.getMessage());
        }
    }

    private void notifyTransferCompleted(TransferRecord record) {
        for (SessionListener l : roomService.getListeners()) {
            try { l.onTransferCompleted(record); } catch (Exception ignored) {}
        }
    }
}
