package com.classdrop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkInfoServiceTest {

    @Test
    void isHotspotIp_recognizesWindowsSubnet() {
        assertTrue(NetworkInfoService.isHotspotIp("192.168.137.1"));
        assertTrue(NetworkInfoService.isHotspotIp("192.168.137.22"));
        assertFalse(NetworkInfoService.isHotspotIp("10.0.0.5"));
        assertFalse(NetworkInfoService.isHotspotIp(null));
    }

    @Test
    void getInterfaceSummaries_doesNotThrow() {
        NetworkInfoService nis = new NetworkInfoService();
        assertNotNull(nis.getInterfaceSummaries());
        assertNotNull(nis.getIPv4Addresses());
        assertNotNull(nis.getPreferredIP());
    }
}
