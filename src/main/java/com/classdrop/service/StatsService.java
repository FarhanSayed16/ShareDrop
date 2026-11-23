package com.classdrop.service;

import com.classdrop.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsService {
    private static final Logger LOGGER = Logger.getLogger(StatsService.class.getName());
    private final ConnectionFactory connectionFactory;

    public record SessionStats(
        String sessionId,
        long deviceCount,
        long transferCount,
        long totalBytes,
        long uploadCount,
        long downloadCount
    ) {}

    public record AllTimeStats(
        long totalSessions,
        long totalDevices,
        long totalTransfers,
        long totalBytes
    ) {}

    public StatsService(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public SessionStats getSessionStats(String sessionId) {
        if (sessionId == null || !connectionFactory.isAvailable()) {
            return new SessionStats(sessionId, 0, 0, 0, 0, 0);
        }

        long deviceCount = 0;
        long transferCount = 0;
        long totalBytes = 0;
        long uploads = 0;
        long downloads = 0;

        try (Connection conn = connectionFactory.getConnection()) {
            String devSql = "SELECT COUNT(*) FROM devices WHERE session_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(devSql)) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) deviceCount = rs.getLong(1);
                }
            }

            String trSql = "SELECT COUNT(*), COALESCE(SUM(size_bytes), 0), " +
                           "COALESCE(SUM(CASE WHEN direction = 'UPLOAD' THEN 1 ELSE 0 END), 0), " +
                           "COALESCE(SUM(CASE WHEN direction = 'DOWNLOAD' THEN 1 ELSE 0 END), 0) " +
                           "FROM transfers WHERE session_id = ? AND status = 'OK'";
            try (PreparedStatement ps = conn.prepareStatement(trSql)) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        transferCount = rs.getLong(1);
                        totalBytes = rs.getLong(2);
                        uploads = rs.getLong(3);
                        downloads = rs.getLong(4);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to compute session stats: {0}", e.getMessage());
        }

        return new SessionStats(sessionId, deviceCount, transferCount, totalBytes, uploads, downloads);
    }

    public AllTimeStats getAllTimeStats() {
        if (!connectionFactory.isAvailable()) {
            return new AllTimeStats(0, 0, 0, 0);
        }

        long sessions = 0;
        long devices = 0;
        long transfers = 0;
        long totalBytes = 0;

        try (Connection conn = connectionFactory.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM sessions");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) sessions = rs.getLong(1);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM devices");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) devices = rs.getLong(1);
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*), COALESCE(SUM(size_bytes), 0) FROM transfers WHERE status = 'OK'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    transfers = rs.getLong(1);
                    totalBytes = rs.getLong(2);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to compute all-time stats: {0}", e.getMessage());
        }

        return new AllTimeStats(sessions, devices, transfers, totalBytes);
    }
}
