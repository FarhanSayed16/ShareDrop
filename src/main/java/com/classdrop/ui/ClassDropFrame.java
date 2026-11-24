package com.classdrop.ui;

import com.classdrop.model.*;
import com.classdrop.service.*;
import com.classdrop.util.ShareLinks;
import com.classdrop.util.ShareUrlBuilder;
import com.classdrop.util.WifiQrPayload;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ClassDropFrame extends JFrame implements SessionListener {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RoomService roomService;
    private final FileService fileService;
    private final ClipboardService clipboardService;
    private final QrService qrService;
    private final NetworkInfoService networkInfo;
    private final int port;
    private final StatsService statsService;
    private final com.classdrop.db.TransferRepository transferRepository;
    private final TunnelService tunnelService;

    private SearchPanel searchPanel;
    private JLabel lblModeBadge;
    private JLabel lblRoomCode;
    private JLabel lblHostIp;
    private JLabel lblBrandHint;
    private JTextField txtShareUrl;
    private JLabel lblQrImage;
    private JLabel lblActiveUsers;
    private JLabel lblSessionStats;
    private JLabel lblHostToken;
    private JLabel transfersEmptyHint;
    private JLabel devicesEmptyHint;
    private JLabel lblInternetBanner;
    private JLabel lblOfflineReady;
    private JLabel lblIpList;
    private JPasswordField txtPassphrase;
    private JTextField txtHotspotSsid;
    private JPasswordField txtHotspotPassword;
    private JLabel lblQrNotReady;
    private JLabel lblWifiQrImage;
    private DropFolderWatcher dropFolderWatcher;
    private boolean firewallPromptShown = false;

    private DeviceTableModel deviceTableModel;
    private TransferTableModel transferTableModel;
    private DefaultListModel<String> clipboardListModel;
    private JTextArea txtLogArea;

    private JButton btnStartSession;
    private JButton btnEndSession;
    private JComboBox<NetworkMode> cmbNetworkMode;
    private JTable tblDevices;
    private JTable tblTransfers;
    private DropZonePanel dropZone;
    /** Prevents combo setSelectedItem from recursively creating new sessions. */
    private boolean syncingUi = false;
    private boolean hotspotHealPrompted = false;
    private JPanel transfersCards;
    private JPanel devicesCards;

    public ClassDropFrame(RoomService roomService, FileService fileService,
                          ClipboardService clipboardService, QrService qrService,
                          NetworkInfoService networkInfo, int port) {
        this(roomService, fileService, clipboardService, qrService, networkInfo, port, null, null, null);
    }

    public ClassDropFrame(RoomService roomService, FileService fileService,
                          ClipboardService clipboardService, QrService qrService,
                          NetworkInfoService networkInfo, int port,
                          StatsService statsService, com.classdrop.db.TransferRepository transferRepository) {
        this(roomService, fileService, clipboardService, qrService, networkInfo, port, statsService, transferRepository, null);
    }

    public ClassDropFrame(RoomService roomService, FileService fileService,
                          ClipboardService clipboardService, QrService qrService,
                          NetworkInfoService networkInfo, int port,
                          StatsService statsService, com.classdrop.db.TransferRepository transferRepository,
                          TunnelService tunnelService) {
        this.roomService = roomService;
        this.fileService = fileService;
        this.clipboardService = clipboardService;
        this.qrService = qrService;
        this.networkInfo = networkInfo;
        this.port = port;
        this.statsService = statsService;
        this.transferRepository = transferRepository;
        this.tunnelService = tunnelService != null ? tunnelService : new TunnelService(port);

        roomService.addListener(this);
        initUi();
        refreshDisplayFromSession();
        // Re-check hotspot adapter while the window is open (host may toggle Mobile Hotspot)
        javax.swing.Timer readinessTimer = new javax.swing.Timer(4000, e -> {
            updateOfflineReadiness();
            maybeHealHotspotSession();
        });
        readinessTimer.setRepeats(true);
        readinessTimer.start();
    }

    private void maybeHealHotspotSession() {
        RoomSession s = roomService.getCurrentSessionOrNull();
        if (hotspotHealPrompted || s == null || !s.isActive()) return;
        if (s.getMode() != NetworkMode.HOTSPOT) return;
        String hotspotIp = networkInfo.getHotspotIpOrNull();
        if (hotspotIp == null) return;
        if (hotspotIp.equals(s.getHostIp())) return;

        hotspotHealPrompted = true;
        int opt = JOptionPane.showConfirmDialog(this,
            "Mobile Hotspot is now ON, but this session still uses " + s.getHostIp() + ".\n"
                + "Switch the room to " + hotspotIp + " so student QR/links work?\n"
                + "(Creates a new room code — students must rejoin.)",
            "Hotspot Detected",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (opt == JOptionPane.YES_OPTION) {
            String pass = s.requiresPassphrase() ? s.getPassphrase() : null;
            log("Hotspot heal — moving session to " + hotspotIp);
            roomService.createSession(NetworkMode.HOTSPOT, hotspotIp, pass);
        }
    }

    private void initUi() {
        setTitle("ClassDrop - Host Classroom Hub");
        setSize(1020, 760);
        setMinimumSize(new Dimension(900, 660));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getRootPane().putClientProperty("JRootPane.titleBarBackground", UiTheme.BG);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                confirmAndExit();
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBorder(new EmptyBorder(16, 16, 16, 16));
        mainPanel.setBackground(UiTheme.BG);
        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildCenterTabs(), BorderLayout.CENTER);
        mainPanel.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(mainPanel);
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);

        JPanel banners = new JPanel();
        banners.setOpaque(false);
        banners.setLayout(new BoxLayout(banners, BoxLayout.Y_AXIS));

        JLabel banner = new JLabel("  Keep this window open — closing stops classroom sharing", SwingConstants.LEFT);
        banner.setOpaque(true);
        banner.setBackground(new Color(15, 158, 145, 28));
        banner.setForeground(UiTheme.ACCENT_SOFT);
        banner.setBorder(new EmptyBorder(10, 14, 10, 14));
        banner.setFont(UiTheme.UI_SMALL_BOLD);
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        banners.add(banner);

        lblInternetBanner = new JLabel("  Offline mode — no internet required. Students must join your hotspot/LAN.", SwingConstants.LEFT);
        lblInternetBanner.setOpaque(true);
        lblInternetBanner.setBackground(new Color(15, 158, 145, 40));
        lblInternetBanner.setForeground(UiTheme.ACCENT_SOFT);
        lblInternetBanner.setBorder(new EmptyBorder(8, 14, 8, 14));
        lblInternetBanner.setFont(UiTheme.UI_SMALL_BOLD);
        lblInternetBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblInternetBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        banners.add(Box.createVerticalStrut(6));
        banners.add(lblInternetBanner);

        lblOfflineReady = new JLabel("  Offline readiness: checking…", SwingConstants.LEFT);
        lblOfflineReady.setOpaque(true);
        lblOfflineReady.setBackground(new Color(51, 65, 85));
        lblOfflineReady.setForeground(UiTheme.MUTED);
        lblOfflineReady.setBorder(new EmptyBorder(8, 14, 8, 14));
        lblOfflineReady.setFont(UiTheme.UI_SMALL);
        lblOfflineReady.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblOfflineReady.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        banners.add(Box.createVerticalStrut(6));
        banners.add(lblOfflineReady);
        header.add(banners, BorderLayout.NORTH);

        JPanel hero = cardPanel();
        hero.setLayout(new BorderLayout(20, 0));
        hero.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel brand = new JLabel("ClassDrop");
        brand.setForeground(UiTheme.TEXT);
        brand.setFont(UiTheme.BRAND);
        brandRow.add(brand);
        lblModeBadge = new JLabel(" LAN ");
        lblModeBadge.setOpaque(true);
        lblModeBadge.setBackground(UiTheme.ACCENT);
        lblModeBadge.setForeground(Color.WHITE);
        lblModeBadge.setFont(UiTheme.UI_SMALL_BOLD);
        lblModeBadge.setBorder(new EmptyBorder(3, 8, 3, 8));
        brandRow.add(lblModeBadge);
        left.add(brandRow);

        lblBrandHint = new JLabel("Students join in the browser — zero install");
        lblBrandHint.setForeground(UiTheme.MUTED);
        lblBrandHint.setFont(UiTheme.UI_SMALL);
        lblBrandHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(lblBrandHint);
        left.add(Box.createVerticalStrut(16));

        JLabel codeLbl = new JLabel("ROOM CODE");
        codeLbl.setForeground(UiTheme.MUTED);
        codeLbl.setFont(UiTheme.UI_SMALL_BOLD);
        codeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(codeLbl);

        lblRoomCode = new JLabel("------");
        lblRoomCode.setForeground(UiTheme.ACCENT_SOFT);
        lblRoomCode.setFont(UiTheme.CODE);
        lblRoomCode.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(lblRoomCode);
        left.add(Box.createVerticalStrut(14));

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel modeLbl = new JLabel("Mode");
        modeLbl.setForeground(UiTheme.MUTED);
        modeLbl.setFont(UiTheme.UI_SMALL_BOLD);
        meta.add(modeLbl);
        java.util.List<NetworkMode> modes = new ArrayList<>();
        modes.add(NetworkMode.HOTSPOT);
        modes.add(NetworkMode.LAN);
        if (com.classdrop.util.Config.isInternetShareEnabled()) {
            modes.add(NetworkMode.RELAY);
        }
        cmbNetworkMode = new JComboBox<>(modes.toArray(new NetworkMode[0]));
        cmbNetworkMode.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof NetworkMode m) {
                    setText(ShareUrlBuilder.displayModeName(m));
                }
                return c;
            }
        });
        NetworkMode preferred = networkInfo.isHotspotAvailable() ? NetworkMode.HOTSPOT : NetworkMode.LAN;
        cmbNetworkMode.setSelectedItem(preferred);
        cmbNetworkMode.addActionListener(e -> {
            if (syncingUi) return;
            switchNetworkMode((NetworkMode) cmbNetworkMode.getSelectedItem());
        });
        meta.add(cmbNetworkMode);
        JLabel ipTitle = new JLabel("  Host IP");
        ipTitle.setForeground(UiTheme.MUTED);
        ipTitle.setFont(UiTheme.UI_SMALL_BOLD);
        meta.add(ipTitle);
        lblHostIp = new JLabel("127.0.0.1");
        lblHostIp.setForeground(UiTheme.TEXT);
        lblHostIp.setFont(UiTheme.UI_BOLD);
        meta.add(lblHostIp);
        JLabel passLbl = new JLabel("  Passphrase");
        passLbl.setForeground(UiTheme.MUTED);
        passLbl.setFont(UiTheme.UI_SMALL_BOLD);
        meta.add(passLbl);
        txtPassphrase = new JPasswordField(10);
        txtPassphrase.setToolTipText("Required for INTERNET mode — students must enter this to join");
        String defaultPass = com.classdrop.util.Config.getString("room.passphrase", "");
        if (defaultPass != null && !defaultPass.isBlank()) {
            txtPassphrase.setText(defaultPass);
        }
        txtPassphrase.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                RoomSession live = roomService.getCurrentSessionOrNull();
                if (live != null && live.isActive()) {
                    applyPassphraseFromField(live);
                }
            }
        });
        meta.add(txtPassphrase);
        left.add(meta);
        left.add(Box.createVerticalStrut(12));

        JPanel urlRow = new JPanel(new BorderLayout(8, 0));
        urlRow.setOpaque(false);
        urlRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        txtShareUrl = new JTextField("http://localhost:8080/r/------");
        txtShareUrl.setEditable(false);
        txtShareUrl.setFont(UiTheme.UI_SMALL);
        urlRow.add(txtShareUrl, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton btnCopy = pillButton("Copy", UiTheme.SURFACE_ALT);
        btnCopy.addActionListener(e -> copyShareUrl());
        actions.add(btnCopy);
        JButton btnWhatsapp = pillButton("WhatsApp", UiTheme.OK);
        btnWhatsapp.addActionListener(e -> openWhatsappLink());
        actions.add(btnWhatsapp);
        JButton btnEmail = pillButton("Email", UiTheme.INFO);
        btnEmail.addActionListener(e -> openEmailLink());
        actions.add(btnEmail);
        urlRow.add(actions, BorderLayout.EAST);
        left.add(urlRow);
        left.add(Box.createVerticalStrut(8));

        JPanel wifiRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        wifiRow.setOpaque(false);
        wifiRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel ssidLbl = new JLabel("Hotspot SSID");
        ssidLbl.setForeground(UiTheme.MUTED);
        ssidLbl.setFont(UiTheme.UI_SMALL_BOLD);
        wifiRow.add(ssidLbl);
        txtHotspotSsid = new JTextField("ClassDrop-Hub", 10);
        txtHotspotSsid.setToolTipText("Type your Windows Mobile Hotspot name for the invite pack / Wi‑Fi QR");
        txtHotspotSsid.getDocument().addDocumentListener(simpleDocListener(this::refreshWifiQr));
        wifiRow.add(txtHotspotSsid);
        JLabel pwdLbl = new JLabel("Wi‑Fi Password");
        pwdLbl.setForeground(UiTheme.MUTED);
        pwdLbl.setFont(UiTheme.UI_SMALL_BOLD);
        wifiRow.add(pwdLbl);
        txtHotspotPassword = new JPasswordField(8);
        txtHotspotPassword.setToolTipText("Optional — invite pack + Wi‑Fi QR for students to join hotspot");
        txtHotspotPassword.getDocument().addDocumentListener(simpleDocListener(this::refreshWifiQr));
        wifiRow.add(txtHotspotPassword);
        left.add(wifiRow);
        left.add(Box.createVerticalStrut(6));

        lblIpList = new JLabel(" ");
        lblIpList.setForeground(UiTheme.MUTED);
        lblIpList.setFont(UiTheme.MONO);
        lblIpList.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(lblIpList);
        left.add(Box.createVerticalStrut(8));

        JPanel offlineActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        offlineActions.setOpaque(false);
        offlineActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton btnConnectGuide = pillButton("How Students Connect", UiTheme.ACCENT);
        btnConnectGuide.setToolTipText("Offline classroom steps — hotspot + QR + mobile data tip");
        btnConnectGuide.addActionListener(e -> showStudentConnectGuide());
        offlineActions.add(btnConnectGuide);
        JButton btnStartHotspot = pillButton("Start Hotspot", UiTheme.OK);
        btnStartHotspot.setToolTipText("Try to enable Windows Mobile Hotspot automatically");
        btnStartHotspot.addActionListener(e -> startMobileHotspotScript());
        offlineActions.add(btnStartHotspot);
        JButton btnHotspotSettings = pillButton("Open Hotspot Settings", UiTheme.INFO);
        btnHotspotSettings.addActionListener(e -> openWindowsHotspotSettings());
        offlineActions.add(btnHotspotSettings);
        JButton btnFirewall = pillButton("Fix Firewall", UiTheme.WARN);
        btnFirewall.setToolTipText("Allow TCP " + port + " on all profiles (run as Administrator)");
        btnFirewall.addActionListener(e -> runFirewallHelper());
        offlineActions.add(btnFirewall);
        JButton btnCopyInvite = pillButton("Copy Invite Pack", UiTheme.SURFACE_ALT);
        btnCopyInvite.addActionListener(e -> copyOfflineInvitePack());
        offlineActions.add(btnCopyInvite);
        JButton btnPoster = pillButton("Export Poster", new Color(99, 102, 241));
        btnPoster.addActionListener(e -> exportProjectorPoster());
        offlineActions.add(btnPoster);
        JButton btnDropFolder = pillButton("USB Drop Folder", new Color(100, 116, 139));
        btnDropFolder.setToolTipText("Watch data/drop-inbox — copy files from a USB stick to share offline");
        btnDropFolder.addActionListener(e -> toggleDropFolderWatcher());
        offlineActions.add(btnDropFolder);
        left.add(offlineActions);

        hero.add(left, BorderLayout.CENTER);

        JPanel qrCard = new JPanel(new BorderLayout(0, 8));
        qrCard.setOpaque(true);
        qrCard.setBackground(Color.WHITE);
        qrCard.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel qrStack = new JPanel();
        qrStack.setLayout(new OverlayLayout(qrStack));
        qrStack.setOpaque(false);
        qrStack.setPreferredSize(new Dimension(132, 132));
        lblQrImage = new JLabel();
        lblQrImage.setPreferredSize(new Dimension(132, 132));
        lblQrImage.setHorizontalAlignment(SwingConstants.CENTER);
        lblQrImage.setAlignmentX(0.5f);
        lblQrImage.setAlignmentY(0.5f);
        lblQrImage.setToolTipText("Students scan this QR to join");
        lblQrNotReady = new JLabel("<html><center><b>NOT READY</b><br>Turn on<br>Mobile Hotspot</center></html>", SwingConstants.CENTER);
        lblQrNotReady.setOpaque(true);
        lblQrNotReady.setBackground(new Color(239, 68, 68, 220));
        lblQrNotReady.setForeground(Color.WHITE);
        lblQrNotReady.setFont(UiTheme.UI_SMALL_BOLD);
        lblQrNotReady.setPreferredSize(new Dimension(132, 132));
        lblQrNotReady.setAlignmentX(0.5f);
        lblQrNotReady.setAlignmentY(0.5f);
        lblQrNotReady.setVisible(false);
        qrStack.add(lblQrNotReady);
        qrStack.add(lblQrImage);
        qrCard.add(qrStack, BorderLayout.CENTER);
        JLabel qrHint = new JLabel("1. Room join", SwingConstants.CENTER);
        qrHint.setForeground(new Color(71, 85, 105));
        qrHint.setFont(UiTheme.UI_SMALL_BOLD);
        qrCard.add(qrHint, BorderLayout.SOUTH);

        JPanel wifiQrCard = new JPanel(new BorderLayout(0, 8));
        wifiQrCard.setOpaque(true);
        wifiQrCard.setBackground(Color.WHITE);
        wifiQrCard.setBorder(new EmptyBorder(10, 10, 10, 10));
        lblWifiQrImage = new JLabel("Enter SSID", SwingConstants.CENTER);
        lblWifiQrImage.setPreferredSize(new Dimension(132, 132));
        lblWifiQrImage.setForeground(new Color(100, 116, 139));
        lblWifiQrImage.setFont(UiTheme.UI_SMALL);
        lblWifiQrImage.setToolTipText("Wi‑Fi QR — students scan to join the hotspot first");
        wifiQrCard.add(lblWifiQrImage, BorderLayout.CENTER);
        JLabel wifiHint = new JLabel("2. Join Wi‑Fi", SwingConstants.CENTER);
        wifiHint.setForeground(new Color(71, 85, 105));
        wifiHint.setFont(UiTheme.UI_SMALL_BOLD);
        wifiQrCard.add(wifiHint, BorderLayout.SOUTH);

        JPanel qrWrap = new JPanel();
        qrWrap.setOpaque(false);
        qrWrap.setLayout(new BoxLayout(qrWrap, BoxLayout.Y_AXIS));
        qrCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        wifiQrCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        qrWrap.add(qrCard);
        qrWrap.add(Box.createVerticalStrut(10));
        qrWrap.add(wifiQrCard);
        hero.add(qrWrap, BorderLayout.EAST);
        refreshWifiQr();

        header.add(hero, BorderLayout.CENTER);
        return header;
    }

    private JPanel cardPanel() {
        JPanel p = new JPanel();
        p.setBackground(UiTheme.SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UiTheme.BORDER, 1),
            new EmptyBorder(0, 0, 0, 0)
        ));
        return p;
    }

    private JTabbedPane buildCenterTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UiTheme.UI_BOLD);
        tabs.setBackground(UiTheme.BG);
        tabs.setForeground(UiTheme.TEXT);

        // Transfers tab with drop zone + empty state
        transferTableModel = new TransferTableModel();
        tblTransfers = new JTable(transferTableModel);
        styleTable(tblTransfers);
        JScrollPane transferScroll = wrapTable(tblTransfers);
        transfersEmptyHint = emptyHintLabel(
            "No files in this room yet",
            "Drop files on the zone above, or click Browse Files."
        );
        transfersCards = cardTable(transferScroll, transfersEmptyHint);
        JPanel transferStack = transfersCards;

        dropZone = new DropZonePanel();
        dropZone.setOnFilesDropped(this::shareFiles);
        dropZone.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                shareLocalFile();
            }
        });

        JPanel transferActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        transferActions.setOpaque(false);
        JButton btnBrowse = pillButton("Browse Files", UiTheme.ACCENT);
        btnBrowse.addActionListener(e -> shareLocalFile());
        transferActions.add(btnBrowse);
        JButton btnRemoveFile = pillButton("Remove Selected", UiTheme.DANGER);
        btnRemoveFile.addActionListener(e -> removeSelectedFile());
        transferActions.add(btnRemoveFile);
        JLabel tip = new JLabel("  Tip: drag PDFs, images, or zip datasets from File Explorer.");
        tip.setForeground(UiTheme.MUTED);
        tip.setFont(UiTheme.UI_SMALL);
        transferActions.add(tip);

        JPanel transfersPanel = new JPanel(new BorderLayout(0, 10));
        transfersPanel.setBackground(UiTheme.BG);
        transfersPanel.setBorder(new EmptyBorder(8, 4, 4, 4));
        transfersPanel.add(dropZone, BorderLayout.NORTH);
        transfersPanel.add(transferStack, BorderLayout.CENTER);
        transfersPanel.add(transferActions, BorderLayout.SOUTH);
        tabs.addTab("Live Transfers", transfersPanel);

        // Devices
        deviceTableModel = new DeviceTableModel();
        tblDevices = new JTable(deviceTableModel);
        styleTable(tblDevices);
        JScrollPane deviceScroll = wrapTable(tblDevices);
        devicesEmptyHint = emptyHintLabel(
            "No students connected yet",
            "Share the room code or QR. Devices appear here when they join."
        );
        devicesCards = cardTable(deviceScroll, devicesEmptyHint);
        JPanel deviceStack = devicesCards;
        JPanel deviceActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        deviceActions.setOpaque(false);
        JButton btnKick = pillButton("Kick Selected Device", UiTheme.WARN);
        btnKick.addActionListener(e -> kickSelectedDevice());
        deviceActions.add(btnKick);
        JPanel devicesPanel = new JPanel(new BorderLayout(0, 8));
        devicesPanel.setBackground(UiTheme.BG);
        devicesPanel.setBorder(new EmptyBorder(8, 4, 4, 4));
        devicesPanel.add(deviceStack, BorderLayout.CENTER);
        devicesPanel.add(deviceActions, BorderLayout.SOUTH);
        tabs.addTab("Connected Devices", devicesPanel);

        // Clipboard
        clipboardListModel = new DefaultListModel<>();
        JList<String> listClipboard = new JList<>(clipboardListModel);
        listClipboard.setBackground(UiTheme.SURFACE);
        listClipboard.setForeground(UiTheme.TEXT);
        listClipboard.setFont(new Font("Consolas", Font.PLAIN, 13));
        listClipboard.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane clipScroll = new JScrollPane(listClipboard);
        styleScroll(clipScroll);
        tabs.addTab("Classroom Clipboard", clipScroll);

        searchPanel = new SearchPanel(transferRepository, statsService);
        tabs.addTab("History & Search", searchPanel);

        txtLogArea = new JTextArea();
        txtLogArea.setEditable(false);
        txtLogArea.setBackground(UiTheme.BG);
        txtLogArea.setForeground(UiTheme.MUTED);
        txtLogArea.setFont(UiTheme.MONO);
        txtLogArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane logScroll = new JScrollPane(txtLogArea);
        styleScroll(logScroll);
        tabs.addTab("System Logs", logScroll);

        transferTableModel.addTableModelListener(e -> updateEmptyStates());
        deviceTableModel.addTableModelListener(e -> updateEmptyStates());
        updateEmptyStates();
        return tabs;
    }

    private JPanel buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.setBackground(UiTheme.SURFACE);
        status.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
            new EmptyBorder(10, 12, 10, 12)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setOpaque(false);
        lblActiveUsers = new JLabel("Students Online: 0");
        lblActiveUsers.setForeground(UiTheme.TEXT);
        lblActiveUsers.setFont(UiTheme.UI_SMALL_BOLD);
        left.add(lblActiveUsers);
        lblSessionStats = new JLabel("Session Transfers: 0 | Transferred: 0 B");
        lblSessionStats.setForeground(UiTheme.MUTED);
        lblSessionStats.setFont(UiTheme.UI_SMALL);
        left.add(lblSessionStats);
        lblHostToken = new JLabel("Host token: -");
        lblHostToken.setForeground(UiTheme.MUTED);
        lblHostToken.setFont(UiTheme.MONO);
        left.add(lblHostToken);
        status.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        btnStartSession = pillButton("New Session", UiTheme.ACCENT);
        btnStartSession.addActionListener(e -> startNewSession());
        right.add(btnStartSession);
        JButton btnSnapshot = pillButton("Save Snapshot", UiTheme.INFO);
        btnSnapshot.addActionListener(e -> saveSnapshotAction());
        right.add(btnSnapshot);
        JButton btnRestoreSnapshot = pillButton("Restore Snapshot", new Color(100, 116, 139));
        btnRestoreSnapshot.addActionListener(e -> restoreSnapshotAction());
        right.add(btnRestoreSnapshot);
        btnEndSession = pillButton("End Session", UiTheme.DANGER);
        btnEndSession.addActionListener(e -> confirmEndSession());
        right.add(btnEndSession);
        status.add(right, BorderLayout.EAST);
        return status;
    }

    private JPanel cardTable(JScrollPane scroll, JLabel emptyHint) {
        JPanel stack = new JPanel(new CardLayout());
        stack.setOpaque(false);
        JPanel emptyWrap = new JPanel(new BorderLayout());
        emptyWrap.setBackground(UiTheme.SURFACE);
        emptyWrap.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        emptyWrap.add(emptyHint, BorderLayout.CENTER);
        stack.add(scroll, "table");
        stack.add(emptyWrap, "empty");
        return stack;
    }

    private JLabel emptyHintLabel(String title, String subtitle) {
        JLabel label = new JLabel("<html><div style='text-align:center;color:#94a3b8;'>"
            + "<div style='font-size:14px;font-weight:bold;color:#e2e8f0;margin-bottom:6px;'>" + title + "</div>"
            + "<div style='font-size:12px;'>" + subtitle + "</div></div></html>", SwingConstants.CENTER);
        label.setOpaque(false);
        return label;
    }

    private void updateEmptyStates() {
        if (transfersCards != null) {
            CardLayout cl = (CardLayout) transfersCards.getLayout();
            cl.show(transfersCards, transferTableModel.getRowCount() == 0 ? "empty" : "table");
        }
        if (devicesCards != null) {
            CardLayout cl = (CardLayout) devicesCards.getLayout();
            cl.show(devicesCards, deviceTableModel.getRowCount() == 0 ? "empty" : "table");
        }
    }

    private JScrollPane wrapTable(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        styleScroll(scroll);
        return scroll;
    }

    private void styleScroll(JScrollPane scroll) {
        scroll.getViewport().setBackground(UiTheme.SURFACE);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        scroll.setBackground(UiTheme.SURFACE);
    }

    private void styleTable(JTable table) {
        table.setBackground(UiTheme.SURFACE);
        table.setForeground(UiTheme.TEXT);
        table.setGridColor(new Color(51, 65, 85));
        table.setRowHeight(30);
        table.setFont(UiTheme.UI_SMALL);
        table.setSelectionBackground(new Color(13, 148, 136, 120));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setBackground(UiTheme.SURFACE_ALT);
        table.getTableHeader().setForeground(UiTheme.TEXT);
        table.getTableHeader().setFont(UiTheme.UI_SMALL_BOLD);
        table.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBackground(UiTheme.SURFACE);
        renderer.setForeground(UiTheme.TEXT);
        renderer.setBorder(new EmptyBorder(0, 8, 0, 8));
        table.setDefaultRenderer(Object.class, renderer);
    }

    private JButton pillButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(UiTheme.UI_SMALL_BOLD);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        return btn;
    }

    public void refreshDisplayFromSession() {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session != null && session.isActive()) {
            lblRoomCode.setText(session.getRoomCode());
            lblHostIp.setText(session.getHostIp());
            lblModeBadge.setText(" " + ShareUrlBuilder.displayModeName(session.getMode()) + " ");

            String publicBase = tunnelService != null ? tunnelService.getPublicBaseUrl() : null;
            String shareUrl = ShareUrlBuilder.shareUrlForSession(session, port, publicBase);
            boolean hotspotModeNotReady = session.getMode() == NetworkMode.HOTSPOT && !networkInfo.isHotspotAvailable();
            if (shareUrl == null && session.isInternetShare()) {
                txtShareUrl.setText("Internet Share waiting for public HTTPS URL…");
                lblQrImage.setIcon(null);
                setQrReadyOverlay(true, "Waiting for public HTTPS URL");
            } else if (hotspotModeNotReady) {
                txtShareUrl.setText("NOT READY — turn on Mobile Hotspot (expected " + NetworkInfoService.WINDOWS_HOTSPOT_IP + ")");
                lblQrImage.setIcon(null);
                setQrReadyOverlay(true, "Turn on Mobile Hotspot");
            } else {
                txtShareUrl.setText(shareUrl != null ? shareUrl : "No share URL");
                try {
                    BufferedImage qr = qrService.generateQr(shareUrl, 110);
                    lblQrImage.setIcon(new ImageIcon(qr));
                    setQrReadyOverlay(false, null);
                } catch (Exception e) {
                    lblQrImage.setIcon(null);
                    setQrReadyOverlay(true, "QR error");
                }
            }
            deviceTableModel.setDevices(session.getDevices());
            btnEndSession.setEnabled(true);
            if (lblHostToken != null && session.getHostToken().length() >= 8) {
                lblHostToken.setText("Host token: " + session.getHostToken().substring(0, 8) + "...");
                lblHostToken.setToolTipText(session.getHostToken());
            }
            updateInternetBanner(session);
            // Guard setSelectedItem — without this, ActionListener recreates sessions forever
            syncingUi = true;
            try {
                cmbNetworkMode.setSelectedItem(session.getMode());
                if (txtPassphrase != null && session.requiresPassphrase()) {
                    txtPassphrase.setText(session.getPassphrase());
                }
            } finally {
                syncingUi = false;
            }
            log("Session active. Code: " + session.getRoomCode()
                + ", Mode: " + ShareUrlBuilder.displayModeName(session.getMode())
                + ", URL: " + shareUrl);
        } else {
            lblRoomCode.setText("------");
            txtShareUrl.setText("No active session");
            lblQrImage.setIcon(null);
            setQrReadyOverlay(true, "No session");
            btnEndSession.setEnabled(false);
            if (lblHostToken != null) {
                lblHostToken.setText("Host token: -");
                lblHostToken.setToolTipText(null);
            }
            updateInternetBanner(null);
            log("Session ended or not started.");
        }
        updateOfflineReadiness();
        updateIpListLabel();
        refreshWifiQr();
        maybePromptFirewallOnce(session);
        updateStatsDisplay();
        updateEmptyStates();
    }

    private javax.swing.event.DocumentListener simpleDocListener(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        };
    }

    private void refreshWifiQr() {
        if (lblWifiQrImage == null || txtHotspotSsid == null) return;
        String ssid = txtHotspotSsid.getText().trim();
        String pass = txtHotspotPassword != null ? new String(txtHotspotPassword.getPassword()) : "";
        if (ssid.isEmpty()) {
            lblWifiQrImage.setIcon(null);
            lblWifiQrImage.setText("Enter SSID");
            return;
        }
        String payload = pass.isEmpty()
            ? WifiQrPayload.build("nopass", ssid, null, false)
            : WifiQrPayload.wpa(ssid, pass);
        try {
            BufferedImage qr = qrService.generateQr(payload, 110);
            lblWifiQrImage.setIcon(new ImageIcon(qr));
            lblWifiQrImage.setText("");
        } catch (Exception ex) {
            lblWifiQrImage.setIcon(null);
            lblWifiQrImage.setText("Wi‑Fi QR error");
        }
    }

    private void maybePromptFirewallOnce(RoomSession session) {
        if (firewallPromptShown) return;
        if (session == null || !session.isActive()) return;
        if (session.getMode() != NetworkMode.HOTSPOT) return;
        if (!networkInfo.isHotspotAvailable()) return;
        firewallPromptShown = true;
        int opt = JOptionPane.showConfirmDialog(this,
            "First Hotspot session tip:\n\n"
                + "Allow Windows Firewall TCP " + port + " now?\n"
                + "(Hotspot networks are often Public — without this, phones time out.)",
            "Fix Firewall?",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (opt == JOptionPane.YES_OPTION) {
            runFirewallHelper();
        }
    }

    private void setQrReadyOverlay(boolean show, String message) {
        if (lblQrNotReady == null) return;
        if (message != null && !message.isBlank()) {
            lblQrNotReady.setText("<html><center><b>NOT READY</b><br>" + message.replace("\n", "<br>") + "</center></html>");
        }
        lblQrNotReady.setVisible(show);
    }

    private void updateIpListLabel() {
        if (lblIpList == null) return;
        List<String> lines = networkInfo.getInterfaceSummaries();
        if (lines.isEmpty()) {
            lblIpList.setText("Local IPs: (none detected)");
            return;
        }
        String joined = String.join("  |  ", lines.size() > 3 ? lines.subList(0, 3) : lines);
        lblIpList.setText("Local IPs: " + joined);
        lblIpList.setToolTipText(String.join("\n", lines));
    }

    private void updateInternetBanner(RoomSession session) {
        if (lblInternetBanner == null) return;
        if (session != null && session.isInternetShare()) {
            String pub = tunnelService != null ? tunnelService.getPublicBaseUrl() : null;
            lblInternetBanner.setBackground(new Color(245, 158, 11, 50));
            lblInternetBanner.setForeground(new Color(253, 230, 138));
            if (pub != null) {
                lblInternetBanner.setText("  Internet Share ON — anyone with link + passphrase can join (" + pub + ")");
            } else {
                lblInternetBanner.setText("  Internet Share ON — waiting for public URL (install cloudflared or set public.base.url)");
            }
        } else {
            lblInternetBanner.setBackground(new Color(15, 158, 145, 40));
            lblInternetBanner.setForeground(UiTheme.ACCENT_SOFT);
            lblInternetBanner.setText("  Offline mode — no internet required. Students must join your hotspot/LAN.");
        }
    }

    private void updateOfflineReadiness() {
        if (lblOfflineReady == null) return;
        RoomSession session = roomService.getCurrentSessionOrNull();
        boolean hotspotUp = networkInfo.isHotspotAvailable();
        boolean internet = session != null && session.isInternetShare();
        if (internet) {
            boolean pub = tunnelService != null && tunnelService.getPublicBaseUrl() != null;
            lblOfflineReady.setBackground(pub ? new Color(245, 158, 11, 40) : new Color(239, 68, 68, 40));
            lblOfflineReady.setForeground(pub ? new Color(253, 230, 138) : new Color(254, 202, 202));
            lblOfflineReady.setText(pub
                ? "  Internet ready — public HTTPS URL active. Prefer Hotspot when everyone is in-room."
                : "  Internet not ready — no public URL yet. Install cloudflared or paste public.base.url.");
            return;
        }
        if (hotspotUp) {
            String hsIp = networkInfo.getHotspotIpOrNull() != null ? networkInfo.getHotspotIpOrNull() : NetworkInfoService.WINDOWS_HOTSPOT_IP;
            lblOfflineReady.setBackground(new Color(34, 197, 94, 35));
            lblOfflineReady.setForeground(new Color(187, 247, 208));
            lblOfflineReady.setText("  OFFLINE READY — Host hotspot " + hsIp + " UP. Students: scan Wi‑Fi QR → then Room QR. Data OFF. http://classdrop.local:" + port);
            if (session != null && session.getMode() == NetworkMode.HOTSPOT) {
                setQrReadyOverlay(false, null);
            }
        } else if (networkInfo.isOnPhoneHotspot()) {
            String ip = networkInfo.getPreferredIP();
            lblOfflineReady.setBackground(new Color(59, 130, 246, 40));
            lblOfflineReady.setForeground(new Color(191, 219, 254));
            lblOfflineReady.setText("  REVERSE OFFLINE — PC is on a phone hotspot (" + ip
                + "). Set Mode=LAN. Students open http://" + ip + ":" + port + " (meter phone data if uplink shared).");
            setQrReadyOverlay(false, null);
        } else {
            lblOfflineReady.setBackground(new Color(239, 68, 68, 45));
            lblOfflineReady.setForeground(new Color(254, 202, 202));
            lblOfflineReady.setText("  NOT OFFLINE-READY — Click Start Hotspot / Open Settings + Fix Firewall (TCP "
                + port + "). QR blocked until hotspot is ON.");
            if (session == null || session.getMode() == NetworkMode.HOTSPOT) {
                setQrReadyOverlay(true, "Turn on Mobile Hotspot");
            }
        }
        updateIpListLabel();
    }

    private void startMobileHotspotScript() {
        Path script = Path.of("scripts", "enable-mobile-hotspot.ps1");
        if (!Files.exists(script)) {
            script = Path.of(System.getProperty("user.dir", "."), "scripts", "enable-mobile-hotspot.ps1");
        }
        try {
            if (Files.exists(script)) {
                new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString()
                ).start();
                log("Launched enable-mobile-hotspot.ps1");
                JOptionPane.showMessageDialog(this,
                    "Hotspot start script launched.\n"
                        + "Wait a few seconds, then look for OFFLINE READY.\n"
                        + "If it fails, use Open Hotspot Settings.",
                    "Start Hotspot",
                    JOptionPane.INFORMATION_MESSAGE);
                javax.swing.Timer t = new javax.swing.Timer(2500, e -> updateOfflineReadiness());
                t.setRepeats(false);
                t.start();
            } else {
                openWindowsHotspotSettings();
            }
        } catch (Exception ex) {
            openWindowsHotspotSettings();
            JOptionPane.showMessageDialog(this, "Auto-start failed: " + ex.getMessage()
                    + "\nOpened settings instead.", "Start Hotspot", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void toggleDropFolderWatcher() {
        try {
            if (dropFolderWatcher != null && dropFolderWatcher.isRunning()) {
                dropFolderWatcher.stop();
                dropFolderWatcher = null;
                log("USB drop folder watcher stopped");
                JOptionPane.showMessageDialog(this, "Drop folder watcher stopped.", "USB Drop Folder", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            Path folder = Path.of("data", "drop-inbox");
            Files.createDirectories(folder);
            dropFolderWatcher = new DropFolderWatcher(folder, path -> SwingUtilities.invokeLater(() -> ingestDroppedFile(path)));
            dropFolderWatcher.start();
            log("Watching USB drop folder: " + folder.toAbsolutePath());
            JOptionPane.showMessageDialog(this,
                "Watching:\n" + folder.toAbsolutePath() + "\n\n"
                    + "Copy files from a USB stick into this folder.\n"
                    + "They are shared into the active ClassDrop room automatically.\n"
                    + "(Sneakernet / zero-radio assist — still fully offline.)",
                "USB Drop Folder ON",
                JOptionPane.INFORMATION_MESSAGE);
            try {
                Desktop.getDesktop().open(folder.toFile());
            } catch (Exception ignored) {
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not start watcher: " + ex.getMessage(),
                "USB Drop Folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ingestDroppedFile(Path path) {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null || !session.isActive()) {
            log("Drop ignored (no session): " + path.getFileName());
            return;
        }
        try {
            fileService.send(path);
            log("Drop-folder shared: " + path.getFileName());
            Toolkit.getDefaultToolkit().beep();
        } catch (Exception ex) {
            log("Drop-folder failed: " + path.getFileName() + " — " + ex.getMessage());
        }
    }

    private void showStudentConnectGuide() {
        boolean hotspotUp = networkInfo.isHotspotAvailable();
        RoomSession session = roomService.getCurrentSessionOrNull();
        String code = (session != null && session.isActive()) ? session.getRoomCode() : "------";
        String url = txtShareUrl != null ? txtShareUrl.getText() : ("http://192.168.137.1:" + port);
        String msg =
            "OFFLINE CLASSROOM CONNECT (no internet required)\n\n"
                + "HOST\n"
                + "1. Turn ON Windows Mobile Hotspot (button: Open Hotspot Settings).\n"
                + "2. Set Mode to HOTSPOT. Wait until banner says OFFLINE READY.\n"
                + "3. Once: run scripts\\allow-firewall-8080.bat as Administrator.\n"
                + "4. Keep ClassDrop window open.\n\n"
                + "STUDENTS\n"
                + "1. Join the teacher hotspot Wi‑Fi (not Somaiya / campus Wi‑Fi).\n"
                + "2. Ignore “No Internet” on that Wi‑Fi — it is normal.\n"
                + "3. Turn OFF mobile data (critical — stops cellular from stealing the link).\n"
                + "4. Scan the QR or open:\n   " + url + "\n"
                + "5. Enter room code " + code + " and name → Join.\n\n"
                + "Hotspot adapter now: " + (hotspotUp ? "DETECTED (192.168.137.1)" : "NOT DETECTED — turn hotspot ON") + "\n"
                + "Help page on phones: http://192.168.137.1:" + port + "/connect";
        JTextArea area = new JTextArea(msg);
        area.setEditable(false);
        area.setFont(UiTheme.UI_SMALL);
        area.setBackground(UiTheme.SURFACE);
        area.setForeground(UiTheme.TEXT);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(560, 360));
        Object[] options = {"Copy Invite Pack", "Open Hotspot Settings", "Close"};
        int choice = JOptionPane.showOptionDialog(
            this, scroll, "How Students Connect (Offline)",
            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
            null, options, options[2]);
        if (choice == 0) copyOfflineInvitePack();
        if (choice == 1) openWindowsHotspotSettings();
    }

    private void openWindowsHotspotSettings() {
        try {
            new ProcessBuilder("cmd", "/c", "start", "ms-settings:network-mobilehotspot").start();
            log("Opened Windows Mobile Hotspot settings");
            JOptionPane.showMessageDialog(this,
                "Mobile Hotspot settings opened.\n\n"
                    + "1. Turn Mobile Hotspot ON\n"
                    + "2. Note the network name & password for students\n"
                    + "3. Return here — banner should become OFFLINE READY\n"
                    + "4. Set Mode to HOTSPOT",
                "Hotspot Settings",
                JOptionPane.INFORMATION_MESSAGE);
            updateOfflineReadiness();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Could not open settings automatically.\n"
                    + "Open Settings → Network & Internet → Mobile hotspot manually.\n\n"
                    + ex.getMessage(),
                "Hotspot Settings",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void copyOfflineInvitePack() {
        RoomSession session = roomService.getCurrentSessionOrNull();
        String code = (session != null && session.isActive()) ? session.getRoomCode() : "------";
        String hostIp = networkInfo.getHotspotIpOrNull();
        if (hostIp == null) {
            hostIp = (session != null && session.isActive()) ? session.getHostIp() : NetworkInfoService.WINDOWS_HOTSPOT_IP;
        }
        String url = ShareUrlBuilder.localRoomUrl(hostIp, port, code);
        String ssid = txtHotspotSsid != null ? txtHotspotSsid.getText().trim() : "";
        String wifiPass = txtHotspotPassword != null ? new String(txtHotspotPassword.getPassword()) : "";
        StringBuilder pack = new StringBuilder();
        pack.append("ClassDrop offline invite (no internet required)\n");
        if (!ssid.isEmpty()) {
            pack.append("Wi-Fi SSID: ").append(ssid).append("\n");
            if (!wifiPass.isEmpty()) {
                pack.append("Wi-Fi Password: ").append(wifiPass).append("\n");
            }
        } else {
            pack.append("1) Join teacher hotspot Wi-Fi\n");
        }
        pack.append("2) Turn OFF mobile data\n");
        pack.append("3) Open ").append(url).append("\n");
        pack.append("   or http://classdrop.local:").append(port).append("/r/").append(code).append("\n");
        pack.append("4) Room code: ").append(code).append("\n");
        pack.append("Help: http://").append(hostIp).append(":").append(port).append("/connect\n");
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(pack.toString()), null);
        log("Copied offline invite pack");
        JOptionPane.showMessageDialog(this, "Invite pack copied to clipboard.\nPaste on projector / board / chat.",
            "Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void runFirewallHelper() {
        Path script = Path.of("scripts", "allow-firewall-8080.bat");
        if (!Files.exists(script)) {
            script = Path.of(System.getProperty("user.dir", "."), "scripts", "allow-firewall-8080.bat");
        }
        String cmd = "netsh advfirewall firewall add rule name=\"ClassDrop HTTP " + port
            + "\" dir=in action=allow protocol=TCP localport=" + port + " profile=any";
        int opt = JOptionPane.showConfirmDialog(this,
            "Windows must allow inbound TCP " + port + " (Hotspot is often Public).\n\n"
                + "Click Yes to launch the helper script (approve UAC / Run as Administrator).\n\n"
                + "Manual command:\n" + cmd,
            "Fix Firewall",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (opt != JOptionPane.YES_OPTION) return;
        try {
            if (Files.exists(script)) {
                new ProcessBuilder("cmd", "/c", "start", "\"ClassDrop Firewall\"", script.toAbsolutePath().toString()).start();
            } else {
                new ProcessBuilder("cmd", "/c", "start", "powershell", "-NoExit", "-Command", cmd).start();
            }
            log("Launched firewall helper");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not launch helper:\n" + ex.getMessage()
                    + "\n\nRun this as Admin:\n" + cmd,
                "Firewall", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportProjectorPoster() {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null || !session.isActive()) {
            JOptionPane.showMessageDialog(this, "Start a session first.", "Export Poster", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (session.getMode() == NetworkMode.HOTSPOT && !networkInfo.isHotspotAvailable()) {
            JOptionPane.showMessageDialog(this, "Turn on Mobile Hotspot first (OFFLINE READY).", "Export Poster", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String publicBase = tunnelService != null ? tunnelService.getPublicBaseUrl() : null;
            String shareUrl = ShareUrlBuilder.shareUrlForSession(session, port, publicBase);
            if (shareUrl == null) {
                shareUrl = ShareUrlBuilder.localRoomUrl(session.getHostIp(), port, session.getRoomCode());
            }
            String ssid = txtHotspotSsid != null ? txtHotspotSsid.getText().trim() : "";
            Path out = Path.of("data", "classdrop-poster-" + session.getRoomCode() + ".png");
            new PosterExportService(qrService).exportPng(out, session.getRoomCode(), shareUrl, ssid,
                "Turn OFF mobile data. Stay on hotspot even if phone says No Internet. Help: /connect");
            log("Exported poster: " + out.toAbsolutePath());
            JOptionPane.showMessageDialog(this,
                "Poster saved:\n" + out.toAbsolutePath() + "\n\nOpen it on the projector.",
                "Poster Exported", JOptionPane.INFORMATION_MESSAGE);
            try {
                Desktop.getDesktop().open(out.toFile());
            } catch (Exception ignored) {
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Export Poster", JOptionPane.ERROR_MESSAGE);
        }
    }

    private NetworkMode currentOrPreferredMode() {
        RoomSession current = roomService.getCurrentSessionOrNull();
        if (current != null && current.isActive()) return current.getMode();
        return networkInfo.isHotspotAvailable() ? NetworkMode.HOTSPOT : NetworkMode.LAN;
    }

    private void restoreModeCombo(NetworkMode mode) {
        syncingUi = true;
        try {
            cmbNetworkMode.setSelectedItem(mode != null ? mode : NetworkMode.HOTSPOT);
        } finally {
            syncingUi = false;
        }
    }

    private void abortInternetShare(NetworkMode restoreMode) {
        if (tunnelService != null) {
            tunnelService.stopInternetShare();
        }
        restoreModeCombo(restoreMode);
        updateInternetBanner(roomService.getCurrentSessionOrNull());
        updateOfflineReadiness();
    }

    private void switchNetworkMode(NetworkMode mode) {
        if (syncingUi || mode == null) return;

        if (mode == NetworkMode.RELAY) {
            enableInternetShare();
            return;
        }

        RoomSession current = roomService.getCurrentSessionOrNull();
        String ip = networkInfo.resolveHostIp(mode);
        if (current != null && current.isActive()
                && current.getMode() == mode
                && ip.equals(current.getHostIp())) {
            updateOfflineReadiness();
            return;
        }

        if (current != null && current.isActive()) {
            int opt = JOptionPane.showConfirmDialog(this,
                "Switching mode creates a new room code.\nConnected students must rejoin.\n\nContinue?",
                "New Room Code",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (opt != JOptionPane.YES_OPTION) {
                restoreModeCombo(current.getMode());
                return;
            }
        }

        if (tunnelService != null) {
            tunnelService.stopInternetShare();
        }

        if (mode == NetworkMode.HOTSPOT && !networkInfo.isHotspotAvailable()) {
            JOptionPane.showMessageDialog(this,
                "Windows Mobile Hotspot IP (192.168.137.1) was not detected.\n"
                    + "Turn on Mobile Hotspot, then choose HOTSPOT again.\n"
                    + "Using best available local IP for now: " + ip,
                "Hotspot Not Detected",
                JOptionPane.WARNING_MESSAGE);
        }

        String pass = txtPassphrase != null ? new String(txtPassphrase.getPassword()).trim() : "";
        roomService.createSession(mode, ip, pass.isEmpty() ? null : pass);
    }

    private void enableInternetShare() {
        if (!com.classdrop.util.Config.isInternetShareEnabled()) {
            JOptionPane.showMessageDialog(this,
                "Internet Share is disabled in application.properties (internet.share.enabled=false).",
                "Internet Share Disabled",
                JOptionPane.WARNING_MESSAGE);
            abortInternetShare(currentOrPreferredMode());
            return;
        }

        NetworkMode previousMode = currentOrPreferredMode();
        if (previousMode == NetworkMode.RELAY) {
            previousMode = networkInfo.isHotspotAvailable() ? NetworkMode.HOTSPOT : NetworkMode.LAN;
        }

        String pass = txtPassphrase != null ? new String(txtPassphrase.getPassword()).trim() : "";
        if (pass.length() < 4) {
            JOptionPane.showMessageDialog(this,
                "Internet Share requires a room passphrase (min 4 characters).\n"
                    + "Students outside your hotspot/LAN will need this passphrase to join.",
                "Passphrase Required",
                JOptionPane.WARNING_MESSAGE);
            abortInternetShare(previousMode);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "This leaves offline-only mode.\n\n"
                + "• Room becomes reachable on the public internet via HTTPS tunnel\n"
                + "• A new room code will be created (students must rejoin)\n"
                + "• Passphrase is mandatory\n"
                + "• Host laptop needs internet uplink\n\n"
                + "Prefer HOTSPOT when everyone is in the same room.\n\nContinue with Internet Share?",
            "Leave Offline Mode?",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            abortInternetShare(previousMode);
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        boolean tunnelOk = false;
        try {
            tunnelOk = tunnelService.startInternetShare();
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }

        if (!tunnelOk && tunnelService.getPublicBaseUrl() == null) {
            String manual = JOptionPane.showInputDialog(this,
                "cloudflared was not found or did not return a URL.\n"
                    + "Paste a public HTTPS base URL from ngrok/Cloudflare\n"
                    + "(example: https://abc.trycloudflare.com):",
                "Manual Public URL",
                JOptionPane.QUESTION_MESSAGE);
            if (manual == null || manual.isBlank()) {
                abortInternetShare(previousMode);
                return;
            }
            tunnelService.setManualPublicBaseUrl(manual.trim());
        }

        if (tunnelService.getPublicBaseUrl() == null) {
            JOptionPane.showMessageDialog(this,
                "No public HTTPS URL available. Staying in offline mode.",
                "Internet Share Failed",
                JOptionPane.ERROR_MESSAGE);
            abortInternetShare(previousMode);
            return;
        }

        String ip = networkInfo.getPreferredIP();
        RoomSession created = roomService.createSession(NetworkMode.RELAY, ip, pass);
        JOptionPane.showMessageDialog(this,
            "Internet Share enabled.\n\n"
                + "Public link:\n" + ShareUrlBuilder.shareUrlForSession(created, port, tunnelService.getPublicBaseUrl()) + "\n\n"
                + "Passphrase: " + pass + "\n\n"
                + "Students on Somaiya Wi-Fi or mobile data can join with this link.",
            "Internet Share Ready",
            JOptionPane.INFORMATION_MESSAGE);
        refreshDisplayFromSession();
    }

    private void applyPassphraseFromField(RoomSession session) {
        if (session == null || txtPassphrase == null) return;
        String pass = new String(txtPassphrase.getPassword()).trim();
        if (session.isInternetShare() && pass.length() < 4) {
            return; // keep existing passphrase rather than clearing internet gate
        }
        if (!pass.isEmpty()) {
            session.setPassphrase(pass);
            roomService.setSessionPassphrase(pass);
        } else if (!session.isInternetShare()) {
            session.setPassphrase(null);
        }
    }

    private void startNewSession() {
        NetworkMode mode = (NetworkMode) cmbNetworkMode.getSelectedItem();
        if (mode == null) mode = NetworkMode.HOTSPOT;
        if (mode == NetworkMode.RELAY) {
            enableInternetShare();
            return;
        }
        if (tunnelService != null) {
            tunnelService.stopInternetShare();
        }
        String ip = networkInfo.resolveHostIp(mode);
        String pass = txtPassphrase != null ? new String(txtPassphrase.getPassword()).trim() : "";
        roomService.createSession(mode, ip, pass.isEmpty() ? null : pass);
    }

    private void saveSnapshotAction() {
        try {
            SnapshotService snapshotService = new SnapshotService(roomService, fileService, clipboardService);
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
            SnapshotService snapshotService = new SnapshotService(roomService, fileService, clipboardService);
            boolean ok = snapshotService.restoreDefault();
            if (ok) {
                refreshDisplayFromSession();
                JOptionPane.showMessageDialog(this, "Session state restored from session.ser", "Snapshot Restored", JOptionPane.INFORMATION_MESSAGE);
                log("Session state restored from session.ser");
            } else {
                JOptionPane.showMessageDialog(this, "No valid session snapshot found on disk.", "Restore Notice", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to restore snapshot: " + ex.getMessage(), "Snapshot Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmEndSession() {
        int opt = JOptionPane.showConfirmDialog(
            this,
            "End this classroom session?\nAll active tokens and sharing will stop.",
            "End ClassDrop Session",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (opt == JOptionPane.YES_OPTION) {
            if (tunnelService != null) {
                tunnelService.stopInternetShare();
            }
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
            if (dropFolderWatcher != null) {
                dropFolderWatcher.stop();
            }
            if (tunnelService != null) {
                tunnelService.stopInternetShare();
            }
            roomService.endSession();
            dispose();
            System.exit(0);
        }
    }

    private void shareLocalFile() {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null || !session.isActive()) {
            JOptionPane.showMessageDialog(this, "Start an active session before sharing a file.", "No Session", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Share local files with the classroom");
        chooser.setFileFilter(new FileNameExtensionFilter(
            "Classroom files (pdf, images, docs, code, zip)",
            "pdf", "png", "jpg", "jpeg", "gif", "webp", "doc", "docx", "ppt", "pptx",
            "xls", "xlsx", "txt", "csv", "json", "java", "py", "sql", "html", "css", "zip"
        ));
        chooser.setAcceptAllFileFilterUsed(true);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;
        shareFiles(List.of(chooser.getSelectedFiles()));
    }

    private void shareFiles(List<File> files) {
        RoomSession session = roomService.getCurrentSessionOrNull();
        if (session == null || !session.isActive()) {
            JOptionPane.showMessageDialog(this, "Start an active session before sharing a file.", "No Session", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (files == null || files.isEmpty()) return;

        List<String> okLines = new ArrayList<>();
        List<String> failLines = new ArrayList<>();
        for (File file : files) {
            if (file == null || !file.isFile()) {
                if (file != null) failLines.add(file.getName() + " (not a file)");
                continue;
            }
            try {
                long size = Files.size(file.toPath());
                SharedItem item = fileService.send(file.toPath());
                okLines.add(item.getOriginalName() + "  ·  " + item.getKind() + "  ·  " + formatBytes(item.getSizeBytes()));
                log("Host shared: " + item.getOriginalName() + " [" + formatBytes(size) + "]");
            } catch (Exception ex) {
                failLines.add(file.getName() + " — " + ex.getMessage());
            }
        }
        updateStatsDisplay();
        updateEmptyStates();

        StringBuilder msg = new StringBuilder();
        if (!okLines.isEmpty()) {
            msg.append("Shared successfully:\n");
            for (String line : okLines) msg.append("  • ").append(line).append('\n');
        }
        if (!failLines.isEmpty()) {
            if (msg.length() > 0) msg.append('\n');
            msg.append("Failed:\n");
            for (String line : failLines) msg.append("  • ").append(line).append('\n');
        }
        if (msg.length() > 0) {
            JOptionPane.showMessageDialog(this, msg.toString().trim(),
                okLines.isEmpty() ? "Share Failed" : "Files Shared",
                okLines.isEmpty() ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void removeSelectedFile() {
        int row = tblTransfers != null ? tblTransfers.getSelectedRow() : -1;
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a transfer/file row to remove.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String fileId = transferTableModel.getTransferIdAt(row);
        String name = transferTableModel.getFilenameAt(row);
        try {
            RoomSession session = roomService.getCurrentSessionOrNull();
            if (session != null) {
                for (SharedItem item : session.getItems()) {
                    if (item.getOriginalName().equals(name)) {
                        fileId = item.getId();
                        break;
                    }
                }
            }
            fileService.removeFile(fileId);
            transferTableModel.removeRow(row);
            log("Host removed file: " + name);
            updateStatsDisplay();
            updateEmptyStates();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not remove file:\n" + ex.getMessage(),
                "Remove Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void kickSelectedDevice() {
        int row = tblDevices != null ? tblDevices.getSelectedRow() : -1;
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a connected device to kick.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Device device = deviceTableModel.getDeviceAt(row);
        if (device == null) return;
        int opt = JOptionPane.showConfirmDialog(this,
            "Kick " + device.getDisplayName() + " from the room?\nTheir token will stop working.",
            "Kick Device", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opt != JOptionPane.YES_OPTION) return;
        try {
            roomService.kickDevice(device.getId());
            log("Host kicked device: " + device.getDisplayName());
            updateStatsDisplay();
            updateEmptyStates();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Kick failed:\n" + ex.getMessage(),
                "Kick Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyShareUrl() {
        String url = txtShareUrl.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        JOptionPane.showMessageDialog(this, "Copied share link:\n" + url, "Link Copied", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openWhatsappLink() {
        openBrowserUri(ShareLinks.whatsappUrl(txtShareUrl.getText()));
    }

    private void openEmailLink() {
        openBrowserUri(ShareLinks.emailUrl(txtShareUrl.getText()));
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
                lblSessionStats.setText(String.format("Session Transfers: %d (up %d / down %d) | %s",
                    stats.transferCount(), stats.uploadCount(), stats.downloadCount(), formatBytes(stats.totalBytes())));
                return;
            }
        }
        int count = transferTableModel.getTransferCount();
        long bytes = transferTableModel.getTotalTransferredBytes();
        lblSessionStats.setText(String.format("Session Transfers: %d | Transferred: %s", count, formatBytes(bytes)));
    }

    private void log(String msg) {
        if (txtLogArea == null) return;
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

    @Override
    public void onDeviceJoined(Device device) {
        SwingUtilities.invokeLater(() -> {
            deviceTableModel.addDevice(device);
            updateStatsDisplay();
            updateEmptyStates();
            String ip = device.getIpAddress() != null ? device.getIpAddress() : "";
            boolean remote = !ip.isBlank() && !ip.startsWith("127.") && !ip.equals("::1");
            log((remote ? "★ Student path LIVE — " : "Device joined: ")
                + device.getDisplayName() + " (" + ip + ")");
            if (remote) {
                Toolkit.getDefaultToolkit().beep();
            }
        });
    }

    @Override
    public void onDeviceLeft(Device device) {
        SwingUtilities.invokeLater(() -> {
            deviceTableModel.removeDevice(device.getId());
            updateStatsDisplay();
            updateEmptyStates();
            log("Device left: " + device.getDisplayName());
        });
    }

    @Override
    public void onTransferStarted(TransferRecord record) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.addOrUpdate(record);
            updateStatsDisplay();
            updateEmptyStates();
            log("Transfer started: " + record.getOriginalName() + " (" + record.getDirection() + ")");
        });
    }

    @Override
    public void onTransferProgress(String transferId, long bytesTransferred, long totalBytes) {
        SwingUtilities.invokeLater(() -> transferTableModel.updateProgress(transferId, bytesTransferred, totalBytes));
    }

    @Override
    public void onTransferCompleted(TransferRecord record) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.addOrUpdate(record);
            updateStatsDisplay();
            updateEmptyStates();
            if (searchPanel != null) searchPanel.doSearch();
            log("Transfer complete: " + record.getOriginalName() + " [" + formatBytes(record.getSizeBytes()) + "]");
        });
    }

    @Override
    public void onTransferFailed(TransferRecord record, String reason) {
        SwingUtilities.invokeLater(() -> {
            transferTableModel.addOrUpdate(record);
            updateStatsDisplay();
            updateEmptyStates();
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
