package com.classdrop.server;

import com.classdrop.exception.SessionExpiredException;
import com.classdrop.exception.TransferFailedException;
import com.classdrop.model.Device;
import com.classdrop.service.FileService;
import com.classdrop.service.RoomService;
import com.classdrop.util.Constants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZipDownloadHandler implements HttpHandler {
    private final RoomService roomService;
    private final FileService fileService;

    public ZipDownloadHandler(RoomService roomService, FileService fileService) {
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
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed for ZIP download");
            return;
        }

        Device device = HandlerSupport.requireToken(exchange, roomService);
        if (device == null) {
            return;
        }

        Path zipPath = null;
        try {
            Path tmp = fileService.getDataDir().resolve("tmp_zip_" + System.currentTimeMillis() + ".zip");
            zipPath = fileService.createZipArchive(tmp);
            long size = Files.size(zipPath);

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"classdrop-room.zip\"");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(size));
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(200, size);

            try (InputStream in = Files.newInputStream(zipPath);
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
            HandlerSupport.sendError(exchange, 400, "ZIP_FAILED", e.getMessage());
        } catch (Exception e) {
            HandlerSupport.sendError(exchange, 500, "ZIP_FAILED", e.getMessage());
        } finally {
            if (zipPath != null) {
                try { Files.deleteIfExists(zipPath); } catch (Exception ignored) {}
            }
        }
    }
}
