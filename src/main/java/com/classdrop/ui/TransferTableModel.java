package com.classdrop.ui;

import com.classdrop.model.TransferRecord;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public final class TransferTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
        "File Name", "Kind", "Size", "Direction", "Status", "Progress", "Speed"
    };

    public static final class TransferItem {
        private final String id;
        private final String filename;
        private final String kind;
        private final long totalBytes;
        private final String direction;
        private String status;
        private long bytesTransferred;
        private double emaSpeedBytesPerSec; // 50-sample Exponential Moving Average
        private long lastUpdateTime;
        private long lastBytes;

        public TransferItem(String id, String filename, String kind, long totalBytes, String direction, String status) {
            this.id = id;
            this.filename = filename;
            this.kind = kind;
            this.totalBytes = totalBytes;
            this.direction = direction;
            this.status = status;
            this.bytesTransferred = ("OK".equals(status)) ? totalBytes : 0;
            this.lastUpdateTime = System.currentTimeMillis();
            this.lastBytes = this.bytesTransferred;
        }

        public void updateProgress(long currentBytes) {
            long now = System.currentTimeMillis();
            long dt = now - lastUpdateTime;
            if (dt > 100) {
                long db = currentBytes - lastBytes;
                double instantSpeed = (dt > 0) ? (db * 1000.0 / dt) : 0;

                // 50-sample EMA smoothing formula: (49 * prev + instant) / 50
                if (emaSpeedBytesPerSec <= 0) {
                    emaSpeedBytesPerSec = instantSpeed;
                } else {
                    emaSpeedBytesPerSec = (49.0 * emaSpeedBytesPerSec + instantSpeed) / 50.0;
                }

                this.lastUpdateTime = now;
                this.lastBytes = currentBytes;
            }
            this.bytesTransferred = currentBytes;
        }
    }

    private final List<TransferItem> transfers = new ArrayList<>();

    public synchronized void addOrUpdate(TransferRecord record) {
        if (record == null) return;
        for (int i = 0; i < transfers.size(); i++) {
            TransferItem item = transfers.get(i);
            if (item.id.equals(record.getId())) {
                item.status = record.getStatus();
                if ("OK".equals(record.getStatus())) {
                    item.bytesTransferred = item.totalBytes;
                }
                fireTableRowsUpdated(i, i);
                return;
            }
        }

        TransferItem newItem = new TransferItem(
            record.getId(),
            record.getOriginalName(),
            record.getItemKind(),
            record.getSizeBytes(),
            record.getDirection(),
            record.getStatus()
        );
        transfers.add(0, newItem);
        fireTableRowsInserted(0, 0);
    }

    public synchronized void updateProgress(String transferId, long currentBytes, long totalBytes) {
        for (int i = 0; i < transfers.size(); i++) {
            TransferItem item = transfers.get(i);
            if (item.id.equals(transferId)) {
                item.updateProgress(currentBytes);
                fireTableRowsUpdated(i, i);
                break;
            }
        }
    }

    public synchronized int getTransferCount() {
        return transfers.size();
    }

    public synchronized long getTotalTransferredBytes() {
        long sum = 0;
        for (TransferItem t : transfers) {
            sum += t.bytesTransferred;
        }
        return sum;
    }

    @Override
    public int getRowCount() {
        return transfers.size();
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
        if (rowIndex < 0 || rowIndex >= transfers.size()) return null;
        TransferItem t = transfers.get(rowIndex);
        switch (columnIndex) {
            case 0: return t.filename;
            case 1: return t.kind;
            case 2: return formatBytes(t.totalBytes);
            case 3: return t.direction;
            case 4: return t.status;
            case 5: {
                if (t.totalBytes <= 0) return "100%";
                int percent = (int) Math.min(100, (t.bytesTransferred * 100 / t.totalBytes));
                return percent + "%";
            }
            case 6: {
                if ("OK".equals(t.status) || "FAILED".equals(t.status)) return "-";
                return formatSpeed(t.emaSpeedBytesPerSec);
            }
            default: return null;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int idx = (int) (Math.log10(bytes) / Math.log10(1024));
        idx = Math.min(idx, units.length - 1);
        return String.format("%.1f %s", bytes / Math.pow(1024, idx), units[idx]);
    }

    private static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec <= 0) return "0 KB/s";
        if (bytesPerSec < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSec / 1024.0);
        }
        return String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0));
    }
}
