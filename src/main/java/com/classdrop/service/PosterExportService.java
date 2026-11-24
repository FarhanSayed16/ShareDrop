package com.classdrop.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Renders an offline projector poster (room code + URL + QR) as PNG. */
public final class PosterExportService {
    private final QrService qrService;

    public PosterExportService(QrService qrService) {
        this.qrService = qrService;
    }

    public Path exportPng(Path target, String roomCode, String shareUrl, String ssid, String extraTip)
            throws IOException {
        int w = 1080;
        int h = 1520;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(15, 23, 42));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(13, 148, 136));
        g.fillRect(0, 0, w, 18);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 72));
        g.drawString("ClassDrop", 64, 120);

        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("SansSerif", Font.PLAIN, 28));
        g.drawString("Offline classroom hub — no internet needed", 64, 170);

        g.setColor(new Color(45, 212, 191));
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        g.drawString("ROOM CODE", 64, 280);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 120));
        g.drawString(roomCode != null ? roomCode : "------", 64, 420);

        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("SansSerif", Font.PLAIN, 26));
        int y = 500;
        g.drawString("1. Join teacher hotspot Wi-Fi", 64, y); y += 40;
        if (ssid != null && !ssid.isBlank()) {
            g.setColor(Color.WHITE);
            g.drawString("   SSID: " + ssid, 64, y); y += 40;
            g.setColor(new Color(148, 163, 184));
        }
        g.drawString("2. Turn OFF mobile data", 64, y); y += 40;
        g.drawString("3. Scan QR or open:", 64, y); y += 40;
        g.setColor(new Color(125, 211, 252));
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        drawWrapped(g, shareUrl != null ? shareUrl : "", 64, y, w - 128, 28);
        y += 90;

        if (shareUrl != null && !shareUrl.isBlank()) {
            BufferedImage qr = qrService.generateQr(shareUrl, 420);
            int qx = (w - qr.getWidth()) / 2;
            g.setColor(Color.WHITE);
            g.fillRoundRect(qx - 16, y - 16, qr.getWidth() + 32, qr.getHeight() + 32, 24, 24);
            g.drawImage(qr, qx, y, null);
            y += qr.getHeight() + 60;
        }

        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        String tip = extraTip != null ? extraTip : "Stay on hotspot even if phone says No Internet.";
        drawWrapped(g, tip, 64, y, w - 128, 28);

        g.dispose();
        Files.createDirectories(target.getParent() != null ? target.getParent() : Path.of("."));
        ImageIO.write(img, "png", target.toFile());
        return target;
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineH) {
        if (text == null) return;
        Font font = g.getFont();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics(font).stringWidth(trial) > maxWidth && line.length() > 0) {
                g.drawString(line.toString(), x, y);
                y += lineH;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) g.drawString(line.toString(), x, y);
    }
}
