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

        String path = exchange.getRequestURI().getPath();
        String prefix = "/download/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            HandlerSupport.sendError(exchange, 400, "BAD_REQUEST", "Missing file ID in download path");
            return;
        }

        String fileId = path.substring(prefix.length());
        if (fileId.contains("/")) {
            fileId = fileId.substring(0, fileId.indexOf('/'));
        }

        try {
            SharedItem item = fileService.get(fileId);
            long size = item.getSizeBytes();
            String contentType = item.getContentType();
            String cleanName = item.getOriginalName();

            long start = 0;
            long end = size - 1;
            boolean partial = false;
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            if (rangeHeader != null && rangeHeader.startsWith("bytes=") && size > 0) {
                String spec = rangeHeader.substring("bytes=".length()).trim();
                String[] parts = spec.split("-", 2);
                try {
                    if (!parts[0].isEmpty()) {
                        start = Long.parseLong(parts[0]);
                    }
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                    if (start < 0 || start >= size || end < start) {
                        exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
                        HandlerSupport.setCorsHeaders(exchange);
                        exchange.sendResponseHeaders(416, -1);
                        return;
                    }
                    end = Math.min(end, size - 1);
                    partial = true;
                } catch (NumberFormatException ignored) {
                    start = 0;
                    end = size - 1;
                    partial = false;
                }
            }

            long contentLength = end - start + 1;
            exchange.getResponseHeaders().set("Content-Type", contentType != null ? contentType : "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + cleanName + "\"");
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));
            if (partial) {
                exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }
            HandlerSupport.setCorsHeaders(exchange);

            int status = partial ? 206 : 200;
            exchange.sendResponseHeaders(status, contentLength);

            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.close();
                return;
            }

            try (InputStream in = fileService.openDownloadStream(fileId, start);
                 OutputStream out = exchange.getResponseBody()) {
                byte[] buffer = new byte[Constants.CHUNK_BUFFER_SIZE];
                long remaining = contentLength;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = in.read(buffer, 0, toRead);
                    if (read == -1) break;
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
            }

            if (!partial || start == 0) {
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
            }

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
