package com.classdrop.db;

import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;

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

public class SessionRepository {
    private static final Logger LOGGER = Logger.getLogger(SessionRepository.class.getName());
    private final ConnectionFactory connectionFactory;

    public SessionRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public boolean insert(RoomSession session) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "INSERT INTO sessions (id, room_code, started_at, ended_at, mode, host_ip) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.getId());
            ps.setString(2, session.getRoomCode());
            ps.setTimestamp(3, Timestamp.valueOf(session.getStartedAt()));
            ps.setTimestamp(4, session.getEndedAt() != null ? Timestamp.valueOf(session.getEndedAt()) : null);
            ps.setString(5, session.getMode().name());
            ps.setString(6, session.getHostIp());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert session: {0}", e.getMessage());
            return false;
        }
    }

    public boolean updateEndTime(String sessionId, LocalDateTime endedAt) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "UPDATE sessions SET ended_at = ? WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, endedAt != null ? Timestamp.valueOf(endedAt) : Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, sessionId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update session end time: {0}", e.getMessage());
            return false;
        }
    }

    public List<RoomSession> findRecent(int limit) {
        if (!connectionFactory.isAvailable()) return Collections.emptyList();
        String sql = "SELECT id, room_code, started_at, ended_at, mode, host_ip FROM sessions ORDER BY started_at DESC LIMIT ?";
        List<RoomSession> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String code = rs.getString("room_code");
                    Timestamp startTs = rs.getTimestamp("started_at");
                    Timestamp endTs = rs.getTimestamp("ended_at");
                    String modeStr = rs.getString("mode");
                    String hostIp = rs.getString("host_ip");

                    LocalDateTime startedAt = startTs != null ? startTs.toLocalDateTime() : LocalDateTime.now();
                    LocalDateTime endedAt = endTs != null ? endTs.toLocalDateTime() : null;
                    NetworkMode mode = NetworkMode.valueOf(modeStr);

                    list.add(new RoomSession(id, code, mode, hostIp, startedAt, endedAt, endedAt == null));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query recent sessions: {0}", e.getMessage());
        }
        return list;
    }
}
