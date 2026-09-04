package com.biasharaai.desktop.v2;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;

final class ProductCard extends JPanel {
    ProductCard(Product product, String currency, Runnable action) {
        super(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        add(new ProductImage(product.imagePath, product.name), BorderLayout.NORTH);

        JPanel copy = new JPanel(new BorderLayout(0, 4));
        copy.setOpaque(false);
        JLabel name = DesktopTheme.label(product.name, Font.BOLD, 14f, DesktopTheme.INK);
        JLabel meta = DesktopTheme.label(product.category.isBlank() ? "Uncategorized" : product.category, Font.PLAIN, 12f, DesktopTheme.MUTED);
        JLabel price = DesktopTheme.label(Money.format(product.priceCents, currency), Font.BOLD, 15f, DesktopTheme.PRIMARY_DARK);
        JLabel stock = DesktopTheme.label(product.stock + " in stock", Font.BOLD, 12f, product.stock <= 5 ? DesktopTheme.AMBER : DesktopTheme.GREEN);
        copy.add(name, BorderLayout.NORTH);
        copy.add(meta, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout());
        lower.setOpaque(false);
        lower.add(price, BorderLayout.WEST);
        lower.add(stock, BorderLayout.EAST);
        copy.add(lower, BorderLayout.SOUTH);
        add(copy, BorderLayout.CENTER);

        JButton button = DesktopTheme.secondaryButton(action == null ? "View" : "Add to cart");
        button.addActionListener(e -> {
            if (action != null) {
                action.run();
            }
        });
        add(button, BorderLayout.SOUTH);
    }
}

final class ServiceCard extends JPanel {
    ServiceCard(ServiceItem service, String currency, Runnable action) {
        super(new BorderLayout(0, 10));
        setOpaque(false);
        add(new ServiceSurface(service), BorderLayout.NORTH);
        JPanel copy = new JPanel(new BorderLayout(0, 4));
        copy.setOpaque(false);
        copy.add(DesktopTheme.label(service.name, Font.BOLD, 14f, DesktopTheme.INK), BorderLayout.NORTH);
        copy.add(DesktopTheme.label(service.category.isBlank() ? "Service" : service.category, Font.PLAIN, 12f, DesktopTheme.MUTED), BorderLayout.CENTER);
        copy.add(DesktopTheme.label(Money.format(service.priceCents, currency) + " - " + service.durationMinutes + " min", Font.BOLD, 13f, DesktopTheme.PRIMARY_DARK), BorderLayout.SOUTH);
        add(copy, BorderLayout.CENTER);
        JButton button = DesktopTheme.secondaryButton("Add service");
        button.addActionListener(e -> {
            if (action != null) {
                action.run();
            }
        });
        add(button, BorderLayout.SOUTH);
    }
}

final class ProductImage extends JComponent {
    private final String imagePath;
    private final String name;
    private Image image;

    ProductImage(String imagePath, String name) {
        this.imagePath = imagePath == null ? "" : imagePath;
        this.name = name == null ? "" : name;
        setPreferredSize(new Dimension(190, 138));
        if (!this.imagePath.isBlank()) {
            try {
                image = ImageIO.read(new File(this.imagePath));
            } catch (Exception ignored) {
                image = null;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 16, 16);
        g2.setColor(new Color(241, 245, 249));
        g2.fill(shape);
        if (image != null) {
            g2.setClip(shape);
            int iw = image.getWidth(this);
            int ih = image.getHeight(this);
            if (iw > 0 && ih > 0) {
                double scale = Math.max(w / (double) iw, h / (double) ih);
                int dw = (int) Math.ceil(iw * scale);
                int dh = (int) Math.ceil(ih * scale);
                int dx = (w - dw) / 2;
                int dy = (h - dh) / 2;
                g2.drawImage(image, dx, dy, dw, dh, this);
            }
            g2.setClip(null);
        } else {
            g2.setColor(DesktopTheme.MUTED);
            g2.setFont(DesktopTheme.font(Font.BOLD, 13f));
            String label = imagePath.isBlank() ? "No product image" : "Image unavailable";
            int tw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, Math.max(12, (w - tw) / 2), h / 2);
            if (!name.isBlank()) {
                g2.setFont(DesktopTheme.font(Font.PLAIN, 12f));
                String truncated = name.length() > 24 ? name.substring(0, 21) + "..." : name;
                int nw = g2.getFontMetrics().stringWidth(truncated);
                g2.drawString(truncated, Math.max(12, (w - nw) / 2), h / 2 + 20);
            }
        }
        g2.setColor(DesktopTheme.BORDER);
        g2.draw(shape);
        g2.dispose();
    }
}

final class ServiceSurface extends JComponent {
    private final ServiceItem service;

    ServiceSurface(ServiceItem service) {
        this.service = service;
        setPreferredSize(new Dimension(190, 138));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(new Color(236, 253, 245));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 16, 16);
        g2.setColor(new Color(187, 247, 208));
        g2.fillRoundRect(16, 18, w - 32, h - 36, 14, 14);
        g2.setColor(DesktopTheme.GREEN);
        g2.setFont(DesktopTheme.font(Font.BOLD, 18f));
        String title = "SERVICE";
        int tw = g2.getFontMetrics().stringWidth(title);
        g2.drawString(title, (w - tw) / 2, h / 2 - 5);
        g2.setFont(DesktopTheme.font(Font.PLAIN, 12f));
        String meta = service.durationMinutes + " min";
        int mw = g2.getFontMetrics().stringWidth(meta);
        g2.drawString(meta, (w - mw) / 2, h / 2 + 18);
        g2.setColor(DesktopTheme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 16, 16);
        g2.dispose();
    }
}
