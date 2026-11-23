package com.classdrop.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON helpers for ClassDrop request bodies (no external JSON library).
 * Supports flat string/number/boolean objects used by the web client.
 */
public final class JsonUtil {
    private static final Pattern STRING_FIELD =
        Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern BARE_FIELD =
        Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,}\\]]+)");

    private JsonUtil() {}

    public static Map<String, String> parseObject(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }

        Matcher stringMatcher = STRING_FIELD.matcher(json);
        while (stringMatcher.find()) {
            out.put(stringMatcher.group(1), unescape(stringMatcher.group(2).trim()));
        }

        Matcher bareMatcher = BARE_FIELD.matcher(json);
        while (bareMatcher.find()) {
            String key = bareMatcher.group(1);
            if (out.containsKey(key)) {
                continue;
            }
            String raw = bareMatcher.group(2).trim();
            if (raw.startsWith("\"")) {
                continue;
            }
            if (raw.endsWith(",")) {
                raw = raw.substring(0, raw.length() - 1).trim();
            }
            out.put(key, raw);
        }
        return out;
    }

    public static String getString(Map<String, String> map, String key) {
        if (map == null) return null;
        String val = map.get(key);
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    public static String quote(String value) {
        if (value == null) return "null";
        String escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    public static String escape(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char n = value.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
