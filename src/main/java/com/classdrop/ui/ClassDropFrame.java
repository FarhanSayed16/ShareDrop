package com.classdrop.ui;

import com.classdrop.model.*;
import com.classdrop.service.*;
import com.classdrop.util.Constants;
import com.classdrop.util.ShareLinks;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ClassDropFrame extends JFrame implements SessionListener {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RoomService roomService;
    private final FileService fileService;
    private final ClipboardService clipboardService;
    private final QrService qrService;
    private final NetworkInfoService networkInfo;
    private final int port;

    // UI Components
    private JLabel lblModeBadge;
    private final StatsService statsService;
    private final com.classdrop.db.TransferRepository transferRepository;
    private SearchPanel searchPanel;

    private JLabel lblRoomCode;
    private JLabel lblHostIp;
    private JTextField txtShareUrl;
    private JLabel lblQrImage;
    private JLabel lblActiveUsers;
    private JLabel lblSessionStats;

    private DeviceTableModel deviceTableModel;
    private TransferTableModel transferTableModel;

    private DefaultListModel<String> clipboardListModel;
    private JTextArea txtLogArea;

    private JButton btnStartSession;
    private JButton btnEndSession;
    private JComboBox<NetworkMode> cmbNetworkMode;

    public ClassDropFrame(RoomService roomService, FileService fileService,
                          ClipboardService clipboardService, QrService qrService,
                          NetworkInfoService networkInfo, int port) {
        this(roomService, fileService, clipboardService, qrService, networkInfo, port, null, null);
    }

    public ClassDropFrame(RoomService roomService, FileService fileService,
                          ClipboardService clipboardService, QrService qrService,
                          NetworkInfoService networkInfo, int port,
                          StatsService statsService, com.classdrop.db.TransferRepository transferRepository) {
        this.roomService = roomService;
        this.fileService = fileService;
        this.clipboardService = clipboardService;
        this.qrService = qrService;
        this.networkInfo = networkInfo;
        this.port = port;
        this.statsService = statsService;
        this.transferRepository = transferRepository;

        roomService.addListener(this);

        initUi();
        refreshDisplayFromSession();
    }

    private void initUi() {
        setTitle("ClassDrop — Host Classroom Hub (KJSIM MCA)");
        setSize(940, 700);
        setMinimumSize(new Dimension(840, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmAndExit();
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        mainPanel.setBackground(new Color(15, 23, 42)); // #0f172a

        // Top Header
        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);

        // Center Tabs
        mainPanel.add(buildCenterTabs(), BorderLayout.CENTER);

        // Bottom Status Bar
        mainPanel.add(buildStatusBar(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 8));
        header.setBackground(new Color(30, 41, 59)); // #1e293b
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(71, 85, 105), 1),
            new EmptyBorder(12, 16, 12, 16)
        ));

        // Keep-Open Warning Banner
        JLabel banner = new JLabel("⚠️  Keep ClassDrop running on this laptop — closing stops classroom sharing.", SwingConstants.CENTER);
        banner.setOpaque(true);
        banner.setBackground(new Color(13, 148, 136, 40));
        banner.setForeground(new Color(94, 234, 212));
        banner.setBorder(new EmptyBorder(6, 10, 6, 10));
        banner.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.add(banner, BorderLayout.NORTH);

        // Details Panel
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 6, 4, 6);

        // Row 0: Mode and IP
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblModeTitle = new JLabel("Mode:");
        lblModeTitle.setForeground(new Color(148, 163, 184));
        detailsPanel.add(lblModeTitle, gbc);

        gbc.gridx = 1; gbc.weightx = 0.3;
        JPanel modeBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modeBox.setOpaque(false);
        lblModeBadge = new JLabel(" HOTSPOT ");
        lblModeBadge.setOpaque(true);
        lblModeBadge.setBackground(new Color(13, 148, 136));
        lblModeBadge.setForeground(Color.WHITE);
        lblModeBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        modeBox.add(lblModeBadge);

        cmbNetworkMode = new JComboBox<>(NetworkMode.values());
        cmbNetworkMode.setSelectedItem(NetworkMode.HOTSPOT);
        cmbNetworkMode.addActionListener(e -> switchNetworkMode((NetworkMode) cmbNetworkMode.getSelectedItem()));
        modeBox.add(cmbNetworkMode);
        detailsPanel.add(modeBox, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JLabel lblIpTitle = new JLabel("Host IP:");
        lblIpTitle.setForeground(new Color(148, 163, 184));
        detailsPanel.add(lblIpTitle, gbc);

        gbc.gridx = 3; gbc.weightx = 0.7;
        lblHostIp = new JLabel("127.0.0.1");
        lblHostIp.setForeground(Color.WHITE);
        lblHostIp.setFont(new Font("Segoe UI", Font.BOLD, 13));
        detailsPanel.add(lblHostIp, gbc);

        // Row 1: 6-Digit Room Code (Large Monospace)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel lblCodeTitle = new JLabel("Room Code:");
        lblCodeTitle.setForeground(new Color(148, 163, 184));
        detailsPanel.add(lblCodeTitle, gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        lblRoomCode = new JLabel("------");
        lblRoomCode.setForeground(new Color(94, 234, 212)); // Teal accent
        lblRoomCode.setFont(new Font("Consolas", Font.BOLD, 26));
        detailsPanel.add(lblRoomCode, gbc);

        // Row 2: Share URL and Action Buttons
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0;
        JLabel lblUrlTitle = new JLabel("Share URL:");
        lblUrlTitle.setForeground(new Color(148, 163, 184));
        detailsPanel.add(lblUrlTitle, gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        JPanel urlActionPanel = new JPanel(new BorderLayout(6, 0));
        urlActionPanel.setOpaque(false);

        txtShareUrl = new JTextField("http://localhost:8080/r/------");
        txtShareUrl.setEditable(false);
        txtShareUrl.setBackground(new Color(51, 65, 85));
        txtShareUrl.setForeground(Color.WHITE);
        txtShareUrl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        urlActionPanel.add(txtShareUrl, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.setOpaque(false);

        JButton btnCopy = new JButton("Copy Link");
        styleButton(btnCopy, new Color(51, 65, 85));
        btnCopy.addActionListener(e -> copyShareUrl());
        btnRow.add(btnCopy);

        JButton btnWhatsapp = new JButton("WhatsApp");
        styleButton(btnWhatsapp, new Color(34, 197, 94));
        btnWhatsapp.addActionListener(e -> openWhatsappLink());
        btnRow.add(btnWhatsapp);

        JButton btnEmail = new JButton("Email");
        styleButton(btnEmail, new Color(59, 130, 246));
        btnEmail.addActionListener(e -> openEmailLink());
        btnRow.add(btnEmail);

        urlActionPanel.add(btnRow, BorderLayout.EAST);
        detailsPanel.add(urlActionPanel, gbc);

        header.add(detailsPanel, BorderLayout.CENTER);

        // QR Code Thumbnail (Right side)
        JPanel qrPanel = new JPanel(new BorderLayout());
        qrPanel.setOpaque(false);
        qrPanel.setBorder(new EmptyBorder(0, 10, 0, 0));
        lblQrImage = new JLabel();
        lblQrImage.setPreferredSize(new Dimension(100, 100));
        lblQrImage.setHorizontalAlignment(SwingConstants.CENTER);
        lblQrImage.setToolTipText("Scan to join room immediately");
        qrPanel.add(lblQrImage, BorderLayout.CENTER);

        header.add(qrPanel, BorderLayout.EAST);

        return header;
    }

    private JTabbedPane buildCenterTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Tab 1: Live Transfers
        transferTableModel = new TransferTableModel();
        JTable tblTransfers = new JTable(transferTableModel);
        styleTable(tblTransfers);
        tabs.addTab("Live Transfers", new JScrollPane(tblTransfers));

        // Tab 2: Connected Devices
        deviceTableModel = new DeviceTableModel();
        JTable tblDevices = new JTable(deviceTableModel);
        styleTable(tblDevices);
        tabs.addTab("Connected Devices", new JScrollPane(tblDevices));

        // Tab 3: Shared Clipboard
        clipboardListModel = new DefaultListModel<>();
        JList<String> listClipboard = new JList<>(clipboardListModel);
        listClipboard.setBackground(new Color(30, 41, 59));
        listClipboard.setForeground(Color.WHITE);
        listClipboard.setFont(new Font("Consolas", Font.PLAIN, 13));
        tabs.addTab("Classroom Clipboard", new JScrollPane(listClipboard));

        // Tab 4: Search & History
        searchPanel = new SearchPanel(transferRepository, statsService);
        tabs.addTab("History & Search", searchPanel);

        // Tab 5: Host Logs
        txtLogArea = new JTextArea();
        txtLogArea.setEditable(false);
        txtLogArea.setBackground(new Color(15, 23, 42));
        txtLogArea.setForeground(new Color(148, 163, 184));
        txtLogArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        tabs.addTab("System Logs", new JScrollPane(txtLogArea));

        return tabs;
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.setBackground(new Color(30, 41, 59));
        status.setBorder(new EmptyBorder(8, 12, 8, 12));

        // Left info
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        left.setOpaque(false);

        lblActiveUsers = new JLabel("Students Online: 0");
        lblActiveUsers.setForeground(Color.WHITE);
        lblActiveUsers.setFont(new Font("Segoe UI", Font.BOLD, 12));
        left.add(lblActiveUsers);

        lblSessionStats = new JLabel("Session Transfers: 0 | Transferred: 0 B");
        lblSessionStats.setForeground(new Color(148, 163, 184));
        lblSessionStats.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        left.add(lblSessionStats);

        status.add(left, BorderLayout.WEST);

        // Right controls: Start / End Session
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        btnStartSession = new JButton("New Session");
        styleButton(btnStartSession, new Color(13, 148, 136));
        btnStartSession.addActionListener(e -> startNewSession());
        right.add(btnStartSession);

        JButton btnSnapshot = new JButton("Save Snapshot");
        styleButton(btnSnapshot, new Color(59, 130, 246));
        btnSnapshot.addActionListener(e -> saveSnapshotAction());
        right.add(btnSnapshot);

        JButton btnRestoreSnapshot = new JButton("Restore Snapshot");
        styleButton(btnRestoreSnapshot, new Color(100, 116, 139));
        btnRestoreSnapshot.addActionListener(e -> restoreSnapshotAction());
        right.add(btnRestoreSnapshot);

        btnEndSession = new JButton("End Session");
        styleButton(btnEndSession, new Color(239, 68, 68));
        btnEndSession.addActionListener(e -> confirmEndSession());
        right.add(btnEndSession);

        status.add(right, BorderLayout.EAST);

        return status;
    }

    private void styleTable(JTable table) {
        table.setBackground(new Color(30, 41, 59));
        table.setForeground(Color.WHITE);
        table.setGridColor(new Color(71, 85, 105));
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setBackground(new Color(51, 65, 85));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public void refreshDisplayFromSession() {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session != null && session.isActive()) {
            lblRoomCode.setText(session.getRoomCode());
            lblHostIp.setText(session.getHostIp());
            lblModeBadge.setText(" " + session.getMode().name() + " ");

            String shareUrl = String.format("http://%s:%d/r/%s", session.getHostIp(), port, session.getRoomCode());
            txtShareUrl.setText(shareUrl);

            // Render QR Code thumbnail
            try {
                BufferedImage qr = qrService.generateQr(shareUrl, 96);
                lblQrImage.setIcon(new ImageIcon(qr));
            } catch (Exception e) {
                lblQrImage.setIcon(null);
            }

            deviceTableModel.setDevices(session.getDevices());
            btnEndSession.setEnabled(true);
            log("Session active. Code: " + session.getRoomCode() + ", Host: " + session.getHostIp());
        } else {
            lblRoomCode.setText("------");
            txtShareUrl.setText("No active session");
            lblQrImage.setIcon(null);
            btnEndSession.setEnabled(false);
            log("Session ended or not started.");
        }
        updateStatsDisplay();
    }

    private void switchNetworkMode(NetworkMode mode) {
        if (mode == null) return;
        String ip = (mode == NetworkMode.HOTSPOT) ? "192.168.137.1" : networkInfo.getPreferredIP();
        roomService.createSession(mode, ip);
        refreshDisplayFromSession();
    }

    private void startNewSession() {
        NetworkMode mode = (NetworkMode) cmbNetworkMode.getSelectedItem();
        String ip = (mode == NetworkMode.HOTSPOT) ? "192.168.137.1" : networkInfo.getPreferredIP();
        roomService.createSession(mode, ip);
        refreshDisplayFromSession();
    }

    private void saveSnapshotAction() {
        try {
            com.classdrop.service.SnapshotService snapshotService = 
                new com.classdrop.service.SnapshotService(roomService, fileService, clipboardService);
            boolean ok = snapshotService.saveDefault();
            if (ok) {
                JOptionPane.showMessageDialog(this, "Session state saved to session.ser", "Snapshot Saved", JOptionPane.INFORMATION_MESSAGE);
                log("Session state saved to session.ser");
            } else {
                JOptionPane.showMessageDialog(this, "No active session to snapshot.", "Snapshot Notice", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save snapshot: " + ex.getMessage(), "Snapshot Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restoreSnapshotAction() {
        try {
            com.classdrop.service.SnapshotService snapshotService = 
                new com.classdrop.service.SnapshotService(roomService, fileService, clipboardService);
            boolean ok = snapshotService.restoreDefault();
            if (ok) {
                refreshDisplayFromSession();
                JOptionPane.showMessageDialog(this, "Session state restored from session.ser", "Snapshot Restored", JOptionPane.INFORMATION_MESSAGE);
                log("Session state restored from session.ser");
            } else {
                JOptionPane.showMessageDialog(this, "No valid session snapshot found on disk (session.ser missing or empty).", "Restore Notice", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to restore snapshot: " + ex.getMessage(), "Snapshot Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmEndSession() {
        int opt = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to end this classroom session?\nAll active tokens and sharing will stop.",
            "End ClassDrop Session",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (opt == JOptionPane.YES_OPTION) {
            roomService.endSession();
            refreshDisplayFromSession();
        }
    }

    private void confirmAndExit() {
        int opt = JOptionPane.showConfirmDialog(
            this,
            "Exit ClassDrop?\nThis will stop sharing and terminate the host server.",
            "Confirm Exit",
            JOptionPane.YES_NO_OPTION
        );
        if (opt == JOptionPane.YES_OPTION) {
            roomService.endSession();
            dispose();
            System.exit(0);
        }
    }

    private void copyShareUrl() {
        String url = txtShareUrl.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        JOptionPane.showMessageDialog(this, "Copied share link:\n" + url, "Link Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openWhatsappLink() {
        String url = txtShareUrl.getText();
        String wa = ShareLinks.whatsappUrl(url);
        openBrowserUri(wa);
    }

    private void openEmailLink() {
        String url = txtShareUrl.getText();
        String mail = ShareLinks.emailUrl(url);
        openBrowserUri(mail);
    }

    private void openBrowserUri(String uriStr) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(uriStr));
            } else {
                copyShareUrl();
            }
        } catch (Exception e) {
            copyShareUrl();
        }
    }

    private void updateStatsDisplay() {
        int users = RoomSession.getActiveUserCount();
        lblActiveUsers.setText("Students Online: " + users);

        RoomSession session = roomService.getCurrentSessionOrNull();
        if (statsService != null && session != null) {
            StatsService.SessionStats stats = statsService.getSessionStats(session.getId());
            if (stats.transferCount() > 0) {
                lblSessionStats.setText(String.format("Session Transfers: %d (↑%d ↓%d) | Transferred: %s",
                    stats.transferCount(), stats.uploadCount(), stats.downloadCount(), formatBytes(stats.totalBytes())));
                return;
            }
        }

        int count = transferTableModel.getTransferCount();
        long bytes = transferTableModel.getTotalTransferredBytes();
        lblSessionStats.setText(String.format("Session Transfers: %d | Transferred: %s", count, formatBytes(bytes)));
    }

    private void log(String msg) {
        String line = "[" + LocalDateTime.now().format(TIME_FMT) + "] " + msg + "\n";
        txtLogArea.append(line);
        txtLogArea.setCaretPosition(txtLogArea.getDocument().getLength());
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB"};
        int idx = (int) (Math.log10(bytes) / Math.log10(1024));
        idx = Math.min(idx, units.length - 1);
        return String.format("%.1f %s", bytes / Math.pow(1024, idx), units[idx]);
    }

    // ── SessionListener Callbacks (dispatched to EDT via invokeLater) ──

    @Override
    public void onDeviceJoined(Device device) {
        SwingUtilities.invokeLater(() -> {
            deviceTableModel.addDevice(device);
            updateStatsDisplay();
            log("Device joined: " + device.getDisplayName() + " (" + device.getIpAddress() + ")");
        });
    }

    @Override
    public void onDeviceLeft(Device device) {
        SwingUtilities.invokeLater(() -> {
            deviceTableModel.removeDevice(device.getId());
            updateStatsDisplay();
            log("Device left: " + device.getDisplayName());
        });
    }

    @Override
    public void onTransferStarted(TransferRecord record) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.addOrUpdate(record);
            updateStatsDisplay();
            log("Transfer started: " + record.getOriginalName() + " (" + record.getDirection() + ")");
        });
    }

    @Override
    public void onTransferProgress(String transferId, long bytesTransferred, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.updateProgress(transferId, bytesTransferred, totalBytes);
        });
    }

    @Override
    public void onTransferCompleted(TransferRecord record) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.addOrUpdate(record);
            updateStatsDisplay();
            if (searchPanel != null) {
                searchPanel.doSearch();
            }
            log("Transfer complete: " + record.getOriginalName() + " [" + formatBytes(record.getSizeBytes()) + "]");
        });
    }

    @Override
    public void onTransferFailed(TransferRecord record, String reason) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.addOrUpdate(record);
            updateStatsDisplay();
            log("Transfer FAILED: " + record.getOriginalName() + " (" + reason + ")");
        });
    }

    @Override
    public void onSessionStarted(RoomSession session) {
        SwingUtilities.invokeLater(this::refreshDisplayFromSession);
    }

    @Override
    public void onSessionEnded(RoomSession session) {
        SwingUtilities.invokeLater(this::refreshDisplayFromSession);
    }

    @Override
    public void onClipboardUpdated(ClipboardEntry entry) {
        SwingUtilities.invokeLater(() -> {
            String item = String.format("[%s] %s (%s): %s",
                entry.getCreatedAt().format(TIME_FMT),
                entry.getDeviceName(),
                entry.getLanguage().toUpperCase(),
                entry.getBody().replace('\n', ' ')
            );
            clipboardListModel.addElement(item);
            log("Clipboard updated by " + entry.getDeviceName() + ": " + entry.getTitle());
        });
    }
}
