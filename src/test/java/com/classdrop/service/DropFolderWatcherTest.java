package com.classdrop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DropFolderWatcherTest {

    @TempDir
    Path temp;

    @Test
    void detectsNewFile() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Path> seen = new AtomicReference<>();
        DropFolderWatcher watcher = new DropFolderWatcher(temp, p -> {
            seen.set(p);
            latch.countDown();
        });
        watcher.start();
        try {
            Path file = temp.resolve("notes.txt");
            Files.writeString(file, "hello offline");
            assertTrue(latch.await(5, TimeUnit.SECONDS), "watcher should see new file");
            assertEquals("notes.txt", seen.get().getFileName().toString());
        } finally {
            watcher.stop();
        }
    }
}
