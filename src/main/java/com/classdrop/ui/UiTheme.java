package com.classdrop.ui;

import java.awt.Color;
import java.awt.Font;

/** ClassDrop visual system — slate + teal, modern host dashboard. */
public final class UiTheme {
    private UiTheme() {}

    public static final Color BG = new Color(11, 18, 32);
    public static final Color SURFACE = new Color(22, 32, 48);
    public static final Color SURFACE_ALT = new Color(34, 48, 68);
    public static final Color BORDER = new Color(58, 74, 98);
    public static final Color TEXT = new Color(241, 245, 249);
    public static final Color MUTED = new Color(148, 163, 184);
    public static final Color ACCENT = new Color(15, 158, 145);
    public static final Color ACCENT_SOFT = new Color(94, 234, 212);
    public static final Color ACCENT_DIM = new Color(15, 158, 145, 40);
    public static final Color DANGER = new Color(244, 63, 94);
    public static final Color WARN = new Color(251, 146, 60);
    public static final Color OK = new Color(34, 197, 94);
    public static final Color INFO = new Color(56, 189, 248);

    public static final Font UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font UI_BOLD = new Font("Segoe UI Semibold", Font.BOLD, 13);
    public static final Font UI_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font UI_SMALL_BOLD = new Font("Segoe UI Semibold", Font.BOLD, 12);
    public static final Font BRAND = new Font("Segoe UI Semibold", Font.BOLD, 24);
    public static final Font CODE = new Font("Consolas", Font.BOLD, 42);
    public static final Font MONO = new Font("Consolas", Font.PLAIN, 12);
}
