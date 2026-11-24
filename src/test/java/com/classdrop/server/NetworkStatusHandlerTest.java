package com.classdrop.server;

import com.classdrop.model.NetworkMode;
import com.classdrop.service.NetworkInfoService;
import com.classdrop.service.RoomService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class NetworkStatusHandlerTest {
    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        RoomService rooms = new RoomService();
        rooms.createSession(NetworkMode.HOTSPOT, "192.168.137.1");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/network-status", new NetworkStatusHandler(rooms, new NetworkInfoService(), port));
        server.createContext("/generate_204", new CaptivePortalHandler());
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void networkStatus_returnsJoinHints() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/network-status").openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        assertEquals(200, conn.getResponseCode());
        String body;
        try (InputStream in = conn.getInputStream()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(body.contains("\"ok\":true"));
        assertTrue(body.contains("joinUrl"));
        assertTrue(body.contains("offlineTip"));
        assertTrue(body.contains("192.168.137.1"));
    }

    @Test
    void generate204_returnsNoContent() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/generate_204").openConnection();
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        assertEquals(204, conn.getResponseCode());
    }
}
