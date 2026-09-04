package com.biasharaai.desktop.v2;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class DesktopTheme {
    static final Color CANVAS = new Color(243, 246, 251);
    static final Color SURFACE = Color.WHITE;
    static final Color SURFACE_ALT = new Color(248, 250, 252);
    static final Color BORDER = new Color(218, 226, 237);
    static final Color INK = new Color(13, 23, 42);
    static final Color MUTED = new Color(71, 85, 105);
    static final Color SIDEBAR = new Color(9, 18, 33);
    static final Color SIDEBAR_SOFT = new Color(19, 32, 54);
    static final Color PRIMARY = new Color(37, 99, 235);
    static final Color PRIMARY_DARK = new Color(29, 78, 216);
    static final Color CYAN = new Color(8, 145, 178);
    static final Color TEAL = new Color(20, 184, 166);
    static final Color GREEN = new Color(22, 163, 74);
    static final Color AMBER = new Color(217, 119, 6);
    static final Color RED = new Color(220, 38, 38);
    static final Color WHATSAPP = new Color(22, 163, 74);

    private DesktopTheme() {
    }

    static Font font(int style, float size) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static JButton primaryButton(String text) {
        return new ModernButton(text, PRIMARY, Color.WHITE, new Color(30, 64, 175));
    }

    static JButton secondaryButton(String text) {
        return new ModernButton(text, SURFACE, INK, new Color(239, 246, 255));
    }

    static NavButton navButton(String label, String hint) {
        return new NavButton(label, hint);
    }

    static JPanel card() {
        return new RoundPanel(SURFACE, BORDER, 18);
    }

    static JPanel softPanel() {
        return new RoundPanel(SURFACE_ALT, BORDER, 16);
    }

    static Border line(Color color) {
        return BorderFactory.createLineBorder(color, 1);
    }

    static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static void input(JTextField field) {
        field.setFont(font(Font.PLAIN, 13f));
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setCaretColor(PRIMARY_DARK);
        field.setBorder(BorderFactory.createCompoundBorder(line(BORDER), pad(8, 10, 8, 10)));
    }

    static void table(JTable table) {
        table.setFont(font(Font.PLAIN, 13f));
        table.setForeground(INK);
        table.setRowHeight(38);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(INK);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(font(Font.BOLD, 12f));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(SURFACE_ALT);
        table.getTableHeader().setBorder(line(BORDER));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                component.setFont(font(Font.PLAIN, 13f));
                component.setForeground(INK);
                component.setBackground(isSelected ? new Color(219, 234, 254) : row % 2 == 0 ? Color.WHITE : new Color(250, 252, 255));
                setBorder(pad(0, 10, 0, 10));
                return component;
            }
        });
    }

    static JTextArea textArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setFont(font(Font.PLAIN, 13f));
        area.setForeground(INK);
        area.setBackground(Color.WHITE);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(pad(10, 10, 10, 10));
        return area;
    }

    static GridBagConstraints gbc(int x, int y, int width, double weightX) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.weightx = weightX;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 6, 6, 6);
        return c;
    }

    static void named(JComponent component, String name) {
        component.putClientProperty("AccessibleName", name);
    }
}

final class ModernButton extends JButton {
    private final Color base;
    private final Color foreground;
    private final Color hoverColor;
    private boolean hover;

    ModernButton(String text, Color base, Color foreground, Color hoverColor) {
        super(text);
        this.base = base;
        this.foreground = foreground;
        this.hoverColor = hoverColor;
        setFont(DesktopTheme.font(Font.BOLD, 13f));
        setForeground(foreground);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(DesktopTheme.pad(10, 16, 10, 16));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(size.width + 16, 104), Math.max(size.height + 8, 42));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        Color fill = isEnabled() ? (hover ? hoverColor : base) : new Color(226, 232, 240);
        g2.setColor(new Color(15, 23, 42, isEnabled() ? 22 : 8));
        g2.fillRoundRect(1, 3, w - 3, h - 4, 14, 14);
        g2.setPaint(new GradientPaint(0, 0, fill.brighter(), 0, h, fill));
        g2.fillRoundRect(0, 0, w - 3, h - 5, 14, 14);
        g2.setColor(fill.equals(Color.WHITE) || fill.equals(DesktopTheme.SURFACE) ? DesktopTheme.BORDER : new Color(255, 255, 255, 90));
        g2.drawRoundRect(0, 0, w - 4, h - 6, 14, 14);
        g2.setColor(isEnabled() ? foreground : DesktopTheme.MUTED);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int x = (w - fm.stringWidth(getText())) / 2;
        int y = ((h - fm.getHeight()) / 2) + fm.getAscent() - 2;
        g2.drawString(getText(), Math.max(12, x), y);
        g2.dispose();
    }
}

final class NavButton extends JButton {
    private final String title;
    private final String hint;
    private boolean active;
    private boolean hover;

    NavButton(String title, String hint) {
        this.title = title;
        this.hint = hint;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(DesktopTheme.pad(0, 0, 0, 0));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        setPreferredSize(new Dimension(208, 64));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    void setActive(boolean active) {
        this.active = active;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        Color fill = active ? DesktopTheme.PRIMARY : hover ? new Color(21, 36, 61) : DesktopTheme.SIDEBAR;
        if (active) {
            g2.setPaint(new LinearGradientPaint(0, 0, w, h, new float[]{0f, 1f}, new Color[]{new Color(59, 130, 246), new Color(14, 165, 233)}));
        } else {
            g2.setColor(fill);
        }
        g2.fillRoundRect(0, 3, w - 2, h - 6, 18, 18);
        g2.setFont(DesktopTheme.font(Font.BOLD, 13f));
        g2.setColor(Color.WHITE);
        g2.drawString(title, 20, 27);
        g2.setFont(DesktopTheme.font(Font.PLAIN, 11f));
        g2.setColor(active ? new Color(235, 245, 255) : new Color(178, 190, 210));
        g2.drawString(hint, 20, 44);
        g2.dispose();
    }
}

final class RoundPanel extends JPanel {
    private final Color fill;
    private final Color stroke;
    private final int radius;

    RoundPanel(Color fill, Color stroke, int radius) {
        this.fill = fill;
        this.stroke = stroke;
        this.radius = radius;
        setOpaque(false);
        setBorder(DesktopTheme.pad(18, 18, 18, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(new Color(15, 23, 42, 14));
        g2.fillRoundRect(2, 5, w - 5, h - 7, radius, radius);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 5, h - 6, radius, radius);
        g2.setColor(stroke);
        g2.drawRoundRect(0, 0, w - 6, h - 7, radius, radius);
        g2.dispose();
        super.paintComponent(g);
    }
}
