package com.classdrop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Modern rounded drop target for host file sharing. */
public final class DropZonePanel extends JPanel {
    private final JLabel icon;
    private final JLabel title;
    private final JLabel subtitle;
    private boolean active;
    private Consumer<List<File>> onFilesDropped;

    public DropZonePanel() {
        setLayout(new GridBagLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(100, 128));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        icon = new JLabel("⬆", SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 28));
        icon.setForeground(UiTheme.ACCENT_SOFT);

        title = new JLabel("Drop files to share with the class", SwingConstants.CENTER);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setForeground(UiTheme.TEXT);
        title.setFont(UiTheme.UI_BOLD);

        subtitle = new JLabel("PDF · images · notes · code · zip  ·  click to browse  ·  max 100 MB", SwingConstants.CENTER);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setForeground(UiTheme.MUTED);
        subtitle.setFont(UiTheme.UI_SMALL);

        inner.add(icon);
        inner.add(Box.createVerticalStrut(8));
        inner.add(title);
        inner.add(Box.createVerticalStrut(4));
        inner.add(subtitle);
        add(inner);

        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    setActive(true);
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                setActive(false);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                setActive(false);
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object data = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (data instanceof List<?> list && onFilesDropped != null) {
                        onFilesDropped.accept((List<File>) list);
                    }
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    public void setOnFilesDropped(Consumer<List<File>> onFilesDropped) {
        this.onFilesDropped = onFilesDropped;
    }

    public void setActive(boolean active) {
        this.active = active;
        title.setText(active ? "Release to share" : "Drop files to share with the class");
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int arc = 18;
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);

        g2.setColor(active ? new Color(15, 158, 145, 55) : new Color(15, 158, 145, 22));
        g2.fill(shape);

        float[] dash = {8f, 6f};
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, dash, 0));
        g2.setColor(active ? UiTheme.ACCENT_SOFT : UiTheme.ACCENT);
        g2.draw(shape);
        g2.dispose();
        super.paintComponent(g);
    }
}
