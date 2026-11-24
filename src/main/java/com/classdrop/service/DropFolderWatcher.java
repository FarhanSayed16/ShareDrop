package com.classdrop.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches a local folder (USB stick / sneakernet drop) and notifies when new files appear.
 * Fully offline — no network required.
 */
public final class DropFolderWatcher implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(DropFolderWatcher.class.getName());

    private final Path folder;
    private final Consumer<Path> onNewFile;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread worker;

    public DropFolderWatcher(Path folder, Consumer<Path> onNewFile) {
        this.folder = Objects.requireNonNull(folder);
        this.onNewFile = Objects.requireNonNull(onNewFile);
    }

    public Path getFolder() {
        return folder;
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;
        Files.createDirectories(folder);
        watchService = FileSystems.getDefault().newWatchService();
        folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        running.set(true);
        worker = new Thread(this::loop, "ClassDrop-DropFolder");
        worker.setDaemon(true);
        worker.start();
        LOGGER.info("[ClassDrop-DropFolder] Watching " + folder.toAbsolutePath());
    }

    public synchronized void stop() {
        running.set(false);
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
            }
            watchService = null;
        }
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void loop() {
        while (running.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (ClosedWatchServiceException | InterruptedException e) {
                break;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path child = folder.resolve(ev.context());
                if (Files.isRegularFile(child)) {
                    // Brief settle time for large USB copies
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    try {
                        onNewFile.accept(child);
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Drop folder ingest failed: {0}", ex.getMessage());
                    }
                }
            }
            if (!key.reset()) break;
        }
        running.set(false);
    }

    @Override
    public void close() {
        stop();
    }
}
