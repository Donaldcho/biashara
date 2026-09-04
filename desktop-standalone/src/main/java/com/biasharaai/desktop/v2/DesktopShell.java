package com.biasharaai.desktop.v2;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class DesktopShell implements PhoneBridgeServer.Listener {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final DesktopStore store;
    private final AppState state;
    private final AssistantAdvisor advisor = new AssistantAdvisor();
    private final PhoneBridgeServer phoneBridge;
    private final JFrame frame = new JFrame("Biashara AI Pro Desktop");
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, NavButton> navigation = new LinkedHashMap<>();
    private final List<Refreshable> refreshables = new ArrayList<>();
    private final JLabel screenTitle = DesktopTheme.label("Today", Font.BOLD, 22f, DesktopTheme.INK);
    private final JLabel screenSubtitle = DesktopTheme.label("", Font.PLAIN, 13f, DesktopTheme.MUTED);
    private final JLabel bridgeStatus = DesktopTheme.label("", Font.BOLD, 12f, DesktopTheme.MUTED);
    private final PosPanel posPanel;
    private String activeScreen = "Today";

    DesktopShell(DesktopStore store, AppState state) {
        this.store = store;
        this.state = state;
        this.phoneBridge = new PhoneBridgeServer(8765, this);
        this.posPanel = new PosPanel();
        buildFrame();
        if (state.settings.phoneBridgeEnabled) {
            startBridgeQuietly();
        }
        refreshAll();
    }

    void show() {
        frame.setVisible(true);
    }

    @Override
    public void onPhoneBridgeScan(ScanEvent event) {
        SwingUtilities.invokeLater(() -> {
            state.scanEvents.add(0, event);
            applyIncomingScan(event);
            persist();
            refreshAll();
        });
    }

    @Override
    public void onPhoneStockIntake(StockSyncItem item) {
        SwingUtilities.invokeLater(() -> {
            applyStockIntake(item);
            state.stockSyncItems.add(0, item);
            persist();
            refreshAll();
        });
    }

    @Override
    public void onPhoneProductSync(ProductSyncItem item) {
        SwingUtilities.invokeLater(() -> {
            applyProductSync(item);
            state.productSyncItems.add(0, item);
            persist();
            refreshAll();
        });
    }

    @Override
    public void onPhoneBridgeChanged() {
        SwingUtilities.invokeLater(this::refreshAll);
    }

    private void buildFrame() {
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1120, 720));
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(DesktopTheme.CANVAS);
        root.add(sidebar(), BorderLayout.WEST);
        root.add(mainArea(), BorderLayout.CENTER);
        frame.setContentPane(root);
    }

    private JPanel sidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setPreferredSize(new Dimension(284, 0));
        sidebar.setBackground(DesktopTheme.SIDEBAR);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(22, 18, 22, 18));

        JLabel brand = DesktopTheme.label("Biashara AI", Font.BOLD, 24f, Color.WHITE);
        JLabel edition = DesktopTheme.label("Pro Desktop", Font.PLAIN, 13f, new Color(203, 213, 225));
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(edition);
        sidebar.add(Box.createVerticalStrut(24));

        addNav(sidebar, "Today", "Daily command center");
        addNav(sidebar, "POS", "Sell products and services");
        addNav(sidebar, "Inventory", "Products and services");
        addNav(sidebar, "Ledger", "Cash flow and records");
        addNav(sidebar, "Customers", "Credit and visits");
        addNav(sidebar, "Assistant", "Business questions");
        addNav(sidebar, "Phone Link", "Mobile scanner bridge");
        addNav(sidebar, "WhatsApp", "Catalog sharing");
        addNav(sidebar, "AI Model", "Desktop model");
        addNav(sidebar, "Settings", "Business setup");

        sidebar.add(Box.createVerticalGlue());
        JPanel syncPill = new RoundPanel(DesktopTheme.SIDEBAR_SOFT, new Color(45, 62, 89), 18);
        syncPill.setLayout(new BorderLayout());
        syncPill.setBorder(DesktopTheme.pad(12, 12, 12, 12));
        JLabel small = DesktopTheme.label("Local-first workspace", Font.BOLD, 12f, new Color(226, 232, 240));
        JLabel sub = DesktopTheme.label("Data stays on this computer", Font.PLAIN, 12f, new Color(148, 163, 184));
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.add(small);
        wrap.add(Box.createVerticalStrut(3));
        wrap.add(sub);
        syncPill.add(wrap);
        sidebar.add(syncPill);
        return sidebar;
    }

    private void addNav(JPanel sidebar, String label, String hint) {
        NavButton button = DesktopTheme.navButton(label, hint);
        button.addActionListener(e -> navigate(label));
        navigation.put(label, button);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(6));
    }

    private JPanel mainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(DesktopTheme.CANVAS);
        main.add(topbar(), BorderLayout.NORTH);

        content.setBackground(DesktopTheme.CANVAS);
        addScreen("Today", new DashboardPanel());
        addScreen("POS", posPanel);
        addScreen("Inventory", new InventoryPanel());
        addScreen("Ledger", new LedgerPanel());
        addScreen("Customers", new CustomersPanel());
        addScreen("Assistant", new AssistantPanel());
        addScreen("Phone Link", new PhoneLinkPanel());
        addScreen("WhatsApp", new WhatsAppPanel());
        addScreen("AI Model", new ModelPanel());
        addScreen("Settings", new SettingsPanel());
        main.add(content, BorderLayout.CENTER);
        navigate("Today");
        return main;
    }

    private JPanel topbar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(DesktopTheme.SURFACE);
        top.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopTheme.BORDER),
            DesktopTheme.pad(16, 24, 16, 24)
        ));

        JPanel titleWrap = new JPanel();
        titleWrap.setOpaque(false);
        titleWrap.setLayout(new BoxLayout(titleWrap, BoxLayout.Y_AXIS));
        titleWrap.add(screenTitle);
        titleWrap.add(Box.createVerticalStrut(4));
        titleWrap.add(screenSubtitle);
        top.add(titleWrap, BorderLayout.CENTER);

        JPanel status = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        status.setOpaque(false);
        bridgeStatus.setBorder(BorderFactory.createCompoundBorder(DesktopTheme.line(DesktopTheme.BORDER), DesktopTheme.pad(8, 12, 8, 12)));
        status.add(bridgeStatus);
        javax.swing.JButton review = DesktopTheme.secondaryButton("Run review");
        review.addActionListener(e -> {
            navigate("Assistant");
            refreshAll();
        });
        status.add(review);
        top.add(status, BorderLayout.EAST);
        return top;
    }

    private void addScreen(String name, JPanel panel) {
        panel.setBorder(DesktopTheme.pad(20, 24, 24, 24));
        panel.setBackground(DesktopTheme.CANVAS);
        polishControls(panel);
        content.add(panel, name);
        if (panel instanceof Refreshable refreshable) {
            refreshables.add(refreshable);
        }
    }

    private void polishControls(java.awt.Component component) {
        if (component instanceof JTextField field) {
            DesktopTheme.input(field);
        } else if (component instanceof JComboBox<?> combo) {
            combo.setFont(DesktopTheme.font(Font.PLAIN, 13f));
            combo.setForeground(DesktopTheme.INK);
            combo.setBackground(Color.WHITE);
            combo.setBorder(DesktopTheme.line(DesktopTheme.BORDER));
        } else if (component instanceof JProgressBar progressBar) {
            progressBar.setFont(DesktopTheme.font(Font.BOLD, 12f));
            progressBar.setForeground(DesktopTheme.PRIMARY);
            progressBar.setBackground(new Color(226, 232, 240));
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                polishControls(child);
            }
        }
    }

    private void navigate(String screen) {
        activeScreen = screen;
        cards.show(content, screen);
        screenTitle.setText(screen);
        screenSubtitle.setText(subtitleFor(screen));
        navigation.forEach((name, button) -> {
            boolean active = name.equals(screen);
            button.setActive(active);
        });
        refreshAll();
    }

    private String subtitleFor(String screen) {
        return switch (screen) {
            case "Today" -> state.settings.businessName + " - " + LocalDate.now();
            case "POS" -> "Fast product, service, mixed-cart, and phone-scan sales";
            case "Inventory" -> "Manage products, stock, service pricing, duration, and warranty";
            case "Ledger" -> "Offline money record, manual entries, and cash flow";
            case "Customers" -> "Customer credit, repayments, and repeat visits";
            case "Assistant" -> "Local business assistant with desktop model readiness";
            case "Phone Link" -> "Pair phones and receive scanner events on this computer";
            case "WhatsApp" -> "Share product catalogs and prepare WhatsApp Business messages";
            case "AI Model" -> "Install a desktop AI model independently from mobile";
            case "Settings" -> "Business profile, tax, receipt, backup, and local bridge settings";
            default -> "";
        };
    }

    private void refreshAll() {
        PhoneBridgeServer.BridgeStatus status = phoneBridge.status();
        String text = status.running
            ? (!status.pairedDevice.isBlank() ? "Phone linked: " + status.pairedDevice : "Phone bridge ready")
            : "Phone bridge off";
        bridgeStatus.setText(text);
        refreshables.forEach(Refreshable::refresh);
    }

    private void persist() {
        store.save(state);
    }

    private void startBridgeQuietly() {
        try {
            phoneBridge.start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Phone bridge", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void applyIncomingScan(ScanEvent event) {
        if ("POS".equals(activeScreen)) {
            boolean applied = posPanel.applyScan(event.rawValue);
            event.status = applied ? "Applied" : "Received";
            return;
        }
        if ("Product".equals(event.kind) && state.productByBarcode(event.rawValue).isPresent()) {
            event.status = "Received";
            return;
        }
        event.status = "Review";
    }

    private void applyStockIntake(StockSyncItem item) {
        String imagePath = "";
        try {
            imagePath = store.saveIncomingImage(item.imageFileName, item.imageBase64);
        } catch (Exception ex) {
            item.status = "Image failed";
        }
        item.imagePath = imagePath;
        String finalImagePath = imagePath;
        Optional<Product> matched = !item.barcode.isBlank()
            ? state.productByBarcode(item.barcode)
            : state.products.stream().filter(product -> product.name.equalsIgnoreCase(item.productName)).findFirst();
        Product product = matched.orElseGet(() -> {
            Product created = new Product(
                state.nextId("PRD"),
                item.productName.isBlank() ? "Mobile stock item" : item.productName,
                item.barcode.isBlank() ? "" : "MOB-" + item.barcode,
                item.barcode,
                item.category,
                finalImagePath,
                item.barcode,
                item.priceCents,
                item.costCents,
                0
            );
            state.products.add(created);
            return created;
        });
        product.stock += item.quantity;
        if (!item.productName.isBlank()) {
            product.name = item.productName;
        }
        if (!item.category.isBlank()) {
            product.category = item.category;
        }
        if (item.priceCents > 0) {
            product.priceCents = item.priceCents;
        }
        if (item.costCents > 0) {
            product.costCents = item.costCents;
        }
        if (!imagePath.isBlank()) {
            product.imagePath = imagePath;
        }
        if (product.whatsappRetailerId.isBlank()) {
            product.whatsappRetailerId = product.barcode.isBlank() ? product.id : product.barcode;
        }
        item.productId = product.id;
        if (!"Image failed".equals(item.status)) {
            item.status = "Synced";
        }
    }

    private void applyProductSync(ProductSyncItem item) {
        String imagePath = "";
        try {
            imagePath = store.saveIncomingImage(item.imageFileName, item.imageBase64);
        } catch (Exception ex) {
            item.status = "Image failed";
        }
        item.imagePath = imagePath;
        String finalImagePath = imagePath;
        Optional<Product> matched = !item.barcode.isBlank()
            ? state.productByBarcode(item.barcode)
            : state.products.stream().filter(product -> product.name.equalsIgnoreCase(item.name)).findFirst();
        Product product = matched.orElseGet(() -> {
            Product created = new Product(
                state.nextId("PRD"),
                item.name.isBlank() ? "Mobile product" : item.name,
                item.sku,
                item.barcode,
                item.category,
                finalImagePath,
                item.whatsappRetailerId,
                item.priceCents,
                item.costCents,
                item.stock
            );
            state.products.add(created);
            return created;
        });
        if (!item.name.isBlank()) {
            product.name = item.name;
        }
        product.sku = item.sku;
        product.barcode = item.barcode;
        product.category = item.category;
        product.stock = item.stock;
        product.priceCents = item.priceCents;
        product.costCents = item.costCents;
        if (!imagePath.isBlank()) {
            product.imagePath = imagePath;
        }
        if (!item.whatsappRetailerId.isBlank()) {
            product.whatsappRetailerId = item.whatsappRetailerId;
        } else if (product.whatsappRetailerId.isBlank()) {
            product.whatsappRetailerId = product.barcode.isBlank() ? product.id : product.barcode;
        }
        if (!"Image failed".equals(item.status)) {
            item.status = "Synced";
        }
    }

    private JPanel vertical() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private JPanel horizontal() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);
        return panel;
    }

    private JScrollPane scroll(JTable table) {
        DesktopTheme.table(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(DesktopTheme.line(DesktopTheme.BORDER));
        scroll.getViewport().setBackground(DesktopTheme.SURFACE);
        return scroll;
    }

    private JScrollPane scrollPanel(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(DesktopTheme.line(DesktopTheme.BORDER));
        scroll.getViewport().setBackground(DesktopTheme.SURFACE_ALT);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JPanel tile(JPanel inner) {
        JPanel card = DesktopTheme.card();
        card.setLayout(new BorderLayout());
        card.setPreferredSize(new Dimension(250, 296));
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    private JPanel emptyState(String title, String body) {
        JPanel panel = DesktopTheme.softPanel();
        panel.setLayout(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(320, 180));
        panel.add(DesktopTheme.label(title, Font.BOLD, 18f, DesktopTheme.INK), BorderLayout.NORTH);
        JTextArea text = DesktopTheme.textArea(body);
        text.setEditable(false);
        text.setBackground(DesktopTheme.SURFACE_ALT);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Biashara AI Desktop", JOptionPane.ERROR_MESSAGE);
    }

    private interface Refreshable {
        void refresh();
    }

    private final class DashboardPanel extends JPanel implements Refreshable {
        private final JLabel todayRevenue = DesktopTheme.label("", Font.BOLD, 29f, DesktopTheme.INK);
        private final JLabel productCount = DesktopTheme.label("", Font.BOLD, 29f, DesktopTheme.INK);
        private final JLabel credit = DesktopTheme.label("", Font.BOLD, 29f, DesktopTheme.INK);
        private final JLabel lowStock = DesktopTheme.label("", Font.BOLD, 29f, DesktopTheme.INK);
        private final JTextArea review = DesktopTheme.textArea("");
        private final JTextArea stockHealth = DesktopTheme.textArea("");
        private final JTextArea syncHealth = DesktopTheme.textArea("");
        private final JTextArea whatsappHealth = DesktopTheme.textArea("");
        private final JTextArea aiHealth = DesktopTheme.textArea("");

        DashboardPanel() {
            super(new BorderLayout(18, 18));
            setOpaque(false);

            JPanel kpis = new JPanel(new java.awt.GridLayout(1, 4, 16, 16));
            kpis.setOpaque(false);
            kpis.add(kpi("Today revenue", todayRevenue, "Desktop sales"));
            kpis.add(kpi("Catalog items", productCount, "Products and services"));
            kpis.add(kpi("Credit due", credit, "Customer balances"));
            kpis.add(kpi("Stock alerts", lowStock, "Needs attention"));
            add(kpis, BorderLayout.NORTH);

            JPanel center = new JPanel(new java.awt.GridLayout(1, 2, 18, 18));
            center.setOpaque(false);
            center.add(briefCard());
            center.add(stockCard());
            add(center, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new java.awt.GridLayout(1, 3, 16, 16));
            bottom.setOpaque(false);
            bottom.add(statusCard("Mobile stock sync", syncHealth, "Open Phone Link", "Phone Link"));
            bottom.add(statusCard("WhatsApp selling", whatsappHealth, "Prepare catalog", "WhatsApp"));
            bottom.add(statusCard("Desktop AI", aiHealth, "AI Model", "AI Model"));
            add(bottom, BorderLayout.SOUTH);
        }

        private JPanel briefCard() {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 14));
            JPanel title = new JPanel(new BorderLayout(12, 0));
            title.setOpaque(false);
            title.add(DesktopTheme.label("Daily command center", Font.BOLD, 19f, DesktopTheme.INK), BorderLayout.CENTER);
            card.add(title, BorderLayout.NORTH);
            review.setEditable(false);
            review.setRows(8);
            card.add(review, BorderLayout.CENTER);
            JPanel actions = horizontal();
            javax.swing.JButton pos = DesktopTheme.primaryButton("Open POS");
            pos.addActionListener(e -> navigate("POS"));
            javax.swing.JButton supplier = DesktopTheme.secondaryButton("Review stock");
            supplier.addActionListener(e -> navigate("Inventory"));
            actions.add(pos);
            actions.add(supplier);
            card.add(actions, BorderLayout.SOUTH);
            return card;
        }

        private JPanel stockCard() {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 14));
            JPanel title = new JPanel(new BorderLayout(12, 0));
            title.setOpaque(false);
            title.add(DesktopTheme.label("Stock health", Font.BOLD, 19f, DesktopTheme.INK), BorderLayout.CENTER);
            card.add(title, BorderLayout.NORTH);
            stockHealth.setEditable(false);
            stockHealth.setRows(8);
            card.add(stockHealth, BorderLayout.CENTER);
            javax.swing.JButton inventory = DesktopTheme.secondaryButton("Manage inventory");
            inventory.addActionListener(e -> navigate("Inventory"));
            card.add(inventory, BorderLayout.SOUTH);
            return card;
        }

        private JPanel statusCard(String title, JTextArea text, String actionText, String screen) {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 12));
            JPanel top = new JPanel(new BorderLayout(10, 0));
            top.setOpaque(false);
            top.add(DesktopTheme.label(title, Font.BOLD, 16f, DesktopTheme.INK), BorderLayout.CENTER);
            card.add(top, BorderLayout.NORTH);
            text.setEditable(false);
            text.setRows(4);
            card.add(text, BorderLayout.CENTER);
            javax.swing.JButton action = DesktopTheme.secondaryButton(actionText);
            action.addActionListener(e -> navigate(screen));
            card.add(action, BorderLayout.SOUTH);
            return card;
        }

        private JPanel kpi(String title, JLabel value, String caption) {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(14, 0));
            JPanel copy = vertical();
            copy.add(DesktopTheme.label(title, Font.BOLD, 12f, DesktopTheme.MUTED));
            copy.add(Box.createVerticalStrut(8));
            copy.add(value);
            copy.add(Box.createVerticalStrut(6));
            copy.add(DesktopTheme.label(caption, Font.PLAIN, 12f, DesktopTheme.MUTED));
            card.add(copy, BorderLayout.CENTER);
            return card;
        }

        @Override
        public void refresh() {
            todayRevenue.setText(Money.format(state.revenueOn(LocalDate.now()), state.settings.currency));
            productCount.setText(Integer.toString(state.products.size() + state.services.size()));
            credit.setText(Money.format(state.creditOutstanding(), state.settings.currency));
            long low = state.products.stream().filter(product -> product.stock <= 5).count();
            lowStock.setText(Long.toString(low));
            review.setText(advisor.businessReview(state));
            stockHealth.setText(stockHealthText());
            PhoneBridgeServer.BridgeStatus bridge = phoneBridge.status();
            syncHealth.setText((bridge.running ? "Bridge online at " + bridge.host + ":" + bridge.port : "Bridge stopped") + "\n"
                + "Paired phone: " + (bridge.pairedDevice.isBlank() ? "none" : bridge.pairedDevice) + "\n"
                + "Stock items synced: " + state.stockSyncItems.size() + "\n"
                + "Images folder: " + store.incomingImagesDir());
            long readyProducts = state.products.stream().filter(product -> product.stock > 0).count();
            whatsappHealth.setText(readyProducts + " products ready to share\n"
                + "Catalog ID: " + (state.settings.whatsappCatalogId.isBlank() ? "not configured" : state.settings.whatsappCatalogId) + "\n"
                + "Use WhatsApp to prepare product messages.");
            aiHealth.setText((state.settings.modelPath.isBlank() ? "No desktop model installed" : "Desktop model installed") + "\n"
                + "Current assistant mode: local business rules\n"
                + "Data stays offline unless you enable integrations.");
        }

        private String stockHealthText() {
            StringBuilder builder = new StringBuilder();
            state.products.stream()
                .sorted(Comparator.comparingInt(product -> product.stock))
                .limit(6)
                .forEach(product -> builder.append(product.name)
                    .append(" - ")
                    .append(product.stock)
                    .append(" in stock")
                    .append(product.imagePath.isBlank() ? "" : " - image")
                    .append("\n"));
            if (builder.isEmpty()) {
                return "No product stock yet.";
            }
            return builder.toString().trim();
        }
    }

    private final class PosPanel extends JPanel implements Refreshable {
        private final DefaultTableModel catalogModel = new DefaultTableModel(new String[]{"Type", "Name", "Price", "Stock/Time"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        private final DefaultTableModel cartModel = new DefaultTableModel(new String[]{"Item", "Qty", "Unit", "Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1;
            }
        };
        private final JTable catalogTable = new JTable(catalogModel);
        private final JTable cartTable = new JTable(cartModel);
        private final JPanel catalogGrid = new JPanel(new java.awt.GridLayout(0, 3, 14, 14));
        private final JComboBox<String> mode = new JComboBox<>(new String[]{"Both", "Products", "Services"});
        private final JTextField search = new JTextField(18);
        private final JTextField scanner = new JTextField(18);
        private final JTextArea scannerStatus = DesktopTheme.textArea("");
        private final JComboBox<Customer> customer = new JComboBox<>();
        private final JComboBox<String> payment = new JComboBox<>(new String[]{"Cash", "Mobile money", "Split", "Credit", "Deposit"});
        private final JTextField paid = new JTextField(10);
        private final JTextField staff = new JTextField(12);
        private final JLabel subtotal = DesktopTheme.label("", Font.BOLD, 14f, DesktopTheme.INK);
        private final JLabel tax = DesktopTheme.label("", Font.BOLD, 14f, DesktopTheme.INK);
        private final JLabel total = DesktopTheme.label("", Font.BOLD, 22f, DesktopTheme.PRIMARY_DARK);
        private final List<Object> catalogRows = new ArrayList<>();
        private final List<CartLine> cart = new ArrayList<>();
        private long lastSuggestedPaidCents = -1L;

        PosPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);

            JPanel left = DesktopTheme.card();
            left.setLayout(new BorderLayout(0, 12));
            JPanel filters = horizontal();
            filters.add(DesktopTheme.label("Mode", Font.BOLD, 12f, DesktopTheme.MUTED));
            filters.add(mode);
            filters.add(DesktopTheme.label("Search", Font.BOLD, 12f, DesktopTheme.MUTED));
            filters.add(search);
            left.add(filters, BorderLayout.NORTH);
            catalogGrid.setOpaque(false);
            left.add(scrollPanel(catalogGrid), BorderLayout.CENTER);
            search.addActionListener(e -> refreshCatalog());
            mode.addActionListener(e -> refreshCatalog());

            JPanel right = DesktopTheme.card();
            right.setLayout(new BorderLayout(0, 12));
            right.add(scannerPanel(), BorderLayout.NORTH);
            right.add(scroll(cartTable), BorderLayout.CENTER);
            right.add(paymentPanel(), BorderLayout.SOUTH);

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
            split.setResizeWeight(0.55);
            split.setBorder(null);
            add(split, BorderLayout.CENTER);
        }

        private JPanel scannerPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 10));
            panel.setOpaque(false);
            JPanel scannerRow = horizontal();
            scannerRow.add(DesktopTheme.label("Barcode", Font.BOLD, 12f, DesktopTheme.MUTED));
            scannerRow.add(scanner);
            javax.swing.JButton scanButton = DesktopTheme.secondaryButton("Apply scan");
            scanButton.addActionListener(e -> {
                if (applyScan(scanner.getText().trim())) {
                    scanner.setText("");
                }
            });
            scanner.addActionListener(e -> scanButton.doClick());
            scannerRow.add(scanButton);
            javax.swing.JButton phone = DesktopTheme.secondaryButton("Pair phone scanner");
            phone.addActionListener(e -> navigate("Phone Link"));
            scannerRow.add(phone);
            panel.add(scannerRow, BorderLayout.NORTH);
            scannerStatus.setEditable(false);
            scannerStatus.setRows(3);
            panel.add(scannerStatus, BorderLayout.CENTER);
            return panel;
        }

        boolean applyScan(String raw) {
            if (raw == null || raw.isBlank()) {
                return false;
            }
            Optional<ServiceItem> service = state.serviceByToken(raw);
            if (service.isPresent()) {
                addService(service.get());
                return true;
            }
            Optional<Product> product = state.productByBarcode(raw);
            if (product.isPresent()) {
                addProduct(product.get());
                return true;
            }
            showError("No catalog item found for scan: " + raw);
            return false;
        }

        private JPanel paymentPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 12));
            panel.setOpaque(false);
            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            form.add(DesktopTheme.label("Customer", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 0, 1, 0));
            form.add(customer, DesktopTheme.gbc(1, 0, 1, 1));
            form.add(DesktopTheme.label("Payment", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 1, 1, 0));
            form.add(payment, DesktopTheme.gbc(1, 1, 1, 1));
            form.add(DesktopTheme.label("Paid now", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 2, 1, 0));
            form.add(paid, DesktopTheme.gbc(1, 2, 1, 1));
            form.add(DesktopTheme.label("Staff", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 3, 1, 0));
            form.add(staff, DesktopTheme.gbc(1, 3, 1, 1));
            panel.add(form, BorderLayout.NORTH);

            JPanel summary = vertical();
            summary.add(summaryRow("Subtotal", subtotal));
            summary.add(summaryRow("Tax", tax));
            summary.add(summaryRow("Total", total));
            panel.add(summary, BorderLayout.CENTER);

            JPanel actions = horizontal();
            javax.swing.JButton clear = DesktopTheme.secondaryButton("Clear");
            clear.addActionListener(e -> {
                cart.clear();
                refreshCart();
            });
            javax.swing.JButton payButton = DesktopTheme.primaryButton("Complete sale");
            payButton.addActionListener(e -> completeSale());
            actions.add(clear);
            actions.add(payButton);
            panel.add(actions, BorderLayout.SOUTH);
            return panel;
        }

        private JPanel summaryRow(String label, JLabel value) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(DesktopTheme.pad(3, 0, 3, 0));
            row.add(DesktopTheme.label(label, Font.BOLD, 12f, DesktopTheme.MUTED), BorderLayout.WEST);
            row.add(value, BorderLayout.EAST);
            return row;
        }

        private void addSelectedCatalogItem() {
            int row = catalogTable.getSelectedRow();
            if (row < 0 || row >= catalogRows.size()) {
                return;
            }
            Object item = catalogRows.get(row);
            if (item instanceof Product product) {
                addProduct(product);
            } else if (item instanceof ServiceItem service) {
                addService(service);
            }
        }

        private void addProduct(Product product) {
            if (product.stock <= currentCartQuantity(product.id)) {
                showError(product.name + " does not have enough stock.");
                return;
            }
            cart.stream()
                .filter(line -> line.kind == CartLine.Kind.PRODUCT && line.itemId.equals(product.id))
                .findFirst()
                .ifPresentOrElse(line -> line.quantity++, () -> cart.add(new CartLine(CartLine.Kind.PRODUCT, product.id, product.name, 1, product.priceCents)));
            refreshCart();
        }

        private void addService(ServiceItem service) {
            cart.stream()
                .filter(line -> line.kind == CartLine.Kind.SERVICE && line.itemId.equals(service.id))
                .findFirst()
                .ifPresentOrElse(line -> line.quantity++, () -> cart.add(new CartLine(CartLine.Kind.SERVICE, service.id, service.name, 1, service.priceCents)));
            refreshCart();
        }

        private int currentCartQuantity(String productId) {
            return cart.stream()
                .filter(line -> line.kind == CartLine.Kind.PRODUCT && line.itemId.equals(productId))
                .mapToInt(line -> line.quantity)
                .sum();
        }

        private void completeSale() {
            if (cart.isEmpty()) {
                showError("Cart is empty.");
                return;
            }
            syncEditedCartQuantities();
            for (CartLine line : cart) {
                if (line.kind == CartLine.Kind.PRODUCT) {
                    Product product = state.products.stream().filter(item -> item.id.equals(line.itemId)).findFirst().orElse(null);
                    if (product == null || product.stock < line.quantity) {
                        showError(line.name + " does not have enough stock.");
                        return;
                    }
                }
            }
            long subtotalCents = cart.stream().mapToLong(CartLine::totalCents).sum();
            long taxCents = subtotalCents * state.settings.taxBasisPoints / 10000L;
            long totalCents = subtotalCents + taxCents;
            long paidCents = paid.getText().trim().isBlank() ? totalCents : Money.parseCents(paid.getText());
            String paymentMethod = (String) payment.getSelectedItem();
            Customer selected = (Customer) customer.getSelectedItem();
            long balance = Math.max(0, totalCents - paidCents);
            if (("Credit".equals(paymentMethod) || "Deposit".equals(paymentMethod) || balance > 0)
                && (selected == null || selected.name.equalsIgnoreCase("Walk-in customer"))) {
                showError("Select a named customer for credit or balance-due sales.");
                return;
            }
            for (CartLine line : cart) {
                if (line.kind == CartLine.Kind.PRODUCT) {
                    state.products.stream().filter(product -> product.id.equals(line.itemId)).findFirst().ifPresent(product -> product.stock -= line.quantity);
                }
            }
            if (selected != null && !selected.name.equalsIgnoreCase("Walk-in customer")) {
                selected.visits++;
                selected.balanceCents += balance;
            }
            boolean servicesOnly = cart.stream().allMatch(line -> line.kind == CartLine.Kind.SERVICE);
            String description = cart.stream()
                .map(line -> line.quantity + " x " + line.name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("Sale");
            Transaction transaction = new Transaction(
                state.nextId("TXN"),
                Instant.now(),
                servicesOnly ? TransactionType.SERVICE_SALE : TransactionType.SALE,
                selected == null ? "" : selected.id,
                selected == null ? "" : selected.name,
                description + (staff.getText().isBlank() ? "" : " - Staff: " + staff.getText().trim()),
                paymentMethod,
                subtotalCents,
                taxCents,
                totalCents,
                Math.min(paidCents, totalCents),
                balance
            );
            state.transactions.add(0, transaction);
            cart.clear();
            paid.setText("");
            persist();
            refreshAll();
            JOptionPane.showMessageDialog(frame, "Sale recorded: " + Money.format(totalCents, state.settings.currency), "POS", JOptionPane.INFORMATION_MESSAGE);
        }

        private void syncEditedCartQuantities() {
            for (int i = 0; i < cart.size(); i++) {
                Object value = cartModel.getValueAt(i, 1);
                try {
                    cart.get(i).quantity = Math.max(1, Integer.parseInt(value.toString()));
                } catch (Exception ignored) {
                    cart.get(i).quantity = 1;
                }
            }
        }

        private void refreshCatalog() {
            catalogGrid.removeAll();
            catalogRows.clear();
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            String selectedMode = (String) mode.getSelectedItem();
            if (!"Services".equals(selectedMode)) {
                state.products.stream()
                    .filter(product -> q.isBlank() || product.name.toLowerCase(Locale.ROOT).contains(q) || product.barcode.contains(q))
                    .forEach(product -> catalogGrid.add(tile(new ProductCard(product, state.settings.currency, () -> addProduct(product)))));
            }
            if (!"Products".equals(selectedMode)) {
                state.services.stream()
                    .filter(service -> q.isBlank() || service.name.toLowerCase(Locale.ROOT).contains(q))
                    .forEach(service -> catalogGrid.add(tile(new ServiceCard(service, state.settings.currency, () -> addService(service)))));
            }
            if (catalogGrid.getComponentCount() == 0) {
                catalogGrid.add(emptyState("No catalog items", "Pair the phone and sync products with images, or add products in Inventory."));
            }
            catalogGrid.revalidate();
            catalogGrid.repaint();
        }

        private void refreshCart() {
            cartModel.setRowCount(0);
            for (CartLine line : cart) {
                cartModel.addRow(new Object[]{line.name, line.quantity, Money.format(line.unitCents, state.settings.currency), Money.format(line.totalCents(), state.settings.currency)});
            }
            long subtotalCents = cart.stream().mapToLong(CartLine::totalCents).sum();
            long taxCents = subtotalCents * state.settings.taxBasisPoints / 10000L;
            long totalCents = subtotalCents + taxCents;
            subtotal.setText(Money.format(subtotalCents, state.settings.currency));
            tax.setText(Money.format(taxCents, state.settings.currency));
            total.setText(Money.format(totalCents, state.settings.currency));
            if (shouldRefreshSuggestedPaid()) {
                paid.setText(Money.input(totalCents));
                lastSuggestedPaidCents = totalCents;
            }
        }

        private boolean shouldRefreshSuggestedPaid() {
            String text = paid.getText().trim();
            if (text.isBlank()) {
                return true;
            }
            if (lastSuggestedPaidCents < 0) {
                return false;
            }
            try {
                return Money.parseCents(text) == lastSuggestedPaidCents;
            } catch (Exception ignored) {
                return false;
            }
        }

        @Override
        public void refresh() {
            refreshCatalog();
            refreshCart();
            PhoneBridgeServer.BridgeStatus bridge = phoneBridge.status();
            scannerStatus.setText("Phone scanner: " + (bridge.running ? (bridge.pairedDevice.isBlank() ? "waiting for pairing" : "linked to " + bridge.pairedDevice) : "bridge off") + "\n"
                + "USB scanner: focus the barcode field and scan\n"
                + "Laptop webcam: needs native camera/barcode module before release");
            DefaultComboBoxModel<Customer> model = new DefaultComboBoxModel<>();
            state.customers.forEach(model::addElement);
            customer.setModel(model);
            customer.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
                JLabel label = new JLabel(value == null ? "No customer" : value.name + (value.balanceCents > 0 ? " - owes " + Money.format(value.balanceCents, state.settings.currency) : ""));
                label.setOpaque(true);
                label.setBorder(DesktopTheme.pad(6, 8, 6, 8));
                label.setBackground(isSelected ? new Color(219, 234, 254) : Color.WHITE);
                return label;
            });
        }
    }

    private final class InventoryPanel extends JPanel implements Refreshable {
        private final JPanel productGrid = new JPanel(new java.awt.GridLayout(0, 3, 14, 14));
        private final JPanel serviceGrid = new JPanel(new java.awt.GridLayout(0, 2, 14, 14));
        private final JLabel summary = DesktopTheme.label("", Font.BOLD, 15f, DesktopTheme.INK);
        private final JTextField productName = new JTextField(14);
        private final JTextField productBarcode = new JTextField(12);
        private final JTextField productPrice = new JTextField(8);
        private final JTextField productCost = new JTextField(8);
        private final JTextField productStock = new JTextField(5);
        private final JTextField productCategory = new JTextField(10);
        private final JTextField serviceName = new JTextField(14);
        private final JTextField servicePrice = new JTextField(8);
        private final JTextField serviceDuration = new JTextField(5);
        private final JTextField serviceWarranty = new JTextField(5);
        private final JTextField serviceCategory = new JTextField(10);

        InventoryPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);
            add(headerCard(), BorderLayout.NORTH);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, productCatalog(), serviceCatalog());
            split.setResizeWeight(0.68);
            split.setBorder(null);
            add(split, BorderLayout.CENTER);
            add(managementForms(), BorderLayout.SOUTH);
        }

        private JPanel headerCard() {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(16, 0));
            JPanel copy = vertical();
            copy.add(DesktopTheme.label("Product catalog", Font.BOLD, 22f, DesktopTheme.INK));
            copy.add(Box.createVerticalStrut(6));
            copy.add(summary);
            card.add(copy, BorderLayout.CENTER);
            JPanel actions = horizontal();
            javax.swing.JButton phone = DesktopTheme.primaryButton("Sync from phone");
            phone.addActionListener(e -> navigate("Phone Link"));
            javax.swing.JButton whatsApp = DesktopTheme.secondaryButton("WhatsApp catalog");
            whatsApp.addActionListener(e -> navigate("WhatsApp"));
            actions.add(phone);
            actions.add(whatsApp);
            card.add(actions, BorderLayout.EAST);
            return card;
        }

        private JPanel productCatalog() {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 12));
            card.add(DesktopTheme.label("Products with images", Font.BOLD, 17f, DesktopTheme.INK), BorderLayout.NORTH);
            productGrid.setOpaque(false);
            card.add(scrollPanel(productGrid), BorderLayout.CENTER);
            return card;
        }

        private JPanel serviceCatalog() {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 12));
            card.add(DesktopTheme.label("Services", Font.BOLD, 17f, DesktopTheme.INK), BorderLayout.NORTH);
            serviceGrid.setOpaque(false);
            card.add(scrollPanel(serviceGrid), BorderLayout.CENTER);
            return card;
        }

        private JPanel managementForms() {
            JPanel card = DesktopTheme.card();
            card.setLayout(new java.awt.GridLayout(1, 2, 16, 0));
            card.add(productForm());
            card.add(serviceForm());
            return card;
        }

        private JPanel productForm() {
            JPanel wrap = new JPanel(new BorderLayout(0, 10));
            wrap.setOpaque(false);
            wrap.add(DesktopTheme.label("Add product manually", Font.BOLD, 14f, DesktopTheme.INK), BorderLayout.NORTH);
            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            form.add(productName, DesktopTheme.gbc(0, 0, 1, 1));
            form.add(productBarcode, DesktopTheme.gbc(1, 0, 1, 1));
            form.add(productPrice, DesktopTheme.gbc(2, 0, 1, 1));
            form.add(productCost, DesktopTheme.gbc(3, 0, 1, 1));
            form.add(productStock, DesktopTheme.gbc(4, 0, 1, 1));
            form.add(productCategory, DesktopTheme.gbc(5, 0, 1, 1));
            setPrompt(productName, "Name");
            setPrompt(productBarcode, "Barcode");
            setPrompt(productPrice, "Price");
            setPrompt(productCost, "Cost");
            setPrompt(productStock, "Stock");
            setPrompt(productCategory, "Category");
            javax.swing.JButton add = DesktopTheme.primaryButton("Add product");
            add.addActionListener(e -> addProduct());
            wrap.add(form, BorderLayout.CENTER);
            wrap.add(add, BorderLayout.EAST);
            return wrap;
        }

        private JPanel serviceForm() {
            JPanel wrap = new JPanel(new BorderLayout(0, 10));
            wrap.setOpaque(false);
            wrap.add(DesktopTheme.label("Add service manually", Font.BOLD, 14f, DesktopTheme.INK), BorderLayout.NORTH);
            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            form.add(serviceName, DesktopTheme.gbc(0, 0, 1, 1));
            form.add(servicePrice, DesktopTheme.gbc(1, 0, 1, 1));
            form.add(serviceDuration, DesktopTheme.gbc(2, 0, 1, 1));
            form.add(serviceWarranty, DesktopTheme.gbc(3, 0, 1, 1));
            form.add(serviceCategory, DesktopTheme.gbc(4, 0, 1, 1));
            setPrompt(serviceName, "Name");
            setPrompt(servicePrice, "Price");
            setPrompt(serviceDuration, "Min");
            setPrompt(serviceWarranty, "Warranty");
            setPrompt(serviceCategory, "Category");
            javax.swing.JButton add = DesktopTheme.primaryButton("Add service");
            add.addActionListener(e -> addService());
            wrap.add(form, BorderLayout.CENTER);
            wrap.add(add, BorderLayout.EAST);
            return wrap;
        }

        private void setPrompt(JTextField field, String name) {
            field.setToolTipText(name);
            field.setFont(DesktopTheme.font(Font.PLAIN, 13f));
        }

        private void addProduct() {
            try {
                if (productName.getText().trim().isBlank()) {
                    showError("Product name is required.");
                    return;
                }
                state.products.add(new Product(
                    state.nextId("PRD"),
                    productName.getText().trim(),
                    "",
                    productBarcode.getText().trim(),
                    productCategory.getText().trim(),
                    Money.parseCents(productPrice.getText()),
                    Money.parseCents(productCost.getText()),
                    Integer.parseInt(productStock.getText().trim().isBlank() ? "0" : productStock.getText().trim())
                ));
                productName.setText("");
                productBarcode.setText("");
                productPrice.setText("");
                productCost.setText("");
                productStock.setText("");
                productCategory.setText("");
                persist();
                refreshAll();
            } catch (Exception ex) {
                showError("Could not add product: " + ex.getMessage());
            }
        }

        private void addService() {
            try {
                if (serviceName.getText().trim().isBlank()) {
                    showError("Service name is required.");
                    return;
                }
                state.services.add(new ServiceItem(
                    state.nextId("SVC"),
                    serviceName.getText().trim(),
                    serviceCategory.getText().trim(),
                    Money.parseCents(servicePrice.getText()),
                    Integer.parseInt(serviceDuration.getText().trim().isBlank() ? "0" : serviceDuration.getText().trim()),
                    Integer.parseInt(serviceWarranty.getText().trim().isBlank() ? "0" : serviceWarranty.getText().trim())
                ));
                serviceName.setText("");
                servicePrice.setText("");
                serviceDuration.setText("");
                serviceWarranty.setText("");
                serviceCategory.setText("");
                persist();
                refreshAll();
            } catch (Exception ex) {
                showError("Could not add service: " + ex.getMessage());
            }
        }

        @Override
        public void refresh() {
            productGrid.removeAll();
            state.products.stream()
                .sorted(Comparator.comparing(product -> product.name))
                .forEach(product -> productGrid.add(tile(new ProductCard(product, state.settings.currency, () -> {
                    navigate("POS");
                    posPanel.addProduct(product);
                }))));
            if (productGrid.getComponentCount() == 0) {
                productGrid.add(emptyState("No products synced yet", "Pair Biashara AI mobile from Phone Link, then send product catalog records with images to this desktop."));
            }
            productGrid.revalidate();
            productGrid.repaint();

            serviceGrid.removeAll();
            state.services.stream()
                .sorted(Comparator.comparing(service -> service.name))
                .forEach(service -> serviceGrid.add(tile(new ServiceCard(service, state.settings.currency, () -> {
                    navigate("POS");
                    posPanel.addService(service);
                }))));
            if (serviceGrid.getComponentCount() == 0) {
                serviceGrid.add(emptyState("No services yet", "Services can be synced later from mobile or added here for desktop POS."));
            }
            serviceGrid.revalidate();
            serviceGrid.repaint();

            long withImages = state.products.stream().filter(product -> !product.imagePath.isBlank()).count();
            summary.setText(state.products.size() + " products - " + withImages + " with images - " + state.stockSyncItems.size() + " mobile stock sync rows");
        }
    }

    private final class LedgerPanel extends JPanel implements Refreshable {
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"Date", "Type", "Description", "Customer", "Method", "Total", "Paid", "Balance"}, 0);
        private final JTable table = new JTable(model);
        private final JLabel moneyIn = DesktopTheme.label("", Font.BOLD, 24f, DesktopTheme.GREEN);
        private final JLabel moneyOut = DesktopTheme.label("", Font.BOLD, 24f, DesktopTheme.RED);
        private final JLabel net = DesktopTheme.label("", Font.BOLD, 24f, DesktopTheme.PRIMARY_DARK);
        private final JTextField description = new JTextField(18);
        private final JTextField amount = new JTextField(10);
        private final JComboBox<String> type = new JComboBox<>(new String[]{"Expense", "Payment", "Adjustment"});

        LedgerPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);
            JPanel top = new JPanel(new java.awt.GridLayout(1, 3, 14, 14));
            top.setOpaque(false);
            top.add(kpi("Money in", moneyIn));
            top.add(kpi("Money out", moneyOut));
            top.add(kpi("Net", net));
            add(top, BorderLayout.NORTH);

            JPanel tableCard = DesktopTheme.card();
            tableCard.setLayout(new BorderLayout(0, 12));
            tableCard.add(scroll(table), BorderLayout.CENTER);
            tableCard.add(manualEntry(), BorderLayout.SOUTH);
            add(tableCard, BorderLayout.CENTER);
        }

        private JPanel kpi(String title, JLabel value) {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.add(DesktopTheme.label(title, Font.BOLD, 12f, DesktopTheme.MUTED));
            card.add(Box.createVerticalStrut(6));
            card.add(value);
            return card;
        }

        private JPanel manualEntry() {
            JPanel panel = horizontal();
            panel.add(DesktopTheme.label("Manual entry", Font.BOLD, 12f, DesktopTheme.MUTED));
            panel.add(type);
            description.setToolTipText("Description");
            amount.setToolTipText("Amount");
            panel.add(description);
            panel.add(amount);
            javax.swing.JButton add = DesktopTheme.primaryButton("Record");
            add.addActionListener(e -> recordManual());
            panel.add(add);
            return panel;
        }

        private void recordManual() {
            try {
                long value = Money.parseCents(amount.getText());
                String selected = (String) type.getSelectedItem();
                TransactionType txType = "Expense".equals(selected) ? TransactionType.EXPENSE : "Payment".equals(selected) ? TransactionType.PAYMENT : TransactionType.ADJUSTMENT;
                long signedTotal = txType == TransactionType.EXPENSE ? -Math.abs(value) : value;
                state.transactions.add(0, new Transaction(
                    state.nextId("TXN"),
                    Instant.now(),
                    txType,
                    "",
                    "",
                    description.getText().trim(),
                    "Manual",
                    signedTotal,
                    0,
                    signedTotal,
                    signedTotal,
                    0
                ));
                description.setText("");
                amount.setText("");
                persist();
                refreshAll();
            } catch (Exception ex) {
                showError("Could not record entry: " + ex.getMessage());
            }
        }

        @Override
        public void refresh() {
            model.setRowCount(0);
            state.transactions.stream()
                .sorted(Comparator.comparing((Transaction transaction) -> transaction.createdAt).reversed())
                .forEach(transaction -> model.addRow(new Object[]{
                    DATE_TIME.format(transaction.createdAt.atZone(ZoneId.systemDefault())),
                    transaction.type,
                    transaction.description,
                    transaction.customerName,
                    transaction.paymentMethod,
                    Money.format(transaction.totalCents, state.settings.currency),
                    Money.format(transaction.paidCents, state.settings.currency),
                    Money.format(transaction.balanceCents, state.settings.currency)
                }));
            long in = state.transactions.stream().filter(transaction -> transaction.totalCents > 0).mapToLong(transaction -> transaction.totalCents).sum();
            long out = state.transactions.stream().filter(transaction -> transaction.totalCents < 0).mapToLong(transaction -> Math.abs(transaction.totalCents)).sum();
            moneyIn.setText(Money.format(in, state.settings.currency));
            moneyOut.setText(Money.format(out, state.settings.currency));
            net.setText(Money.format(in - out, state.settings.currency));
        }
    }

    private final class CustomersPanel extends JPanel implements Refreshable {
        private final DefaultTableModel model = new DefaultTableModel(new String[]{"Name", "Phone", "Balance", "Visits"}, 0);
        private final JTable table = new JTable(model);
        private final JTextField name = new JTextField(14);
        private final JTextField phone = new JTextField(12);
        private final JTextField repayment = new JTextField(8);

        CustomersPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 12));
            card.add(scroll(table), BorderLayout.CENTER);
            card.add(actions(), BorderLayout.SOUTH);
            add(card);
        }

        private JPanel actions() {
            JPanel panel = horizontal();
            panel.add(DesktopTheme.label("New customer", Font.BOLD, 12f, DesktopTheme.MUTED));
            name.setToolTipText("Name");
            phone.setToolTipText("Phone");
            panel.add(name);
            panel.add(phone);
            javax.swing.JButton add = DesktopTheme.primaryButton("Add");
            add.addActionListener(e -> addCustomer());
            panel.add(add);
            panel.add(Box.createHorizontalStrut(18));
            panel.add(DesktopTheme.label("Repayment", Font.BOLD, 12f, DesktopTheme.MUTED));
            panel.add(repayment);
            javax.swing.JButton pay = DesktopTheme.secondaryButton("Apply to selected");
            pay.addActionListener(e -> applyRepayment());
            panel.add(pay);
            return panel;
        }

        private void addCustomer() {
            if (name.getText().trim().isBlank()) {
                showError("Customer name is required.");
                return;
            }
            state.customers.add(new Customer(state.nextId("CUS"), name.getText().trim(), phone.getText().trim(), 0, 0));
            name.setText("");
            phone.setText("");
            persist();
            refreshAll();
        }

        private void applyRepayment() {
            int row = table.getSelectedRow();
            if (row < 0 || row >= state.customers.size()) {
                showError("Select a customer first.");
                return;
            }
            Customer customer = state.customers.stream()
                .sorted(Comparator.comparing(c -> c.name))
                .toList()
                .get(row);
            long paid = Money.parseCents(repayment.getText());
            customer.balanceCents = Math.max(0, customer.balanceCents - paid);
            state.transactions.add(0, new Transaction(
                state.nextId("TXN"),
                Instant.now(),
                TransactionType.PAYMENT,
                customer.id,
                customer.name,
                "Credit repayment",
                "Customer payment",
                paid,
                0,
                paid,
                paid,
                customer.balanceCents
            ));
            repayment.setText("");
            persist();
            refreshAll();
        }

        @Override
        public void refresh() {
            model.setRowCount(0);
            state.customers.stream().sorted(Comparator.comparing(customer -> customer.name)).forEach(customer ->
                model.addRow(new Object[]{
                    customer.name,
                    customer.phone,
                    Money.format(customer.balanceCents, state.settings.currency),
                    customer.visits
                })
            );
        }
    }

    private final class AssistantPanel extends JPanel implements Refreshable {
        private final JTextArea transcript = DesktopTheme.textArea("");
        private final JTextField prompt = new JTextField();

        AssistantPanel() {
            super(new BorderLayout(14, 14));
            setOpaque(false);
            JPanel chat = DesktopTheme.card();
            chat.setLayout(new BorderLayout(0, 12));
            transcript.setEditable(false);
            transcript.setRows(18);
            transcript.setText("Assistant: " + advisor.welcome(state) + "\n");
            chat.add(new JScrollPane(transcript), BorderLayout.CENTER);
            JPanel input = new JPanel(new BorderLayout(10, 0));
            input.setOpaque(false);
            input.add(prompt, BorderLayout.CENTER);
            javax.swing.JButton send = DesktopTheme.primaryButton("Ask");
            send.addActionListener(e -> ask());
            input.add(send, BorderLayout.EAST);
            chat.add(input, BorderLayout.SOUTH);
            prompt.addActionListener(e -> ask());
            add(chat, BorderLayout.CENTER);

            JPanel actions = horizontal();
            javax.swing.JButton review = DesktopTheme.secondaryButton("Business review");
            review.addActionListener(e -> append("Assistant", advisor.businessReview(state)));
            actions.add(review);
            add(actions, BorderLayout.SOUTH);
        }

        private void ask() {
            String text = prompt.getText().trim();
            if (text.isBlank()) {
                return;
            }
            append("You", text);
            append("Assistant", advisor.answer(text, state));
            prompt.setText("");
        }

        private void append(String speaker, String text) {
            transcript.append(speaker + ": " + text + "\n\n");
            transcript.setCaretPosition(transcript.getDocument().getLength());
        }

        @Override
        public void refresh() {
            // Transcript is intentionally stable while navigating.
        }
    }

    private final class PhoneLinkPanel extends JPanel implements Refreshable {
        private final JLabel status = DesktopTheme.label("", Font.BOLD, 22f, DesktopTheme.INK);
        private final JLabel endpoint = DesktopTheme.label("", Font.PLAIN, 13f, DesktopTheme.MUTED);
        private final JTextArea payload = DesktopTheme.textArea("");
        private final DefaultTableModel scans = new DefaultTableModel(new String[]{"Time", "Device", "Type", "Value", "Status"}, 0);
        private final DefaultTableModel products = new DefaultTableModel(new String[]{"Time", "Device", "Product", "Barcode", "Stock", "Image", "Status"}, 0);
        private final DefaultTableModel stock = new DefaultTableModel(new String[]{"Time", "Device", "Product", "Barcode", "Qty", "Image", "Status"}, 0);

        PhoneLinkPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);
            JPanel top = DesktopTheme.card();
            top.setLayout(new BorderLayout(0, 12));
            JPanel statusWrap = vertical();
            statusWrap.add(status);
            statusWrap.add(Box.createVerticalStrut(6));
            statusWrap.add(endpoint);
            top.add(statusWrap, BorderLayout.NORTH);
            payload.setEditable(false);
            payload.setRows(3);
            payload.setFont(DesktopTheme.font(Font.BOLD, 15f));
            top.add(payload, BorderLayout.CENTER);
            JPanel actions = horizontal();
            javax.swing.JButton start = DesktopTheme.primaryButton("Start bridge");
            start.addActionListener(e -> startBridgeQuietly());
            javax.swing.JButton stop = DesktopTheme.secondaryButton("Stop");
            stop.addActionListener(e -> phoneBridge.stop());
            javax.swing.JButton rotate = DesktopTheme.secondaryButton("New pairing code");
            rotate.addActionListener(e -> phoneBridge.rotateToken());
            actions.add(start);
            actions.add(stop);
            actions.add(rotate);
            top.add(actions, BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);

            JTable table = new JTable(scans);
            JTable productTable = new JTable(products);
            JTable stockTable = new JTable(stock);
            JSplitPane lower = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard("Mobile product catalog", productTable), tableCard("Mobile stock intake", stockTable));
            lower.setResizeWeight(0.5);
            lower.setBorder(null);
            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard("Phone scans", table), lower);
            split.setResizeWeight(0.45);
            split.setBorder(null);
            add(split, BorderLayout.CENTER);
        }

        private JPanel tableCard(String title, JTable table) {
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 12));
            card.add(DesktopTheme.label(title, Font.BOLD, 16f, DesktopTheme.INK), BorderLayout.NORTH);
            card.add(scroll(table), BorderLayout.CENTER);
            return card;
        }

        @Override
        public void refresh() {
            PhoneBridgeServer.BridgeStatus bridge = phoneBridge.status();
            status.setText(bridge.running ? (bridge.pairedDevice.isBlank() ? "Ready for phone pairing" : "Linked to " + bridge.pairedDevice) : "Phone bridge stopped");
            endpoint.setText("Endpoint: http://" + bridge.host + ":" + bridge.port + " - Pairing code: " + bridge.token);
            payload.setText(phoneBridge.pairingPayload() + "\nPOST /product-sync for catalog with images - POST /scan for POS scanner - POST /stock-intake for stock counts");
            scans.setRowCount(0);
            state.scanEvents.stream().limit(50).forEach(event -> scans.addRow(new Object[]{
                DATE_TIME.format(event.createdAt.atZone(ZoneId.systemDefault())),
                event.sourceDevice,
                event.kind,
                event.rawValue,
                event.status
            }));
            products.setRowCount(0);
            state.productSyncItems.stream().limit(50).forEach(item -> products.addRow(new Object[]{
                DATE_TIME.format(item.createdAt.atZone(ZoneId.systemDefault())),
                item.sourceDevice,
                item.name,
                item.barcode,
                item.stock,
                item.imagePath.isBlank() ? "No image" : "Saved",
                item.status
            }));
            stock.setRowCount(0);
            state.stockSyncItems.stream().limit(50).forEach(item -> stock.addRow(new Object[]{
                DATE_TIME.format(item.createdAt.atZone(ZoneId.systemDefault())),
                item.sourceDevice,
                item.productName,
                item.barcode,
                item.quantity,
                item.imagePath.isBlank() ? "No image" : "Saved",
                item.status
            }));
        }
    }

    private final class WhatsAppPanel extends JPanel implements Refreshable {
        private final DefaultTableModel productModel = new DefaultTableModel(new String[]{"Product", "Price", "Stock", "Image", "Retailer ID"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        private final JTable products = new JTable(productModel);
        private final JTextArea message = DesktopTheme.textArea("");
        private final JLabel readiness = DesktopTheme.label("", Font.BOLD, 16f, DesktopTheme.INK);
        private List<Product> visibleProducts = List.of();

        WhatsAppPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);

            JPanel hero = DesktopTheme.card();
            hero.setLayout(new BorderLayout(16, 0));
            JPanel copy = vertical();
            copy.add(DesktopTheme.label("WhatsApp Business catalog", Font.BOLD, 23f, DesktopTheme.INK));
            copy.add(Box.createVerticalStrut(7));
            copy.add(readiness);
            hero.add(copy, BorderLayout.CENTER);
            JPanel actions = horizontal();
            javax.swing.JButton copyMessage = DesktopTheme.primaryButton("Copy message");
            copyMessage.addActionListener(e -> copyToClipboard(message.getText()));
            javax.swing.JButton openShare = DesktopTheme.secondaryButton("Open WhatsApp");
            openShare.addActionListener(e -> openWhatsAppShare());
            javax.swing.JButton settings = DesktopTheme.secondaryButton("Settings");
            settings.addActionListener(e -> navigate("Settings"));
            actions.add(copyMessage);
            actions.add(openShare);
            actions.add(settings);
            hero.add(actions, BorderLayout.EAST);
            add(hero, BorderLayout.NORTH);

            JPanel center = new JPanel(new java.awt.GridLayout(1, 2, 16, 16));
            center.setOpaque(false);
            JPanel productCard = DesktopTheme.card();
            productCard.setLayout(new BorderLayout(0, 12));
            productCard.add(DesktopTheme.label("Products ready to sell", Font.BOLD, 17f, DesktopTheme.INK), BorderLayout.NORTH);
            productCard.add(scroll(products), BorderLayout.CENTER);
            center.add(productCard);

            JPanel messageCard = DesktopTheme.card();
            messageCard.setLayout(new BorderLayout(0, 12));
            messageCard.add(DesktopTheme.label("Customer message", Font.BOLD, 17f, DesktopTheme.INK), BorderLayout.NORTH);
            message.setRows(14);
            messageCard.add(new JScrollPane(message), BorderLayout.CENTER);
            JPanel messageActions = horizontal();
            javax.swing.JButton selected = DesktopTheme.secondaryButton("Use selected");
            selected.addActionListener(e -> buildMessageFromSelection());
            javax.swing.JButton all = DesktopTheme.secondaryButton("Use all in stock");
            all.addActionListener(e -> buildMessage(visibleProducts));
            messageActions.add(selected);
            messageActions.add(all);
            messageCard.add(messageActions, BorderLayout.SOUTH);
            center.add(messageCard);
            add(center, BorderLayout.CENTER);
        }

        private void buildMessageFromSelection() {
            int[] rows = products.getSelectedRows();
            if (rows.length == 0) {
                buildMessage(visibleProducts);
                return;
            }
            List<Product> selected = new ArrayList<>();
            for (int row : rows) {
                if (row >= 0 && row < visibleProducts.size()) {
                    selected.add(visibleProducts.get(row));
                }
            }
            buildMessage(selected);
        }

        private void buildMessage(List<Product> items) {
            StringBuilder builder = new StringBuilder();
            builder.append(state.settings.businessName).append("\n");
            builder.append("Available today:\n");
            items.stream().filter(product -> product.stock > 0).limit(12).forEach(product ->
                builder.append("- ")
                    .append(product.name)
                    .append(" - ")
                    .append(Money.format(product.priceCents, state.settings.currency))
                    .append(" (")
                    .append(product.stock)
                    .append(" available)")
                    .append("\n")
            );
            builder.append("\nReply with the item name and quantity to order.");
            message.setText(builder.toString());
        }

        private void copyToClipboard(String text) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(frame, "Message copied.", "WhatsApp", JOptionPane.INFORMATION_MESSAGE);
        }

        private void openWhatsAppShare() {
            try {
                String text = message.getText().isBlank() ? productMessage() : message.getText();
                String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
                Desktop.getDesktop().browse(URI.create("https://wa.me/?text=" + encoded));
            } catch (Exception ex) {
                showError("Could not open WhatsApp share: " + ex.getMessage());
            }
        }

        private String productMessage() {
            buildMessage(visibleProducts);
            return message.getText();
        }

        @Override
        public void refresh() {
            visibleProducts = state.products.stream()
                .sorted(Comparator.comparing(product -> product.name))
                .toList();
            productModel.setRowCount(0);
            for (Product product : visibleProducts) {
                productModel.addRow(new Object[]{
                    product.name,
                    Money.format(product.priceCents, state.settings.currency),
                    product.stock,
                    product.imagePath.isBlank() ? "Missing" : "Ready",
                    product.whatsappRetailerId.isBlank() ? product.id : product.whatsappRetailerId
                });
            }
            long sellable = visibleProducts.stream().filter(product -> product.stock > 0).count();
            readiness.setText(sellable + " stocked products ready - Catalog ID "
                + (state.settings.whatsappCatalogId.isBlank() ? "not configured" : state.settings.whatsappCatalogId));
            if (message.getText().isBlank()) {
                buildMessage(visibleProducts);
            }
        }
    }

    private final class ModelPanel extends JPanel implements Refreshable {
        private final JLabel status = DesktopTheme.label("", Font.BOLD, 22f, DesktopTheme.INK);
        private final JLabel detail = DesktopTheme.label("", Font.PLAIN, 13f, DesktopTheme.MUTED);
        private final JTextField url = new JTextField();
        private final JProgressBar progress = new JProgressBar(0, 100);
        private SwingWorker<Path, Integer> worker;

        ModelPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);
            JPanel card = DesktopTheme.card();
            card.setLayout(new BorderLayout(0, 16));
            JPanel header = vertical();
            header.add(status);
            header.add(Box.createVerticalStrut(6));
            header.add(detail);
            card.add(header, BorderLayout.NORTH);

            JPanel form = new JPanel(new BorderLayout(10, 10));
            form.setOpaque(false);
            form.add(DesktopTheme.label("Direct model URL", Font.BOLD, 12f, DesktopTheme.MUTED), BorderLayout.NORTH);
            form.add(url, BorderLayout.CENTER);
            javax.swing.JButton download = DesktopTheme.primaryButton("Download");
            download.addActionListener(e -> downloadModel());
            form.add(download, BorderLayout.EAST);
            card.add(form, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(0, 12));
            bottom.setOpaque(false);
            progress.setStringPainted(true);
            bottom.add(progress, BorderLayout.NORTH);
            JPanel actions = horizontal();
            javax.swing.JButton importButton = DesktopTheme.secondaryButton("Import model");
            importButton.addActionListener(e -> importModel());
            javax.swing.JButton open = DesktopTheme.secondaryButton("Open folder");
            open.addActionListener(e -> openModelsFolder());
            actions.add(importButton);
            actions.add(open);
            bottom.add(actions, BorderLayout.SOUTH);
            card.add(bottom, BorderLayout.SOUTH);
            add(card, BorderLayout.NORTH);
        }

        private void importModel() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Import desktop AI model");
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path target = store.importModel(chooser.getSelectedFile().toPath());
                state.settings.modelPath = target.toAbsolutePath().toString();
                persist();
                refreshAll();
            }
        }

        private void openModelsFolder() {
            try {
                Files.createDirectories(store.modelsDir());
                Desktop.getDesktop().open(store.modelsDir().toFile());
            } catch (Exception ex) {
                showError("Could not open model folder: " + ex.getMessage());
            }
        }

        private void downloadModel() {
            String rawUrl = url.getText().trim();
            if (rawUrl.isBlank()) {
                showError("Paste a direct model URL.");
                return;
            }
            if (worker != null && !worker.isDone()) {
                showError("A model download is already running.");
                return;
            }
            worker = new SwingWorker<>() {
                @Override
                protected Path doInBackground() throws Exception {
                    Files.createDirectories(store.modelsDir());
                    String fileName = Path.of(URI.create(rawUrl).getPath()).getFileName().toString();
                    if (fileName.isBlank()) {
                        fileName = "desktop-ai-model.bin";
                    }
                    Path part = store.modelsDir().resolve(fileName + ".part").normalize();
                    Path target = store.modelsDir().resolve(fileName).normalize();
                    HttpURLConnection connection = (HttpURLConnection) URI.create(rawUrl).toURL().openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    long total = connection.getContentLengthLong();
                    long read = 0;
                    try (var input = connection.getInputStream(); var output = Files.newOutputStream(part)) {
                        byte[] buffer = new byte[1024 * 64];
                        int count;
                        while ((count = input.read(buffer)) >= 0) {
                            output.write(buffer, 0, count);
                            read += count;
                            if (total > 0) {
                                publish((int) Math.min(100, (read * 100) / total));
                            }
                            if (isCancelled()) {
                                throw new IOException("Download cancelled.");
                            }
                        }
                    }
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                    return target;
                }

                @Override
                protected void process(List<Integer> chunks) {
                    progress.setValue(chunks.get(chunks.size() - 1));
                    progress.setString(progress.getValue() + "%");
                }

                @Override
                protected void done() {
                    try {
                        Path target = get();
                        state.settings.modelPath = target.toAbsolutePath().toString();
                        persist();
                        progress.setValue(100);
                        progress.setString("Installed");
                        refreshAll();
                    } catch (Exception ex) {
                        progress.setString("Failed");
                        showError("Model download failed: " + ex.getMessage());
                    }
                }
            };
            progress.setValue(0);
            progress.setString("Starting");
            worker.execute();
        }

        @Override
        public void refresh() {
            if (state.settings.modelPath.isBlank()) {
                status.setText("No desktop model installed");
                detail.setText("Model folder: " + store.modelsDir());
                progress.setValue(0);
                progress.setString("Idle");
            } else {
                status.setText("Desktop model installed");
                detail.setText(state.settings.modelPath);
                if (worker == null || worker.isDone()) {
                    progress.setValue(100);
                    progress.setString("Ready");
                }
            }
        }
    }

    private final class SettingsPanel extends JPanel implements Refreshable {
        private final JTextField business = new JTextField(22);
        private final JTextField owner = new JTextField(18);
        private final JTextField currency = new JTextField(8);
        private final JTextField tax = new JTextField(8);
        private final JTextField footer = new JTextField(30);
        private final JTextField whatsappPhoneNumberId = new JTextField(18);
        private final JTextField whatsappCatalogId = new JTextField(18);
        private final JTextField whatsappCountry = new JTextField(8);
        private final JCheckBox bridge = new JCheckBox("Enable phone bridge on startup");

        SettingsPanel() {
            super(new BorderLayout(16, 16));
            setOpaque(false);
            JPanel card = DesktopTheme.card();
            card.setLayout(new GridBagLayout());
            card.add(DesktopTheme.label("Business name", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 0, 1, 0));
            card.add(business, DesktopTheme.gbc(1, 0, 2, 1));
            card.add(DesktopTheme.label("Owner", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 1, 1, 0));
            card.add(owner, DesktopTheme.gbc(1, 1, 2, 1));
            card.add(DesktopTheme.label("Currency", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 2, 1, 0));
            card.add(currency, DesktopTheme.gbc(1, 2, 1, 1));
            card.add(DesktopTheme.label("Tax %", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 3, 1, 0));
            card.add(tax, DesktopTheme.gbc(1, 3, 1, 1));
            card.add(DesktopTheme.label("Receipt footer", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 4, 1, 0));
            card.add(footer, DesktopTheme.gbc(1, 4, 2, 1));
            card.add(DesktopTheme.label("WhatsApp phone ID", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 5, 1, 0));
            card.add(whatsappPhoneNumberId, DesktopTheme.gbc(1, 5, 2, 1));
            card.add(DesktopTheme.label("WhatsApp catalog ID", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 6, 1, 0));
            card.add(whatsappCatalogId, DesktopTheme.gbc(1, 6, 2, 1));
            card.add(DesktopTheme.label("Default country code", Font.BOLD, 12f, DesktopTheme.MUTED), DesktopTheme.gbc(0, 7, 1, 0));
            card.add(whatsappCountry, DesktopTheme.gbc(1, 7, 1, 1));
            bridge.setOpaque(false);
            bridge.setFont(DesktopTheme.font(Font.BOLD, 13f));
            bridge.setForeground(DesktopTheme.INK);
            card.add(bridge, DesktopTheme.gbc(1, 8, 2, 1));

            JPanel actions = horizontal();
            javax.swing.JButton save = DesktopTheme.primaryButton("Save settings");
            save.addActionListener(e -> saveSettings());
            javax.swing.JButton backup = DesktopTheme.secondaryButton("Export backup");
            backup.addActionListener(e -> exportBackup());
            javax.swing.JButton openData = DesktopTheme.secondaryButton("Open data folder");
            openData.addActionListener(e -> openDataFolder());
            actions.add(save);
            actions.add(backup);
            actions.add(openData);
            card.add(actions, DesktopTheme.gbc(1, 9, 2, 1));
            add(card, BorderLayout.NORTH);
        }

        private void saveSettings() {
            try {
                state.settings.businessName = business.getText().trim();
                state.settings.ownerName = owner.getText().trim();
                state.settings.currency = currency.getText().trim().toUpperCase(Locale.ROOT);
                state.settings.taxBasisPoints = Math.round(Double.parseDouble(tax.getText().trim().isBlank() ? "0" : tax.getText().trim()) * 100);
                state.settings.receiptFooter = footer.getText().trim();
                state.settings.phoneBridgeEnabled = bridge.isSelected();
                state.settings.whatsappPhoneNumberId = whatsappPhoneNumberId.getText().trim();
                state.settings.whatsappCatalogId = whatsappCatalogId.getText().trim();
                state.settings.whatsappDefaultCountryCode = whatsappCountry.getText().trim();
                if (bridge.isSelected()) {
                    startBridgeQuietly();
                } else {
                    phoneBridge.stop();
                }
                persist();
                refreshAll();
            } catch (Exception ex) {
                showError("Could not save settings: " + ex.getMessage());
            }
        }

        private void exportBackup() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export backup");
            chooser.setSelectedFile(new java.io.File("biashara-desktop-backup.zip"));
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path target = store.exportBackup(chooser.getSelectedFile().toPath());
                JOptionPane.showMessageDialog(frame, "Backup exported to " + target, "Backup", JOptionPane.INFORMATION_MESSAGE);
            }
        }

        private void openDataFolder() {
            try {
                Desktop.getDesktop().open(store.dataDir().toFile());
            } catch (Exception ex) {
                showError("Could not open data folder: " + ex.getMessage());
            }
        }

        @Override
        public void refresh() {
            business.setText(state.settings.businessName);
            owner.setText(state.settings.ownerName);
            currency.setText(state.settings.currency);
            tax.setText(Double.toString(state.settings.taxBasisPoints / 100.0));
            footer.setText(state.settings.receiptFooter);
            whatsappPhoneNumberId.setText(state.settings.whatsappPhoneNumberId);
            whatsappCatalogId.setText(state.settings.whatsappCatalogId);
            whatsappCountry.setText(state.settings.whatsappDefaultCountryCode);
            bridge.setSelected(state.settings.phoneBridgeEnabled);
        }
    }
}
