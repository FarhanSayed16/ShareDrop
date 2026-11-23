package com.classdrop.ui;

import com.classdrop.model.Device;

import javax.swing.table.AbstractTableModel;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class DeviceTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"#", "Display Name", "IP Address", "Joined At", "Last Seen"};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<Device> devices = new ArrayList<>();

    public synchronized void setDevices(List<Device> newDevices) {
        devices.clear();
        if (newDevices != null) {
            devices.addAll(newDevices);
        }
        fireTableDataChanged();
    }

    public synchronized void addDevice(Device device) {
        if (device != null) {
            devices.add(device);
            int row = devices.size() - 1;
            fireTableRowsInserted(row, row);
        }
    }

    public synchronized void removeDevice(String deviceId) {
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).getId().equals(deviceId)) {
                devices.remove(i);
                fireTableRowsDeleted(i, i);
                break;
            }
        }
    }

    public synchronized int getDeviceCount() {
        return devices.size();
    }

    public synchronized Device getDeviceAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= devices.size()) return null;
        return devices.get(rowIndex);
    }

    @Override
    public int getRowCount() {
        return devices.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= devices.size()) return null;
        Device d = devices.get(rowIndex);
        switch (columnIndex) {
            case 0: return rowIndex + 1;
            case 1: return d.getDisplayName();
            case 2: return d.getIpAddress() != null ? d.getIpAddress() : "Local";
            case 3: return d.getJoinedAt() != null ? d.getJoinedAt().format(TIME_FMT) : "-";
            case 4: return d.getLastSeen() != null ? d.getLastSeen().format(TIME_FMT) : "-";
            default: return null;
        }
    }
}
