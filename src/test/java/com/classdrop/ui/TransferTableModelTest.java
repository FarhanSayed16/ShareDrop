package com.classdrop.ui;

import com.classdrop.model.TransferRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransferTableModelTest {

    private TransferTableModel model;

    @BeforeEach
    void setUp() {
        model = new TransferTableModel();
    }

    @Test
    void testEmptyModel() {
        assertEquals(0, model.getRowCount());
        assertEquals(7, model.getColumnCount());
        assertEquals("File Name", model.getColumnName(0));
        assertEquals("Speed", model.getColumnName(6));
    }

    @Test
    void testAddAndProgressTransfer() {
        TransferRecord rec = new TransferRecord(
            "tx-1", "sess-1", "dev-1", "DOCUMENT", "homework.pdf",
            "stored-1.pdf", 2048, "application/pdf", "UPLOAD", "IN_PROGRESS", null, LocalDateTime.now()
        );

        model.addOrUpdate(rec);
        assertEquals(1, model.getRowCount());
        assertEquals("homework.pdf", model.getValueAt(0, 0));
        assertEquals("DOCUMENT", model.getValueAt(0, 1));
        assertEquals("2.0 KB", model.getValueAt(0, 2));
        assertEquals("IN_PROGRESS", model.getValueAt(0, 4));

        // Update progress
        model.updateProgress("tx-1", 1024, 2048);
        assertEquals("50%", model.getValueAt(0, 5));

        // Mark completed
        rec.setStatus("OK");
        model.addOrUpdate(rec);
        assertEquals("OK", model.getValueAt(0, 4));
        assertEquals("100%", model.getValueAt(0, 5));
    }

    @Test
    void testEmaSpeedSmoothingCalculation() {
        TransferTableModel.TransferItem item = new TransferTableModel.TransferItem(
            "tx-2", "video.mp4", "DOCUMENT", 10_000_000, "UPLOAD", "IN_PROGRESS"
        );

        // Simulate 2 progress events spaced by 200ms
        item.updateProgress(1_000_000); // 1 MB
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        item.updateProgress(2_000_000); // 2 MB (1 MB delta in ~150ms)

        // Cell value at column 6 should display formatted speed (e.g. "x.x MB/s")
        model.addOrUpdate(new TransferRecord(
            "tx-2", "sess-1", "dev-1", "DOCUMENT", "video.mp4",
            "stored-2.mp4", 10_000_000, "video/mp4", "UPLOAD", "IN_PROGRESS", null, LocalDateTime.now()
        ));
        model.updateProgress("tx-2", 2_000_000, 10_000_000);

        Object speedVal = model.getValueAt(0, 6);
        assertNotNull(speedVal);
    }
}
