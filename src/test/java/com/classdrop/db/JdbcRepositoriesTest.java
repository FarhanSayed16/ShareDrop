package com.classdrop.db;

import com.classdrop.model.ClipboardEntry;
import com.classdrop.model.Device;
import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;
import com.classdrop.model.TransferRecord;
import com.classdrop.service.StatsService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcRepositoriesTest {

    private static ConnectionFactory connectionFactory;
    private static SessionRepository sessionRepo;
    private static DeviceRepository deviceRepo;
    private static TransferRepository transferRepo;
    private static SnippetRepository snippetRepo;
    private static StatsService statsService;

    @BeforeAll
    public static void setUp() {
        // In-memory H2 in MySQL compatibility mode
        String testUrl = "jdbc:h2:mem:classdrop_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        connectionFactory = new ConnectionFactory(testUrl, "sa", "");
        assertTrue(connectionFactory.isAvailable(), "In-memory test database should be available");

        // Initialize schema
        connectionFactory.initSchema();

        sessionRepo = new SessionRepository(connectionFactory);
        deviceRepo = new DeviceRepository(connectionFactory);
        transferRepo = new TransferRepository(connectionFactory);
        snippetRepo = new SnippetRepository(connectionFactory);
        statsService = new StatsService(connectionFactory);
    }

    @Test
    public void testSessionRepositoryCrud() {
        RoomSession session = new RoomSession("998877", NetworkMode.HOTSPOT, "192.168.137.1");
        boolean inserted = sessionRepo.insert(session);
        assertTrue(inserted, "Session insert should succeed");

        List<RoomSession> recent = sessionRepo.findRecent(10);
        assertFalse(recent.isEmpty(), "Recent sessions should not be empty");
        assertTrue(recent.stream().anyMatch(s -> s.getId().equals(session.getId())), "Inserted session should be found");

        LocalDateTime ended = LocalDateTime.now();
        boolean updated = sessionRepo.updateEndTime(session.getId(), ended);
        assertTrue(updated, "Update end time should succeed");
    }

    @Test
    public void testDeviceRepositoryCrud() {
        RoomSession session = new RoomSession("112233", NetworkMode.LAN, "192.168.1.100");
        sessionRepo.insert(session);

        Device dev1 = new Device("Alice iPhone", "192.168.1.105");
        Device dev2 = new Device("Bob Laptop", "192.168.1.106");

        assertTrue(deviceRepo.insert(dev1, session.getId()), "Device 1 insert should succeed");
        assertTrue(deviceRepo.insert(dev2, session.getId()), "Device 2 insert should succeed");

        List<Device> devices = deviceRepo.findBySession(session.getId());
        assertEquals(2, devices.size(), "Session should have 2 devices");

        boolean touched = deviceRepo.updateLastSeen(dev1.getId(), LocalDateTime.now());
        assertTrue(touched, "Device last seen update should succeed");
    }

    @Test
    public void testTransferRepositoryAndSearch() {
        RoomSession session = new RoomSession("445566", NetworkMode.HOTSPOT, "192.168.137.1");
        sessionRepo.insert(session);

        Device dev = new Device("Charlie Phone", "192.168.137.5");
        deviceRepo.insert(dev, session.getId());

        TransferRecord tr1 = new TransferRecord(
            "tr-001", session.getId(), dev.getId(), "IMAGE", "assignment_diagram.png",
            "stored_001.png", 2048500L, "image/png", "UPLOAD", "OK", null, LocalDateTime.now().minusHours(1)
        );
        TransferRecord tr2 = new TransferRecord(
            "tr-002", session.getId(), dev.getId(), "DOCUMENT", "LectureNotes_MCA.pdf",
            "stored_002.pdf", 5120000L, "application/pdf", "UPLOAD", "OK", null, LocalDateTime.now()
        );

        assertTrue(transferRepo.insert(tr1));
        assertTrue(transferRepo.insert(tr2));

        // Test search by name LIKE
        List<TransferRecord> foundNotes = transferRepo.search("Lecture", null, null, null);
        assertEquals(1, foundNotes.size());
        assertEquals("LectureNotes_MCA.pdf", foundNotes.get(0).getOriginalName());

        // Test search by itemKind
        List<TransferRecord> foundImages = transferRepo.search(null, "IMAGE", null, null);
        assertTrue(foundImages.stream().anyMatch(t -> t.getId().equals("tr-001")));

        // Test search by date range
        List<TransferRecord> foundRecent = transferRepo.search(null, null, LocalDateTime.now().minusMinutes(30), LocalDateTime.now().plusMinutes(5));
        assertEquals(1, foundRecent.size());
        assertEquals("tr-002", foundRecent.get(0).getId());
    }

    @Test
    public void testSnippetRepositoryCrud() {
        RoomSession session = new RoomSession("778899", NetworkMode.HOTSPOT, "192.168.137.1");
        sessionRepo.insert(session);

        Device dev = new Device("Farhan Host", "192.168.137.1");
        deviceRepo.insert(dev, session.getId());

        ClipboardEntry entry = new ClipboardEntry(
            session.getId(), dev.getId(), dev.getDisplayName(), "Quick Sort Java", "java", "public static void sort(int[] a) {}"
        );

        assertTrue(snippetRepo.insert(entry, session.getId(), dev.getId()));

        List<ClipboardEntry> snippets = snippetRepo.findBySession(session.getId());
        assertEquals(1, snippets.size());
        assertEquals("Quick Sort Java", snippets.get(0).getTitle());
        assertEquals("java", snippets.get(0).getLanguage());
        assertEquals("public static void sort(int[] a) {}", snippets.get(0).getBody());
    }

    @Test
    public void testStatsServiceAggregation() {
        RoomSession session = new RoomSession("667788", NetworkMode.HOTSPOT, "192.168.137.1");
        sessionRepo.insert(session);

        Device dev = new Device("Student Tablet", "192.168.137.8");
        deviceRepo.insert(dev, session.getId());

        TransferRecord tr = new TransferRecord(
            "tr-stat-1", session.getId(), dev.getId(), "DOCUMENT", "Syllabus.pdf",
            "stored_syl.pdf", 1000000L, "application/pdf", "UPLOAD", "OK", null, LocalDateTime.now()
        );
        transferRepo.insert(tr);

        StatsService.SessionStats sessionStats = statsService.getSessionStats(session.getId());
        assertEquals(session.getId(), sessionStats.sessionId());
        assertEquals(1, sessionStats.deviceCount());
        assertEquals(1, sessionStats.transferCount());
        assertEquals(1000000L, sessionStats.totalBytes());
        assertEquals(1, sessionStats.uploadCount());
        assertEquals(0, sessionStats.downloadCount());

        StatsService.AllTimeStats allTimeStats = statsService.getAllTimeStats();
        assertTrue(allTimeStats.totalSessions() >= 1);
        assertTrue(allTimeStats.totalDevices() >= 1);
        assertTrue(allTimeStats.totalTransfers() >= 1);
        assertTrue(allTimeStats.totalBytes() >= 1000000L);
    }

    @Test
    public void testGracefulDegradationWhenDbDown() {
        // Point connection factory to an unreachable port/host
        ConnectionFactory badFactory = new ConnectionFactory("jdbc:mysql://localhost:9999/nonexistent", "root", "wrong");
        assertFalse(badFactory.isAvailable());

        SessionRepository badSessionRepo = new SessionRepository(badFactory);
        DeviceRepository badDeviceRepo = new DeviceRepository(badFactory);
        TransferRepository badTransferRepo = new TransferRepository(badFactory);
        SnippetRepository badSnippetRepo = new SnippetRepository(badFactory);
        StatsService badStats = new StatsService(badFactory);

        // Repositories should gracefully return false / empty lists rather than crashing with exceptions
        RoomSession dummySession = new RoomSession("000000", NetworkMode.HOTSPOT, "127.0.0.1");
        assertDoesNotThrow(() -> assertFalse(badSessionRepo.insert(dummySession)));
        assertDoesNotThrow(() -> assertTrue(badSessionRepo.findRecent(5).isEmpty()));
        assertDoesNotThrow(() -> assertFalse(badDeviceRepo.insert(new Device("X", "127.0.0.1"), "s1")));
        assertDoesNotThrow(() -> assertTrue(badTransferRepo.search("test", null, null, null).isEmpty()));
        assertDoesNotThrow(() -> assertTrue(badSnippetRepo.findBySession("s1").isEmpty()));

        StatsService.SessionStats stats = badStats.getSessionStats("s1");
        assertEquals(0, stats.transferCount());
    }

    @Test
    public void testUrlSanitization() {
        assertEquals("jdbc:mysql://localhost:3306/db?user=root&password=*****", 
            ConnectionFactory.sanitizeUrl("jdbc:mysql://localhost:3306/db?user=root&password=supersecretpassword"));
    }
}
