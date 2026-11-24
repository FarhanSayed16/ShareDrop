package com.classdrop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PosterExportServiceTest {

    @TempDir
    Path temp;

    @Test
    void exportPng_writesFile() throws Exception {
        PosterExportService svc = new PosterExportService(new QrService());
        Path out = temp.resolve("poster.png");
        svc.exportPng(out, "482917", "http://192.168.137.1:8080/r/482917", "ClassDrop-Hub", "Tip");
        assertTrue(Files.exists(out));
        assertTrue(Files.size(out) > 1000);
    }
}
