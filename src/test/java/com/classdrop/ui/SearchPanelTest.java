package com.classdrop.ui;

import com.classdrop.db.ConnectionFactory;
import com.classdrop.db.DeviceRepository;
import com.classdrop.db.SessionRepository;
import com.classdrop.db.TransferRepository;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.model.TransferRecord;
import com.classdrop.service.StatsService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class SearchPanelTest {

    private static TransferRepository transferRepo;
    private static StatsService statsService;

    @BeforeAll
    public static void setUp() {
        String testUrl = "jdbc:h2:mem:search_panel_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        ConnectionFactory cf = new ConnectionFactory(testUrl, "sa", "");
        cf.initSchema();

        SessionRepository sessionRepo = new SessionRepository(cf);
        DeviceRepository devRepo = new DeviceRepository(cf);
        transferRepo = new TransferRepository(cf);
        statsService = new StatsService(cf);

        RoomSession session = new RoomSession("998811", NetworkMode.HOTSPOT, "192.168.137.1");
        sessionRepo.insert(session);

        Device dev = new Device("Student 1", "192.168.137.5");
        devRepo.insert(dev, session.getId());

        transferRepo.insert(new TransferRecord(
            "tr-search-1", session.getId(), dev.getId(), "DOCUMENT", "Java_Algorithms.pdf",
            "stored_algo.pdf", 3000000L, "application/pdf", "UPLOAD", "OK", null, LocalDateTime.now()
        ));
    }

    @Test
    public void testSearchPanelWithNullRepo() {
        assertDoesNotThrow(() -> {
            SearchPanel panel = new SearchPanel(null, null);
            assertNotNull(panel);
            assertEquals(0, panel.getTableModel().getRowCount());
        });
    }

    @Test
    public void testSearchPanelPopulatesResults() {
        SearchPanel panel = new SearchPanel(transferRepo, statsService);
        assertNotNull(panel);
        assertTrue(panel.getTableModel().getRowCount() >= 1, "Should find at least 1 transfer in search results");
        assertEquals("Java_Algorithms.pdf", panel.getTableModel().getValueAt(0, 1));
        assertEquals("DOCUMENT", panel.getTableModel().getValueAt(0, 2));
    }
}
