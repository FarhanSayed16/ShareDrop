package com.classdrop.service;

import com.classdrop.model.NetworkMode;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public final class NetworkInfoService {

    public static final String WINDOWS_HOTSPOT_IP = "192.168.137.1";

    public List<String> getIPv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return addresses;

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }

                String name = iface.getName().toLowerCase(Locale.ROOT);
                String displayName = iface.getDisplayName() != null
                    ? iface.getDisplayName().toLowerCase(Locale.ROOT) : "";

                if (isVirtualAdapter(name) || isVirtualAdapter(displayName)) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = iface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress addr = inetAddresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        addresses.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error enumerating network interfaces: " + e.getMessage());
        }
        return addresses;
    }

    /** Human-readable adapter lines for host diagnostics panel. */
    public List<String> getInterfaceSummaries() {
        List<String> lines = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return lines;
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                String display = iface.getDisplayName() != null ? iface.getDisplayName() : iface.getName();
                String lower = display.toLowerCase(Locale.ROOT);
                if (isVirtualAdapter(lower) && !looksLikeHotspotAdapter(lower)) continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String tag = looksLikeHotspotAdapter(lower) || WINDOWS_HOTSPOT_IP.equals(addr.getHostAddress())
                            ? " [HOTSPOT]" : "";
                        lines.add(addr.getHostAddress() + " — " + display + tag);
                    }
                }
            }
        } catch (SocketException e) {
            lines.add("(could not list interfaces: " + e.getMessage() + ")");
        }
        return lines;
    }

    public String getPreferredIP() {
        List<String> addrs = getIPv4Addresses();
        if (addrs.isEmpty()) {
            return "127.0.0.1";
        }
        String hotspot = findHotspotIp(addrs);
        if (hotspot != null) return hotspot;
        return addrs.get(0);
    }

    public NetworkMode detectMode(String chosenIp) {
        if (isHotspotIp(chosenIp)) {
            return NetworkMode.HOTSPOT;
        }
        return NetworkMode.LAN;
    }

    public boolean isHotspotAvailable() {
        return findHotspotIp(getIPv4Addresses()) != null || hasHotspotNamedInterface();
    }

    public String getHotspotIpOrNull() {
        return findHotspotIp(getIPv4Addresses());
    }

    public String resolveHostIp(NetworkMode mode) {
        if (mode == NetworkMode.HOTSPOT) {
            String hs = getHotspotIpOrNull();
            return hs != null ? hs : getPreferredIP();
        }
        return getPreferredIP();
    }

    public static boolean isHotspotIp(String ip) {
        if (ip == null) return false;
        return WINDOWS_HOTSPOT_IP.equals(ip.trim()) || ip.trim().startsWith("192.168.137.");
    }

    /**
     * True when this PC looks like a <em>client</em> on a phone hotspot
     * (Android 192.168.43.x / iPhone 172.20.10.x) — reverse offline mode.
     */
    public boolean isOnPhoneHotspot() {
        if (isHotspotAvailable()) return false;
        for (String ip : getIPv4Addresses()) {
            if (isPhoneHotspotClientIp(ip)) return true;
        }
        return false;
    }

    public static boolean isPhoneHotspotClientIp(String ip) {
        if (ip == null) return false;
        String t = ip.trim();
        return t.startsWith("192.168.43.")   // common Android hotspot
            || t.startsWith("172.20.10.");   // iPhone personal hotspot
    }

    public String describeOfflineRole() {
        if (isHotspotAvailable()) return "HOST_HOTSPOT";
        if (isOnPhoneHotspot()) return "CLIENT_ON_PHONE_HOTSPOT";
        return "LAN_OR_UNKNOWN";
    }

    private String findHotspotIp(List<String> addrs) {
        for (String ip : addrs) {
            if (WINDOWS_HOTSPOT_IP.equals(ip)) return ip;
        }
        // Some builds may expose another 192.168.137.x on the local machine
        for (String ip : addrs) {
            if (ip != null && ip.startsWith("192.168.137.")) return ip;
        }
        return null;
    }

    private boolean hasHotspotNamedInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return false;
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp()) continue;
                String display = iface.getDisplayName() != null ? iface.getDisplayName().toLowerCase(Locale.ROOT) : "";
                if (looksLikeHotspotAdapter(display)) {
                    Enumeration<InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress addr = addrs.nextElement();
                        if (addr instanceof Inet4Address) return true;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return false;
    }

    private boolean looksLikeHotspotAdapter(String name) {
        return name.contains("local area connection*")
            || name.contains("microsoft wi-fi direct virtual")
            || name.contains("hosted network")
            || name.contains("mobile hotspot")
            || name.contains("androidtethering")
            || name.contains("shared");
    }

    private boolean isVirtualAdapter(String name) {
        return name.contains("virtualbox") ||
               name.contains("vmware") ||
               name.contains("hyper-v") ||
               name.contains("vethernet") ||
               name.contains("docker") ||
               name.contains("wsl");
    }
}
