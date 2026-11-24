package com.classdrop.service;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Advertises ClassDrop on the local LAN/hotspot as <code>classdrop.local</code> (mDNS).
 * Fully offline — no public internet required.
 */
public final class MdnsService {
    private static final Logger LOGGER = Logger.getLogger(MdnsService.class.getName());
    public static final String SERVICE_TYPE = "_http._tcp.local.";
    public static final String SERVICE_NAME = "ClassDrop";

    private volatile JmDNS jmdns;
    private volatile int advertisedPort = -1;

    public synchronized void start(int port, String preferredIp) {
        stop();
        try {
            InetAddress addr = resolveBindAddress(preferredIp);
            jmdns = JmDNS.create(addr);
            ServiceInfo info = ServiceInfo.create(SERVICE_TYPE, SERVICE_NAME, port,
                "ClassDrop offline classroom hub");
            jmdns.registerService(info);
            advertisedPort = port;
            LOGGER.info("[ClassDrop-mDNS] Advertising " + SERVICE_NAME + " on port " + port
                + " via " + addr.getHostAddress() + " (try http://classdrop.local:" + port + ")");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[ClassDrop-mDNS] Could not start: {0}", e.getMessage());
            stop();
        }
    }

    public synchronized void stop() {
        JmDNS local = jmdns;
        jmdns = null;
        advertisedPort = -1;
        if (local != null) {
            try {
                local.unregisterAllServices();
                local.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isRunning() {
        return jmdns != null;
    }

    public String getHintUrl() {
        if (advertisedPort <= 0) return null;
        return "http://classdrop.local:" + advertisedPort;
    }

    private static InetAddress resolveBindAddress(String preferredIp) throws IOException {
        if (preferredIp != null && !preferredIp.isBlank() && !"127.0.0.1".equals(preferredIp)) {
            try {
                return InetAddress.getByName(preferredIp.trim());
            } catch (Exception ignored) {
            }
        }
        return InetAddress.getLocalHost();
    }
}
