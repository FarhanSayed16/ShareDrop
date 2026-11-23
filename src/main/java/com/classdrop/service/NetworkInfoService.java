package com.classdrop.service;

import com.classdrop.model.NetworkMode;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class NetworkInfoService {

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

                String name = iface.getName().toLowerCase();
                String displayName = iface.getDisplayName().toLowerCase();

                // Filter out common virtual adapters
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
            // Log and return best effort
            System.err.println("Error enumerating network interfaces: " + e.getMessage());
        }
        return addresses;
    }

    public String getPreferredIP() {
        List<String> addrs = getIPv4Addresses();
        if (addrs.isEmpty()) {
            return "127.0.0.1";
        }
        // Prefer Windows Mobile Hotspot IP (192.168.137.1)
        for (String ip : addrs) {
            if ("192.168.137.1".equals(ip)) {
                return ip;
            }
        }
        // Otherwise return first non-loopback
        return addrs.get(0);
    }

    public NetworkMode detectMode(String chosenIp) {
        if ("192.168.137.1".equals(chosenIp)) {
            return NetworkMode.HOTSPOT;
        }
        return NetworkMode.LAN;
    }

    /** True when Windows Mobile Hotspot adapter IP is present. */
    public boolean isHotspotAvailable() {
        return getIPv4Addresses().contains("192.168.137.1");
    }

    public String resolveHostIp(NetworkMode mode) {
        if (mode == NetworkMode.HOTSPOT) {
            return isHotspotAvailable() ? "192.168.137.1" : getPreferredIP();
        }
        return getPreferredIP();
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
