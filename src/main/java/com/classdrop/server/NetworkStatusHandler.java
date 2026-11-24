package com.classdrop.server;

import com.classdrop.model.RoomSession;
import com.classdrop.service.NetworkInfoService;
import com.classdrop.service.RoomService;
import com.classdrop.util.JsonUtil;
import com.classdrop.util.ShareUrlBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/** Public diagnostics for offline join troubleshooting. */
public final class NetworkStatusHandler implements HttpHandler {
    private final RoomService roomService;
    private final NetworkInfoService networkInfo;
    private final int port;

    public NetworkStatusHandler(RoomService roomService, NetworkInfoService networkInfo, int port) {
        this.roomService = roomService;
        this.networkInfo = networkInfo;
        this.port = port;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HandlerSupport.sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Only GET allowed");
            return;
        }

        boolean hotspot = networkInfo.isHotspotAvailable();
        List<String> ips = networkInfo.getIPv4Addresses();
        String preferred = networkInfo.getPreferredIP();
        RoomSession session = roomService.getCurrentSessionOrNull();
        boolean active = session != null && session.isActive();
        String roomCode = active ? session.getRoomCode() : null;
        String joinUrl = active
            ? ShareUrlBuilder.localRoomUrl(
                session.isInternetShare() ? preferred : session.getHostIp(),
                port,
                roomCode)
            : ShareUrlBuilder.localBaseUrl(preferred, port) + "/";

        String ipJson = ips.stream().map(JsonUtil::quote).collect(Collectors.joining(","));
        String json = "{"
            + "\"ok\":true,"
            + "\"hotspotAvailable\":" + hotspot + ","
            + "\"preferredIp\":" + JsonUtil.quote(preferred) + ","
            + "\"hotspotIp\":" + JsonUtil.quote(
                networkInfo.getHotspotIpOrNull() != null ? networkInfo.getHotspotIpOrNull() : NetworkInfoService.WINDOWS_HOTSPOT_IP) + ","
            + "\"port\":" + port + ","
            + "\"addresses\":[" + ipJson + "],"
            + "\"sessionActive\":" + active + ","
            + "\"roomCode\":" + (roomCode != null ? JsonUtil.quote(roomCode) : "null") + ","
            + "\"mode\":" + JsonUtil.quote(active ? ShareUrlBuilder.displayModeName(session.getMode()) : "NONE") + ","
            + "\"internetShare\":" + (active && session.isInternetShare()) + ","
            + "\"joinUrl\":" + JsonUtil.quote(joinUrl) + ","
            + "\"mdnsHint\":" + JsonUtil.quote("http://classdrop.local:" + port) + ","
            + "\"offlineTip\":" + JsonUtil.quote(
                "Join the host hotspot Wi-Fi, turn off mobile data, then open the join URL. No internet is required.")
            + "}";
        HandlerSupport.sendJson(exchange, 200, json);
    }
}
