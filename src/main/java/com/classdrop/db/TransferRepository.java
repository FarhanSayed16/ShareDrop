package com.classdrop.db;

import com.classdrop.model.TransferRecord;

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

public class TransferRepository {
    private static final Logger LOGGER = Logger.getLogger(TransferRepository.class.getName());
    private final ConnectionFactory connectionFactory;

    public TransferRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public boolean insert(TransferRecord record) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "INSERT INTO transfers (id, session_id, device_id, item_kind, original_name, stored_name, size_bytes, content_type, direction, status, file_token, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getId());
            ps.setString(2, record.getSessionId());
            ps.setString(3, record.getDeviceId());
            ps.setString(4, record.getItemKind());
            ps.setString(5, record.getOriginalName());
            ps.setString(6, record.getStoredName());
            ps.setLong(7, record.getSizeBytes());
            ps.setString(8, record.getContentType());
            ps.setString(9, record.getDirection());
            ps.setString(10, record.getStatus());
            ps.setString(11, record.getFileToken());
            ps.setTimestamp(12, Timestamp.valueOf(record.getCreatedAt() != null ? record.getCreatedAt() : LocalDateTime.now()));
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert transfer: {0}", e.getMessage());
            return false;
        }
    }

    public boolean updateStatus(String transferId, String status) {
        if (!connectionFactory.isAvailable()) return false;
        String sql = "UPDATE transfers SET status = ? WHERE id = ?";
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, transferId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to update transfer status: {0}", e.getMessage());
            return false;
        }
    }

    public List<TransferRecord> findBySession(String sessionId) {
        if (!connectionFactory.isAvailable()) return Collections.emptyList();
        String sql = "SELECT id, session_id, device_id, item_kind, original_name, stored_name, size_bytes, content_type, direction, status, file_token, created_at " +
                     "FROM transfers WHERE session_id = ? ORDER BY created_at DESC";
        List<TransferRecord> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query transfers by session: {0}", e.getMessage());
        }
        return list;
    }

    public List<TransferRecord> search(String nameQuery, String itemKind, LocalDateTime fromDate, LocalDateTime toDate) {
        if (!connectionFactory.isAvailable()) return Collections.emptyList();

        StringBuilder sql = new StringBuilder(
            "SELECT id, session_id, device_id, item_kind, original_name, stored_name, size_bytes, content_type, direction, status, file_token, created_at " +
            "FROM transfers WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (nameQuery != null && !nameQuery.trim().isEmpty()) {
            sql.append(" AND original_name LIKE ?");
            params.add("%" + nameQuery.trim() + "%");
        }
        if (itemKind != null && !itemKind.trim().isEmpty() && !"ALL".equalsIgnoreCase(itemKind.trim())) {
            sql.append(" AND item_kind = ?");
            params.add(itemKind.trim().toUpperCase());
        }
        if (fromDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(Timestamp.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(Timestamp.valueOf(toDate));
        }
        sql.append(" ORDER BY created_at DESC LIMIT 100");

        List<TransferRecord> list = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Timestamp) {
                    ps.setTimestamp(i + 1, (Timestamp) p);
                } else if (p instanceof Long) {
                    ps.setLong(i + 1, (Long) p);
                } else {
                    ps.setString(i + 1, p.toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRecord(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to search transfers: {0}", e.getMessage());
        }
        return list;
    }

    private TransferRecord mapRecord(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return new TransferRecord(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("device_id"),
            rs.getString("item_kind"),
            rs.getString("original_name"),
            rs.getString("stored_name"),
            rs.getLong("size_bytes"),
            rs.getString("content_type"),
            rs.getString("direction"),
            rs.getString("status"),
            rs.getString("file_token"),
            ts != null ? ts.toLocalDateTime() : LocalDateTime.now()
        );
    }
}
