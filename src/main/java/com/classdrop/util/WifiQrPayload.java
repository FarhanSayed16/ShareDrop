package com.classdrop.util;

/**
 * Builds WIFI: QR payloads (WPA/WEP/nopass) for phone cameras to join a hotspot.
 * Spec: WIFI:T:&lt;auth&gt;;S:&lt;ssid&gt;;P:&lt;password&gt;;H:&lt;hidden&gt;;
 */
public final class WifiQrPayload {
    private WifiQrPayload() {}

    public static String wpa(String ssid, String password) {
        return build("WPA", ssid, password, false);
    }

    public static String build(String authType, String ssid, String password, boolean hidden) {
        if (ssid == null || ssid.isBlank()) return null;
        String t = (authType == null || authType.isBlank()) ? "WPA" : authType.trim().toUpperCase();
        StringBuilder sb = new StringBuilder("WIFI:");
        sb.append("T:").append(escape(t)).append(';');
        sb.append("S:").append(escape(ssid.trim())).append(';');
        if (!"NOPASS".equals(t) && password != null && !password.isEmpty()) {
            sb.append("P:").append(escape(password)).append(';');
        }
        if (hidden) {
            sb.append("H:true;");
        }
        sb.append(';');
        return sb.toString();
    }

    /** Escape special characters per WIFI QR conventions. */
    static String escape(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"");
    }
}
