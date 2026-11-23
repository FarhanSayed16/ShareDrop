package com.classdrop.util;

import com.classdrop.model.NetworkMode;
import com.classdrop.model.RoomSession;

/**
 * Builds student-facing share URLs for local LAN/hotspot or public Internet Share.
 */
public final class ShareUrlBuilder {
    private ShareUrlBuilder() {}

    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return null;
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    public static String roomUrl(String baseUrl, String roomCode) {
        String base = normalizeBaseUrl(baseUrl);
        if (base == null || roomCode == null || roomCode.isBlank()) return null;
        return base + "/r/" + roomCode.trim();
    }

    public static String localBaseUrl(String hostIp, int port) {
        String ip = (hostIp == null || hostIp.isBlank()) ? "127.0.0.1" : hostIp.trim();
        return "http://" + ip + ":" + port;
    }

    public static String localRoomUrl(String hostIp, int port, String roomCode) {
        return roomUrl(localBaseUrl(hostIp, port), roomCode);
    }

    /**
     * Prefer public/internet base when session is RELAY (Internet Share).
     * RELAY without a public base returns null — never advertise a LAN IP as "internet".
     */
    public static String shareUrlForSession(RoomSession session, int port, String publicBaseUrl) {
        if (session == null) return null;
        if (session.getMode() == NetworkMode.RELAY) {
            return roomUrl(publicBaseUrl, session.getRoomCode());
        }
        return localRoomUrl(session.getHostIp(), port, session.getRoomCode());
    }

    public static String displayModeName(NetworkMode mode) {
        if (mode == NetworkMode.RELAY) return "INTERNET";
        return mode != null ? mode.name() : "LAN";
    }
}
