package com.classdrop.ui;

import com.classdrop.model.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DeviceTableModelTest {

    private DeviceTableModel model;

    @BeforeEach
    void setUp() {
        model = new DeviceTableModel();
    }

    @Test
    void testEmptyModel() {
        assertEquals(0, model.getRowCount());
        assertEquals(5, model.getColumnCount());
        assertEquals("Display Name", model.getColumnName(1));
    }

    @Test
    void testAddAndRemoveDevice() {
        Device d1 = new Device("id-1", "Alice", "192.168.1.10", "tok-1", LocalDateTime.now(), LocalDateTime.now());
        Device d2 = new Device("id-2", "Bob", "192.168.1.11", "tok-2", LocalDateTime.now(), LocalDateTime.now());

        model.addDevice(d1);
        model.addDevice(d2);

        assertEquals(2, model.getRowCount());
        assertEquals("Alice", model.getValueAt(0, 1));
        assertEquals("Bob", model.getValueAt(1, 1));
        assertEquals("192.168.1.10", model.getValueAt(0, 2));

        model.removeDevice("id-1");
        assertEquals(1, model.getRowCount());
        assertEquals("Bob", model.getValueAt(0, 1));
    }
}
