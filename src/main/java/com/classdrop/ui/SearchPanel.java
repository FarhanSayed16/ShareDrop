package com.classdrop.ui;

import com.classdrop.db.TransferRepository;
import com.classdrop.model.TransferRecord;
import com.classdrop.service.StatsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SearchPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TransferRepository transferRepository;
    private final StatsService statsService;

    private JTextField txtQuery;
    private JComboBox<String> cmbKind;
    private JComboBox<String> cmbRange;
    private JLabel lblStatsSummary;
    private DefaultTableModel tableModel;
    private JTable tblResults;

    public SearchPanel(TransferRepository transferRepository, StatsService statsService) {
        this.transferRepository = transferRepository;
        this.statsService = statsService;

        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(15, 23, 42)); // #0f172a
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top Filter Bar
        add(buildFilterPanel(), BorderLayout.NORTH);

        // Center Table
        add(buildTablePanel(), BorderLayout.CENTER);

        // Bottom Stats Banner
        add(buildBottomPanel(), BorderLayout.SOUTH);

        // Initial search load
        doSearch();
    }

    private JPanel buildFilterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        panel.setBackground(new Color(30, 41, 59)); // #1e293b
        panel.setBorder(BorderFactory.createLineBorder(new Color(71, 85, 105), 1));

        JLabel lblName = new JLabel("Filename:");
        lblName.setForeground(Color.WHITE);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(lblName);

        txtQuery = new JTextField(15);
        txtQuery.setBackground(new Color(15, 23, 42));
        txtQuery.setForeground(Color.WHITE);
        txtQuery.setCaretColor(Color.WHITE);
        txtQuery.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(71, 85, 105)),
            new EmptyBorder(4, 6, 4, 6)
        ));
        panel.add(txtQuery);

        JLabel lblKind = new JLabel("Kind:");
        lblKind.setForeground(Color.WHITE);
        lblKind.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(lblKind);

        cmbKind = new JComboBox<>(new String[]{"ALL", "DOCUMENT", "IMAGE", "SNIPPET"});
        styleCombo(cmbKind);
        panel.add(cmbKind);

        JLabel lblRange = new JLabel("Time:");
        lblRange.setForeground(Color.WHITE);
        lblRange.setFont(new Font("Segoe UI", Font.BOLD, 12));
        panel.add(lblRange);

        cmbRange = new JComboBox<>(new String[]{"All Time", "Today", "Past 1 Hour", "Past 24 Hours"});
        styleCombo(cmbRange);
        panel.add(cmbRange);

        JButton btnSearch = new JButton("Search");
        styleButton(btnSearch, new Color(13, 148, 136)); // Teal #0D9488
        btnSearch.addActionListener(e -> doSearch());
        panel.add(btnSearch);

        JButton btnClear = new JButton("Clear");
        styleButton(btnClear, new Color(71, 85, 105));
        btnClear.addActionListener(e -> {
            txtQuery.setText("");
            cmbKind.setSelectedIndex(0);
            cmbRange.setSelectedIndex(0);
            doSearch();
        });
        panel.add(btnClear);

        return panel;
    }

    private JScrollPane buildTablePanel() {
        String[] columns = {"Timestamp", "Filename", "Kind", "Size", "Direction", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        tblResults = new JTable(tableModel);
        tblResults.setBackground(new Color(30, 41, 59));
        tblResults.setForeground(Color.WHITE);
        tblResults.setGridColor(new Color(51, 65, 85));
        tblResults.setRowHeight(30);
        tblResults.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblResults.setFillsViewportHeight(true);
        tblResults.setShowVerticalLines(false);
        tblResults.setSelectionBackground(new Color(13, 148, 136, 120));
        tblResults.setSelectionForeground(Color.WHITE);
        tblResults.getTableHeader().setBackground(new Color(51, 65, 85));
        tblResults.getTableHeader().setForeground(new Color(248, 250, 252));
        tblResults.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        tblResults.getColumnModel().getColumn(0).setPreferredWidth(140);
        tblResults.getColumnModel().getColumn(1).setPreferredWidth(260);
        tblResults.getColumnModel().getColumn(2).setPreferredWidth(90);
        tblResults.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tblResults.getColumnModel().getColumn(3).setPreferredWidth(90);
        tblResults.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tblResults.getColumnModel().getColumn(4).setPreferredWidth(90);
        tblResults.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        tblResults.getColumnModel().getColumn(5).setPreferredWidth(80);
        tblResults.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);

        JScrollPane scroll = new JScrollPane(tblResults);
        scroll.getViewport().setBackground(new Color(15, 23, 42));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(71, 85, 105)));
        return scroll;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(6, 4, 0, 4));

        lblStatsSummary = new JLabel("Database status: Initializing...");
        lblStatsSummary.setForeground(new Color(148, 163, 184));
        lblStatsSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.add(lblStatsSummary, BorderLayout.WEST);

        return panel;
    }

    public void doSearch() {
        if (transferRepository == null) {
            lblStatsSummary.setText("Database is not connected (in-memory mode).");
            return;
        }

        String query = txtQuery != null ? txtQuery.getText().trim() : "";
        String kind = cmbKind != null ? (String) cmbKind.getSelectedItem() : "ALL";
        String range = cmbRange != null ? (String) cmbRange.getSelectedItem() : "All Time";

        LocalDateTime fromDate = null;
        LocalDateTime toDate = LocalDateTime.now();

        if ("Today".equalsIgnoreCase(range)) {
            fromDate = LocalDateTime.now().toLocalDate().atStartOfDay();
        } else if ("Past 1 Hour".equalsIgnoreCase(range)) {
            fromDate = LocalDateTime.now().minusHours(1);
        } else if ("Past 24 Hours".equalsIgnoreCase(range)) {
            fromDate = LocalDateTime.now().minusHours(24);
        }

        List<TransferRecord> records = transferRepository.search(query, kind, fromDate, toDate);

        tableModel.setRowCount(0);
        for (TransferRecord r : records) {
            String timeStr = r.getCreatedAt() != null ? r.getCreatedAt().format(TIME_FMT) : "-";
            String sizeStr = formatBytes(r.getSizeBytes());
            tableModel.addRow(new Object[]{
                timeStr,
                r.getOriginalName(),
                r.getItemKind(),
                sizeStr,
                r.getDirection(),
                r.getStatus()
            });
        }

        if (statsService != null) {
            StatsService.AllTimeStats allStats = statsService.getAllTimeStats();
            lblStatsSummary.setText(String.format(
                "Found %d results | All-Time History: %d sessions, %d transfers, %s volume",
                records.size(),
                allStats.totalSessions(),
                allStats.totalTransfers(),
                formatBytes(allStats.totalBytes())
            ));
        } else {
            lblStatsSummary.setText(String.format("Found %d results in database history", records.size()));
        }
    }

    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(new Color(15, 23, 42));
        combo.setForeground(Color.WHITE);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
