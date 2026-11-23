package com.classdrop.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class Config {
    private static final Properties PROPS = new Properties();

    static {
        // 1. Try loading from working directory if user provided custom override
        Path externalConfig = Paths.get("application.properties");
        if (Files.exists(externalConfig)) {
            try (InputStream is = Files.newInputStream(externalConfig)) {
                PROPS.load(is);
            } catch (Exception ignored) {}
        }

        // 2. Load defaults from classpath
        try (InputStream is = Config.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                Properties defaults = new Properties();
                defaults.load(is);
                for (String key : defaults.stringPropertyNames()) {
                    PROPS.putIfAbsent(key, defaults.getProperty(key));
                }
            }
        } catch (Exception ignored) {}
    }

    private Config() {}

    public static int getInt(String key, int defaultValue) {
        String val = PROPS.getProperty(key);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static long getLong(String key, long defaultValue) {
        String val = PROPS.getProperty(key);
        if (val != null) {
            try {
                return Long.parseLong(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static String getString(String key, String defaultValue) {
        String val = PROPS.getProperty(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    public static int getPort() {
        return getInt("server.port", Constants.PORT);
    }

    public static long getMaxFileSizeBytes() {
        return getLong("max.file.size.bytes", Constants.MAX_FILE_SIZE);
    }
}
