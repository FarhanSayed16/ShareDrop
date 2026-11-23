package com.classdrop.db;

import com.classdrop.model.ClipboardEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnippetRepository {
    private static final Logger LOGGER = Logger.getLogger(SnippetRepository.class.getName());
    private final ConnectionFactory connectionFactory;

    public SnippetRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public boolean insert(ClipboardEntry entry, String sessionId, String deviceId) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "INSERT INTO snippets (id, session_id, device_id, title, language, body, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.getId());
            ps.setString(2, sessionId);
            ps.setString(3, deviceId != null ? deviceId : entry.getDeviceId());
            ps.setString(4, entry.getTitle());
            ps.setString(5, entry.getLanguage());
            ps.setString(6, entry.getBody());
            ps.setTimestamp(7, Timestamp.valueOf(entry.getCreatedAt() != null ? entry.getCreatedAt() : LocalDateTime.now()));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert snippet: {0}", e.getMessage());
            return false;
        }
    }

    public List<ClipboardEntry> findBySession(String sessionId) {
        if (!connectionFactory.isAvailable()) return Collections.emptyList();
        String sql = "SELECT s.id, s.session_id, s.device_id, COALESCE(d.display_name, 'Host/Guest') AS device_name, " +
                     "s.title, s.language, s.body, s.created_at " +
                     "FROM snippets s LEFT JOIN devices d ON s.device_id = d.id " +
                     "WHERE s.session_id = ? ORDER BY s.created_at ASC";
        List<ClipboardEntry> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    list.add(new ClipboardEntry(
                        rs.getString("id"),
                        rs.getString("session_id"),
                        rs.getString("device_id"),
                        rs.getString("device_name"),
                        rs.getString("title"),
                        rs.getString("language"),
                        rs.getString("body"),
                        ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query snippets by session: {0}", e.getMessage());
        }
        return list;
    }
}
