package com.classdrop.db;

import com.classdrop.util.Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionFactory {
    private static final Logger LOGGER = Logger.getLogger(ConnectionFactory.class.getName());

    private final String url;
    private final String user;
    private final String password;
    private volatile Boolean available = null;
    private volatile long lastChecked = 0L;

    public ConnectionFactory() {
        this(
            Config.getString("db.url", "jdbc:mysql://localhost:3306/classdrop"),
            Config.getString("db.user", "root"),
            Config.getString("db.password", "")
        );
    }

    public ConnectionFactory(String url, String user, String password) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.user = user != null ? user : "";
        this.password = password != null ? password : "";
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (available != null && (now - lastChecked < 15000)) {
            return available;
        }

        try {
            DriverManager.setLoginTimeout(2);
            try (Connection conn = getConnection()) {
                available = (conn != null && !conn.isClosed());
            }
        } catch (Exception e) {
            if (available == null || available) {
                // Log once when switching from available to unavailable
                LOGGER.log(Level.INFO, "[ClassDrop-DB] MySQL unavailable at {0} (running in-memory fallback)", sanitizeUrl(url));
            }
            available = false;
        }
        lastChecked = now;
        return available;
    }

    public void initSchema() {
        try (InputStream is = getClass().getResourceAsStream("/schema.sql")) {
            if (is == null) return;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                 Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--") || line.startsWith("/*")) continue;
                    // Skip database creation/switching statements if target already handles it
                    if (line.toUpperCase().startsWith("CREATE DATABASE") || line.toUpperCase().startsWith("USE ")) {
                        continue;
                    }
                    sb.append(line).append(" ");
                    if (line.endsWith(";")) {
                        String sql = sb.toString().replace(";", "").trim();
                        if (!sql.isEmpty()) {
                            try {
                                stmt.execute(sql);
                            } catch (SQLException ignored) {
                                // Tables may already exist or constraints already applied
                            }
                        }
                        sb.setLength(0);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Schema auto-initialization skipped or partial: {0}", e.getMessage());
        }
    }

    public String getUrl() {
        return url;
    }

    public static String sanitizeUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)(password=)[^&;]*", "$1*****");
    }
}
