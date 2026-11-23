package com.classdrop.db;

import com.classdrop.model.Device;

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

public class DeviceRepository {
    private static final Logger LOGGER = Logger.getLogger(DeviceRepository.class.getName());
    private final ConnectionFactory connectionFactory;

    public DeviceRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public boolean insert(Device device, String sessionId) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "INSERT INTO devices (id, session_id, display_name, ip_address, joined_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, device.getId());
            ps.setString(2, sessionId);
            ps.setString(3, device.getDisplayName());
            ps.setString(4, device.getIpAddress());
            ps.setTimestamp(5, Timestamp.valueOf(device.getJoinedAt()));
            ps.setTimestamp(6, Timestamp.valueOf(device.getLastSeen()));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert device: {0}", e.getMessage());
            return false;
        }
    }

    public boolean updateLastSeen(String deviceId, LocalDateTime lastSeen) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "UPDATE devices SET last_seen = ? WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(lastSeen != null ? lastSeen : LocalDateTime.now()));
            ps.setString(2, deviceId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update device last seen: {0}", e.getMessage());
            return false;
        }
    }

    public List<Device> findBySession(String sessionId) {
        if (!connectionFactory.isAvailable()) return Collections.emptyList();
        String sql = "SELECT id, display_name, ip_address, joined_at, last_seen FROM devices WHERE session_id = ? ORDER BY joined_at ASC";
        List<Device> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("display_name");
                    String ip = rs.getString("ip_address");
                    Timestamp joinedTs = rs.getTimestamp("joined_at");
                    Timestamp seenTs = rs.getTimestamp("last_seen");

                    LocalDateTime joined = joinedTs != null ? joinedTs.toLocalDateTime() : LocalDateTime.now();
                    LocalDateTime seen = seenTs != null ? seenTs.toLocalDateTime() : LocalDateTime.now();
                    list.add(new Device(id, name, ip, "TOKEN_PERSISTED", joined, seen));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query devices by session: {0}", e.getMessage());
        }
        return list;
    }
}
