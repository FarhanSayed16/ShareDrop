package com.classdrop.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/** Applies a modern FlatLaf dark theme before any Swing window opens. */
public final class UiLookAndFeel {
    private UiLookAndFeel() {}

    public static void install() {
        try {
            FlatDarkLaf.setup();

            UIManager.put("Component.arc", 14);
            UIManager.put("Button.arc", 14);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("ScrollBar.width", 12);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.tabHeight", 38);
            UIManager.put("TabbedPane.tabArc", 10);
            UIManager.put("TabbedPane.selectedBackground", UiTheme.SURFACE_ALT);
            UIManager.put("TabbedPane.hoverColor", new Color(51, 65, 85));
            UIManager.put("TabbedPane.underlineColor", UiTheme.ACCENT);
            UIManager.put("TabbedPane.tabAreaBackground", UiTheme.BG);
            UIManager.put("Panel.background", UiTheme.BG);
            UIManager.put("Viewport.background", UiTheme.SURFACE);
            UIManager.put("Table.background", UiTheme.SURFACE);
            UIManager.put("Table.foreground", UiTheme.TEXT);
            UIManager.put("Table.selectionBackground", new Color(13, 148, 136, 90));
            UIManager.put("Table.selectionForeground", UiTheme.TEXT);
            UIManager.put("Table.gridColor", new Color(45, 55, 72));
            UIManager.put("TableHeader.background", UiTheme.SURFACE_ALT);
            UIManager.put("TableHeader.foreground", UiTheme.TEXT);
            UIManager.put("ScrollPane.background", UiTheme.SURFACE);
            UIManager.put("Label.foreground", UiTheme.TEXT);
            UIManager.put("TextField.background", UiTheme.SURFACE_ALT);
            UIManager.put("TextField.foreground", UiTheme.TEXT);
            UIManager.put("ComboBox.background", UiTheme.SURFACE_ALT);
            UIManager.put("ComboBox.foreground", UiTheme.TEXT);
            UIManager.put("Button.font", new Font("Segoe UI Semibold", Font.PLAIN, 13));
            UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("TitlePane.unifiedBackground", true);
        } catch (Exception e) {
            System.err.println("[ClassDrop] FlatLaf unavailable, using system L&F: " + e.getMessage());
        }
    }
}
