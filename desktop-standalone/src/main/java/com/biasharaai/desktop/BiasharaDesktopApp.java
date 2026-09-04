package com.biasharaai.desktop;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

public final class BiasharaDesktopApp {
    private BiasharaDesktopApp() {
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                for (var info : UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        UIManager.setLookAndFeel(info.getClassName());
                        break;
                    }
                }
                UIManager.put("control", new Color(248, 250, 252));
                UIManager.put("nimbusBase", new Color(51, 65, 85));
                UIManager.put("nimbusBlueGrey", new Color(226, 232, 240));
                UIManager.put("Table.showGrid", false);
            } catch (Exception ignored) {
                // The default Swing look and feel is acceptable.
            }
            var store = DesktopDataStore.openDefault();
            var frame = new BiasharaDesktopFrame(store);
            frame.setVisible(true);
        });
    }
}

final class BiasharaDesktopFrame extends JFrame {
    private static final Color BACKGROUND = new Color(248, 250, 252);
    private static final Color SURFACE = Color.WHITE;
    private static final Color INK = new Color(24, 31, 42);
    private static final Color MUTED = new Color(93, 104, 119);
    private static final Color ACCENT = new Color(37, 99, 235);
    private static final Color DANGER = new Color(185, 28, 28);
    private static final Color SUCCESS = new Color(22, 101, 52);
    private static final Color WARNING = new Color(146, 64, 14);

    private final DesktopDataStore store;
    private final ProductTableModel products;
    private final TransactionTableModel transactions;
    private final CustomerTableModel customers;
    private final CartTableModel cart = new CartTableModel();
    private final DesktopModelManager modelManager;
    private final DesktopAdvisor advisor = new DesktopAdvisor();
    private final DefaultListModel<Insight> insightModel = new DefaultListModel<>();
    private final List<ChatTurn> chatTurns = new ArrayList<>();
    private final JCheckBox autonomousScan = new JCheckBox("Autonomous scan", true);
    private final javax.swing.Timer autonomyTimer;

    private final JLabel revenueValue = metricValue();
    private final JLabel expenseValue = metricValue();
    private final JLabel netValue = metricValue();
    private final JLabel stockValue = metricValue();
    private final JLabel debtValue = metricValue();
    private final JLabel lowStockValue = metricValue();
    private final JLabel dataPathLabel = new JLabel();

    private final JTextField inventoryScan = new JTextField(18);
    private final JTextField productName = new JTextField(16);
    private final JTextField productCategory = new JTextField(12);
    private final JTextField productBarcode = new JTextField(12);
    private final JSpinner productCost = moneySpinner();
    private final JSpinner productPrice = moneySpinner();
    private final JSpinner productStock = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));

    private final JTextField ledgerDate = new JTextField(LocalDate.now().toString(), 10);
    private final JComboBox<TransactionType> ledgerType = new JComboBox<>(
        new TransactionType[] {TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.RETURN}
    );
    private final JTextField ledgerAmount = new JTextField(10);
    private final JTextField ledgerDescription = new JTextField(28);

    private final JTextField posScan = new JTextField(18);
    private final JComboBox<ProductRef> posProduct = new JComboBox<>();
    private final JSpinner posQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JCheckBox creditSale = new JCheckBox("Credit sale");
    private final JComboBox<CustomerRef> posCustomer = new JComboBox<>();
    private final JLabel cartTotal = metricValue();

    private final JTextField customerName = new JTextField(16);
    private final JTextField customerPhone = new JTextField(12);
    private final JTextField customerNotes = new JTextField(18);
    private final JComboBox<CustomerRef> debtCustomer = new JComboBox<>();
    private final JTextField debtAmount = new JTextField(10);
    private final JComboBox<String> debtAction = new JComboBox<>(new String[] {"Add credit", "Record repayment"});

    private final JTextArea assistantAnswer = new JTextArea();
    private final JTextField assistantQuestion = new JTextField();
    private final JTextField modelUrl = new JTextField(42);
    private final JLabel modelStatus = new JLabel();
    private final JLabel modelDetail = new JLabel();
    private final JProgressBar modelProgress = new JProgressBar(0, 100);
    private JButton modelDownloadButton;
    private JButton modelCancelButton;
    private javax.swing.SwingWorker<Path, Void> modelDownloadWorker;

    BiasharaDesktopFrame(DesktopDataStore store) {
        super("Biashara AI Desktop Standalone");
        this.store = store;
        this.modelManager = new DesktopModelManager(store.dir());
        this.products = new ProductTableModel(store.products());
        this.transactions = new TransactionTableModel(store.transactions());
        this.customers = new CustomerTableModel(store.customers());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1160, 760));
        setLocationByPlatform(true);
        setContentPane(content());
        refreshAll();
        appendChat("Assistant", advisor.welcome());
        autonomyTimer = new javax.swing.Timer(30_000, e -> {
            if (autonomousScan.isSelected()) {
                refreshInsights();
            }
        });
        autonomyTimer.start();
        pack();
    }

    private JPanel content() {
        var root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.add(header(), BorderLayout.NORTH);

        var tabs = new JTabbedPane();
        tabs.addTab("Dashboard", dashboardTab());
        tabs.addTab("Inventory", inventoryTab());
        tabs.addTab("POS", posTab());
        tabs.addTab("Ledger", ledgerTab());
        tabs.addTab("Customers", customersTab());
        tabs.addTab("Assistant", assistantTab());
        tabs.addTab("AI Model", modelTab());
        tabs.addTab("Data", dataTab());
        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    private JPanel header() {
        var panel = new JPanel(new BorderLayout());
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(18, 24, 16, 24));

        var titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        var title = new JLabel("Biashara AI Desktop");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        title.setForeground(INK);
        var subtitle = new JLabel("Local command center for POS, inventory, cash flow, debt, and assistant review");
        subtitle.setForeground(MUTED);
        titleBox.add(title);
        titleBox.add(subtitle);

        dataPathLabel.setForeground(MUTED);
        dataPathLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(titleBox, BorderLayout.WEST);
        panel.add(dataPathLabel, BorderLayout.EAST);
        return panel;
    }

    private JPanel dashboardTab() {
        var panel = page();
        var grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        var c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridy = 0;
        addMetric(grid, c, 0, "Cash revenue", revenueValue);
        addMetric(grid, c, 1, "Expenses", expenseValue);
        addMetric(grid, c, 2, "Net cash", netValue);
        c.gridy = 1;
        addMetric(grid, c, 0, "Inventory value", stockValue);
        addMetric(grid, c, 1, "Customer debt", debtValue);
        addMetric(grid, c, 2, "Low stock items", lowStockValue);

        var recent = table(transactions);
        var runScan = primaryButton("Run business review");
        runScan.addActionListener(e -> {
            refreshInsights();
            appendChat("Assistant", advisor.autonomyReport(insights()));
        });
        panel.add(grid);
        panel.add(actionRow(runScan));

        var split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            insightsPanel("Autonomous insights"),
            tablePanel("Recent ledger", recent)
        );
        split.setResizeWeight(0.42);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        panel.add(split);
        return panel;
    }

    private JPanel inventoryTab() {
        var panel = page();
        var scanner = card(new BorderLayout(8, 8));
        scanner.add(section("Inventory scanner"), BorderLayout.NORTH);
        var scanButton = primaryButton("Use scan");
        scanButton.addActionListener(e -> handleInventoryScan());
        inventoryScan.addActionListener(e -> handleInventoryScan());
        scanner.add(actionRow(new JLabel("Barcode"), inventoryScan, scanButton), BorderLayout.CENTER);

        var form = card(new GridBagLayout());
        var c = constraints();
        addField(form, c, 0, "Name", productName);
        addField(form, c, 1, "Category", productCategory);
        addField(form, c, 2, "Cost", productCost);
        addField(form, c, 3, "Price", productPrice);
        addField(form, c, 4, "Stock", productStock);
        addField(form, c, 5, "Barcode", productBarcode);

        var add = primaryButton("Add product");
        add.addActionListener(e -> addProduct());
        c.gridx = 12;
        c.gridy = 0;
        c.gridheight = 2;
        form.add(add, c);

        var table = table(products);
        var delete = dangerButton("Delete selected");
        delete.addActionListener(e -> deleteSelected(table, products));
        panel.add(scanner);
        panel.add(form);
        panel.add(actionRow(delete));
        panel.add(new JScrollPane(table));
        return panel;
    }

    private JPanel posTab() {
        var panel = page();
        var scanner = card(new BorderLayout(8, 8));
        scanner.add(section("POS scanner"), BorderLayout.NORTH);
        var scanButton = primaryButton("Add scan");
        scanButton.addActionListener(e -> addScannedProductToCart());
        posScan.addActionListener(e -> addScannedProductToCart());
        scanner.add(actionRow(new JLabel("Barcode"), posScan, scanButton), BorderLayout.CENTER);

        var form = card(new GridBagLayout());
        var c = constraints();
        posProduct.setPrototypeDisplayValue(new ProductRef("", "Choose product", BigDecimal.ZERO, 0));
        addField(form, c, 0, "Product", posProduct);
        addField(form, c, 1, "Qty", posQuantity);
        c.gridx = 4;
        c.gridy = 0;
        form.add(creditSale, c);
        addField(form, c, 3, "Customer", posCustomer);
        var add = primaryButton("Add to cart");
        add.addActionListener(e -> addCartLine());
        c.gridx = 8;
        c.gridy = 0;
        c.gridheight = 2;
        form.add(add, c);

        var totals = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        totals.setOpaque(false);
        totals.add(new JLabel("Cart total"));
        totals.add(cartTotal);
        var record = primaryButton("Record sale");
        record.addActionListener(e -> recordSale());
        var clear = secondaryButton("Clear cart");
        clear.addActionListener(e -> {
            cart.clear();
            updateCartTotal();
        });
        totals.add(record);
        totals.add(clear);

        panel.add(scanner);
        panel.add(form);
        panel.add(totals);
        panel.add(new JScrollPane(table(cart)));
        return panel;
    }

    private JPanel ledgerTab() {
        var panel = page();
        var form = card(new GridBagLayout());
        var c = constraints();
        addField(form, c, 0, "Date", ledgerDate);
        addField(form, c, 1, "Type", ledgerType);
        addField(form, c, 2, "Amount", ledgerAmount);
        addField(form, c, 3, "Description", ledgerDescription);
        var add = primaryButton("Add entry");
        add.addActionListener(e -> addLedgerEntry());
        c.gridx = 8;
        c.gridy = 0;
        c.gridheight = 2;
        form.add(add, c);

        var table = table(transactions);
        var delete = dangerButton("Delete selected");
        delete.addActionListener(e -> deleteSelected(table, transactions));
        panel.add(form);
        panel.add(actionRow(delete));
        panel.add(new JScrollPane(table));
        return panel;
    }

    private JPanel customersTab() {
        var panel = page();
        var addForm = card(new GridBagLayout());
        var c = constraints();
        addField(addForm, c, 0, "Name", customerName);
        addField(addForm, c, 1, "Phone", customerPhone);
        addField(addForm, c, 2, "Notes", customerNotes);
        var add = primaryButton("Add customer");
        add.addActionListener(e -> addCustomer());
        c.gridx = 6;
        c.gridy = 0;
        c.gridheight = 2;
        addForm.add(add, c);

        var debtForm = card(new GridBagLayout());
        c = constraints();
        addField(debtForm, c, 0, "Customer", debtCustomer);
        addField(debtForm, c, 1, "Action", debtAction);
        addField(debtForm, c, 2, "Amount", debtAmount);
        var apply = primaryButton("Apply");
        apply.addActionListener(e -> applyDebtAction());
        c.gridx = 6;
        c.gridy = 0;
        c.gridheight = 2;
        debtForm.add(apply, c);

        var table = table(customers);
        var delete = dangerButton("Delete selected");
        delete.addActionListener(e -> deleteSelected(table, customers));
        panel.add(addForm);
        panel.add(debtForm);
        panel.add(actionRow(delete));
        panel.add(new JScrollPane(table));
        return panel;
    }

    private JPanel assistantTab() {
        var panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(16, 20, 20, 20));

        var suggested = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        suggested.setOpaque(false);
        suggested.add(promptButton("What should I do today?"));
        suggested.add(promptButton("Show low stock risks"));
        suggested.add(promptButton("Review debts and credit sales"));
        suggested.add(promptButton("How is this month performing?"));
        suggested.add(promptButton("Find cash flow problems"));

        var input = new JPanel(new BorderLayout(8, 0));
        input.setOpaque(false);
        var ask = primaryButton("Ask");
        ask.addActionListener(e -> answerAssistant());
        assistantQuestion.addActionListener(e -> answerAssistant());
        input.add(assistantQuestion, BorderLayout.CENTER);
        input.add(ask, BorderLayout.EAST);

        assistantAnswer.setEditable(false);
        assistantAnswer.setLineWrap(true);
        assistantAnswer.setWrapStyleWord(true);
        assistantAnswer.setFont(assistantAnswer.getFont().deriveFont(15f));
        assistantAnswer.setBorder(new EmptyBorder(12, 12, 12, 12));
        assistantAnswer.setBackground(SURFACE);

        var chat = card(new BorderLayout());
        chat.add(suggested, BorderLayout.NORTH);
        chat.add(new JScrollPane(assistantAnswer), BorderLayout.CENTER);
        chat.add(input, BorderLayout.SOUTH);

        var scan = primaryButton("Run autonomous scan");
        scan.addActionListener(e -> {
            refreshInsights();
            appendChat("Assistant", advisor.autonomyReport(insights()));
        });
        var side = insightsPanel("Model workspace");
        side.add(actionRow(scan, autonomousScan), BorderLayout.SOUTH);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chat, side);
        split.setResizeWeight(0.68);
        split.setBorder(BorderFactory.createEmptyBorder());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel modelTab() {
        var panel = page();

        var status = card(new BorderLayout(8, 8));
        status.add(section("Local AI model"), BorderLayout.NORTH);
        var statusText = new JPanel();
        statusText.setOpaque(false);
        statusText.setLayout(new BoxLayout(statusText, BoxLayout.Y_AXIS));
        modelStatus.setFont(modelStatus.getFont().deriveFont(Font.BOLD, 16f));
        modelStatus.setForeground(INK);
        modelDetail.setForeground(MUTED);
        statusText.add(modelStatus);
        statusText.add(modelDetail);
        modelProgress.setStringPainted(true);
        status.add(statusText, BorderLayout.CENTER);
        status.add(modelProgress, BorderLayout.SOUTH);

        var controls = card(new GridBagLayout());
        var c = constraints();
        addField(controls, c, 0, "Download URL", modelUrl);
        modelDownloadButton = primaryButton("Download / resume");
        modelDownloadButton.addActionListener(e -> startModelDownload());
        modelCancelButton = secondaryButton("Cancel");
        modelCancelButton.setEnabled(false);
        modelCancelButton.addActionListener(e -> cancelModelDownload());

        c.gridx = 2;
        c.gridy = 0;
        c.gridheight = 2;
        controls.add(modelDownloadButton, c);
        c.gridx = 3;
        controls.add(modelCancelButton, c);

        var importButton = secondaryButton("Import local model");
        importButton.addActionListener(e -> importModelFile());
        var openFolder = secondaryButton("Open model folder");
        openFolder.addActionListener(e -> openModelFolder());

        panel.add(status);
        panel.add(controls);
        panel.add(actionRow(importButton, openFolder));
        updateModelStatus();
        return panel;
    }

    private JPanel dataTab() {
        var panel = page();
        var text = new JTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setText(
            "Standalone desktop data is stored locally as TSV files.\n\n" +
                "Folder:\n" + store.dir().toAbsolutePath() + "\n\n" +
                "This app does not require Android, mobile services, or internet access."
        );
        text.setBorder(new EmptyBorder(12, 12, 12, 12));

        var export = primaryButton("Export backup");
        export.addActionListener(e -> exportBackup());
        var open = secondaryButton("Open data folder");
        open.addActionListener(e -> openDataFolder());

        panel.add(new JScrollPane(text));
        panel.add(actionRow(export, open));
        return panel;
    }

    private void addProduct() {
        var name = productName.getText().trim();
        if (name.isEmpty()) {
            showError("Product name is required.");
            return;
        }
        var product = new Product(
            UUID.randomUUID().toString(),
            name,
            productCategory.getText().trim(),
            money(productCost),
            money(productPrice),
            ((Number) productStock.getValue()).intValue(),
            productBarcode.getText().trim()
        );
        products.upsertByBarcode(product);
        clearProductForm();
        saveAndRefresh();
    }

    private void handleInventoryScan() {
        var barcode = inventoryScan.getText().trim();
        if (barcode.isBlank()) {
            return;
        }
        inventoryScan.setText("");
        productBarcode.setText(barcode);

        var existing = products.findByBarcode(barcode);
        if (existing.isEmpty()) {
            productName.requestFocusInWindow();
            JOptionPane.showMessageDialog(
                this,
                "Barcode captured. Fill product details, then add the product.",
                "Biashara AI Desktop",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        var product = existing.get();
        populateProductForm(product);
        var input = JOptionPane.showInputDialog(
            this,
            product.name() + " found. Quantity received to add:",
            "1"
        );
        if (input == null || input.trim().isBlank()) {
            return;
        }
        try {
            var received = Integer.parseInt(input.trim());
            if (received < 0) {
                showError("Quantity cannot be negative.");
                return;
            }
            if (received > 0) {
                products.adjustStock(product.id(), received);
                productStock.setValue(product.stock() + received);
                saveAndRefresh();
            }
        } catch (NumberFormatException ex) {
            showError("Quantity must be a whole number.");
        }
    }

    private void populateProductForm(Product product) {
        productName.setText(product.name());
        productCategory.setText(product.category());
        productBarcode.setText(product.barcode());
        productCost.setValue(product.cost().doubleValue());
        productPrice.setValue(product.price().doubleValue());
        productStock.setValue(product.stock());
    }

    private void clearProductForm() {
        productName.setText("");
        productCategory.setText("");
        productBarcode.setText("");
        productCost.setValue(0.0);
        productPrice.setValue(0.0);
        productStock.setValue(0);
    }

    private void addLedgerEntry() {
        var parsed = parseLedgerForm();
        if (parsed.isEmpty()) {
            return;
        }
        transactions.add(parsed.get());
        ledgerAmount.setText("");
        ledgerDescription.setText("");
        saveAndRefresh();
    }

    private Optional<Transaction> parseLedgerForm() {
        LocalDate date;
        try {
            date = LocalDate.parse(ledgerDate.getText().trim());
        } catch (DateTimeParseException ex) {
            showError("Use date format YYYY-MM-DD.");
            return Optional.empty();
        }
        var amount = parseMoney(ledgerAmount.getText());
        if (amount.signum() <= 0) {
            showError("Amount must be greater than zero.");
            return Optional.empty();
        }
        var description = ledgerDescription.getText().trim();
        if (description.isEmpty()) {
            showError("Description is required.");
            return Optional.empty();
        }
        return Optional.of(new Transaction(
            UUID.randomUUID().toString(),
            date,
            (TransactionType) ledgerType.getSelectedItem(),
            amount,
            description,
            ""
        ));
    }

    private void addCustomer() {
        var name = customerName.getText().trim();
        if (name.isEmpty()) {
            showError("Customer name is required.");
            return;
        }
        customers.add(new Customer(
            UUID.randomUUID().toString(),
            name,
            customerPhone.getText().trim(),
            customerNotes.getText().trim(),
            BigDecimal.ZERO
        ));
        customerName.setText("");
        customerPhone.setText("");
        customerNotes.setText("");
        saveAndRefresh();
    }

    private void addCartLine() {
        var selected = (ProductRef) posProduct.getSelectedItem();
        if (selected == null || selected.id().isBlank()) {
            showError("Choose a product.");
            return;
        }
        var product = products.findById(selected.id()).orElse(null);
        if (product == null) {
            showError("Product not found.");
            return;
        }
        var qty = ((Number) posQuantity.getValue()).intValue();
        if (!addProductToCart(product, qty)) {
            return;
        }
        posQuantity.setValue(1);
    }

    private void addScannedProductToCart() {
        var barcode = posScan.getText().trim();
        if (barcode.isBlank()) {
            return;
        }
        posScan.setText("");
        var product = products.findByBarcode(barcode).orElse(null);
        if (product == null) {
            showError("No inventory product uses barcode " + barcode + ".");
            return;
        }
        addProductToCart(product, 1);
    }

    private boolean addProductToCart(Product product, int qty) {
        var available = product.stock() - cart.quantityForProduct(product.id());
        if (qty > available) {
            showError("Only " + Math.max(available, 0) + " available after items already in the cart.");
            return false;
        }
        cart.add(new CartLine(product.id(), product.name(), qty, product.price()));
        updateCartTotal();
        return true;
    }

    private void recordSale() {
        if (cart.lines().isEmpty()) {
            showError("Cart is empty.");
            return;
        }
        var total = cart.total();
        var date = LocalDate.now();
        var customerId = "";
        var description = "POS sale";

        if (creditSale.isSelected()) {
            var customerRef = (CustomerRef) posCustomer.getSelectedItem();
            if (customerRef == null || customerRef.id().isBlank()) {
                showError("Choose a customer for a credit sale.");
                return;
            }
            customerId = customerRef.id();
            description = "Credit sale - " + customerRef.name();
            customers.adjustBalance(customerId, total);
            transactions.add(new Transaction(UUID.randomUUID().toString(), date, TransactionType.CREDIT, total, description, customerId));
        } else {
            transactions.add(new Transaction(UUID.randomUUID().toString(), date, TransactionType.INCOME, total, description, ""));
        }

        for (var line : cart.lines()) {
            products.adjustStock(line.productId(), -line.quantity());
        }
        cart.clear();
        updateCartTotal();
        saveAndRefresh();
    }

    private void applyDebtAction() {
        var selected = (CustomerRef) debtCustomer.getSelectedItem();
        if (selected == null || selected.id().isBlank()) {
            showError("Choose a customer.");
            return;
        }
        var amount = parseMoney(debtAmount.getText());
        if (amount.signum() <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }
        var action = String.valueOf(debtAction.getSelectedItem());
        if ("Record repayment".equals(action)) {
            customers.adjustBalance(selected.id(), amount.negate());
            transactions.add(new Transaction(
                UUID.randomUUID().toString(),
                LocalDate.now(),
                TransactionType.REPAYMENT,
                amount,
                "Debt repayment - " + selected.name(),
                selected.id()
            ));
        } else {
            customers.adjustBalance(selected.id(), amount);
            transactions.add(new Transaction(
                UUID.randomUUID().toString(),
                LocalDate.now(),
                TransactionType.CREDIT,
                amount,
                "Customer credit - " + selected.name(),
                selected.id()
            ));
        }
        debtAmount.setText("");
        saveAndRefresh();
    }

    private void answerAssistant() {
        var question = assistantQuestion.getText().trim();
        if (question.isBlank()) {
            question = "What should I do today?";
        }
        appendChat("Owner", question);
        var answer = mentionsModelSetup(question)
            ? modelManager.assistantStatus()
            : advisor.answer(
                question,
                transactions.rows(),
                products.rows(),
                customers.rows(),
                insights(),
                chatTurns
            );
        appendChat("Assistant", answer);
        assistantQuestion.setText("");
    }

    private boolean mentionsModelSetup(String question) {
        var q = question.toLowerCase(Locale.ROOT);
        return q.contains("model") || q.contains("download") || q.contains("install") ||
            q.contains("whisper") || q.contains("gguf") || q.contains("onnx");
    }

    private void refreshAll() {
        transactions.sortNewestFirst();
        products.fireTableDataChanged();
        customers.fireTableDataChanged();
        refreshCombos();
        var summary = Summary.from(transactions.rows(), products.rows(), customers.rows());
        revenueValue.setText(currency(summary.cashRevenue()));
        expenseValue.setText(currency(summary.expenses()));
        netValue.setText(currency(summary.netCash()));
        stockValue.setText(currency(summary.inventoryValue()));
        debtValue.setText(currency(summary.customerDebt()));
        lowStockValue.setText(String.valueOf(summary.lowStockCount()));
        dataPathLabel.setText(store.dir().toAbsolutePath().toString());
        refreshInsights();
        updateModelStatus();
    }

    private void refreshCombos() {
        posProduct.removeAllItems();
        posProduct.addItem(new ProductRef("", "Choose product", BigDecimal.ZERO, 0));
        for (var product : products.rows()) {
            posProduct.addItem(new ProductRef(product.id(), product.name(), product.price(), product.stock()));
        }
        posCustomer.removeAllItems();
        debtCustomer.removeAllItems();
        posCustomer.addItem(new CustomerRef("", "Choose customer"));
        debtCustomer.addItem(new CustomerRef("", "Choose customer"));
        for (var customer : customers.rows()) {
            var ref = new CustomerRef(customer.id(), customer.name());
            posCustomer.addItem(ref);
            debtCustomer.addItem(ref);
        }
    }

    private void updateCartTotal() {
        cartTotal.setText(currency(cart.total()));
    }

    private void saveAndRefresh() {
        try {
            store.save(products.rows(), transactions.rows(), customers.rows());
            refreshAll();
        } catch (IOException ex) {
            showError("Could not save data: " + ex.getMessage());
        }
    }

    private void exportBackup() {
        var chooser = new JFileChooser();
        chooser.setSelectedFile(Path.of("biasharaai-desktop-backup").toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            store.exportTo(chooser.getSelectedFile().toPath(), products.rows(), transactions.rows(), customers.rows());
            JOptionPane.showMessageDialog(this, "Backup exported.", "Biashara AI Desktop", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError("Could not export backup: " + ex.getMessage());
        }
    }

    private void openDataFolder() {
        try {
            Files.createDirectories(store.dir());
            Desktop.getDesktop().open(store.dir().toFile());
        } catch (Exception ex) {
            showError("Could not open data folder: " + ex.getMessage());
        }
    }

    private void importModelFile() {
        var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setDialogTitle("Import local AI model");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            var installed = modelManager.installFromFile(chooser.getSelectedFile().toPath());
            updateModelStatus();
            appendChat("Assistant", "Installed local AI model: " + installed.getFileName() + ".");
        } catch (IOException ex) {
            showError("Could not import model: " + ex.getMessage());
        }
    }

    private void startModelDownload() {
        var url = modelUrl.getText().trim();
        if (url.isBlank()) {
            showError("Paste a direct model download URL.");
            return;
        }
        if (modelDownloadWorker != null && !modelDownloadWorker.isDone()) {
            return;
        }

        modelDownloadButton.setEnabled(false);
        modelCancelButton.setEnabled(true);
        modelProgress.setIndeterminate(false);
        modelProgress.setValue(0);
        modelProgress.setString("Starting");

        modelDownloadWorker = new javax.swing.SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return modelManager.download(url, progress -> SwingUtilities.invokeLater(() -> applyModelProgress(progress)));
            }

            @Override
            protected void done() {
                try {
                    var path = get();
                    modelProgress.setIndeterminate(false);
                    modelProgress.setValue(100);
                    modelProgress.setString("Installed");
                    appendChat("Assistant", "Downloaded and installed AI model: " + path.getFileName() + ".");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    modelProgress.setString("Interrupted");
                } catch (java.util.concurrent.CancellationException ex) {
                    modelProgress.setString("Cancelled");
                } catch (Exception ex) {
                    var cause = ex.getCause() == null ? ex : ex.getCause();
                    modelProgress.setIndeterminate(false);
                    modelProgress.setString("Failed");
                    showError("Download failed: " + cause.getMessage());
                } finally {
                    modelDownloadButton.setEnabled(true);
                    modelCancelButton.setEnabled(false);
                    updateModelStatus();
                }
            }
        };
        modelDownloadWorker.execute();
    }

    private void cancelModelDownload() {
        if (modelDownloadWorker != null && !modelDownloadWorker.isDone()) {
            modelDownloadWorker.cancel(true);
        }
    }

    private void applyModelProgress(ModelDownloadProgress progress) {
        if (progress.totalBytes() > 0) {
            modelProgress.setIndeterminate(false);
            modelProgress.setValue(progress.percent());
            modelProgress.setString(
                progress.percent() + "% - " +
                    formatBytes(progress.downloadedBytes()) + " / " +
                    formatBytes(progress.totalBytes()) + " - " +
                    formatBytes(progress.bytesPerSecond()) + "/s"
            );
        } else {
            modelProgress.setIndeterminate(true);
            modelProgress.setString(formatBytes(progress.downloadedBytes()) + " - " + formatBytes(progress.bytesPerSecond()) + "/s");
        }
    }

    private void updateModelStatus() {
        var installed = modelManager.installed();
        if (installed.isPresent()) {
            var model = installed.get();
            modelStatus.setText("Installed: " + model.file().getFileName());
            modelDetail.setText(formatModelDetail(model.file(), model.source()));
        } else {
            modelStatus.setText("No local AI model installed");
            modelDetail.setText("Model folder: " + modelManager.modelsDir().toAbsolutePath());
        }
        var downloading = modelDownloadWorker != null && !modelDownloadWorker.isDone();
        if (!downloading) {
            modelProgress.setIndeterminate(false);
            modelProgress.setValue(installed.isPresent() ? 100 : 0);
            modelProgress.setString(installed.isPresent() ? "Ready" : "Idle");
        }
    }

    private String formatModelDetail(Path file, String source) {
        try {
            var detail = formatBytes(Files.size(file)) + " at " + file.toAbsolutePath();
            return source == null || source.isBlank() ? detail : detail + " | Source: " + source;
        } catch (IOException ex) {
            return file.toAbsolutePath().toString();
        }
    }

    private void openModelFolder() {
        try {
            Files.createDirectories(modelManager.modelsDir());
            Desktop.getDesktop().open(modelManager.modelsDir().toFile());
        } catch (Exception ex) {
            showError("Could not open model folder: " + ex.getMessage());
        }
    }

    private void deleteSelected(JTable table, RowBackedModel<?> model) {
        var selected = table.getSelectedRow();
        if (selected < 0) {
            return;
        }
        var row = table.convertRowIndexToModel(selected);
        model.remove(row);
        saveAndRefresh();
    }

    private JPanel page() {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACKGROUND);
        panel.setBorder(new EmptyBorder(16, 20, 20, 20));
        return panel;
    }

    private JPanel card(java.awt.LayoutManager layout) {
        var panel = new JPanel(layout);
        panel.setBackground(SURFACE);
        panel.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)), new EmptyBorder(14, 14, 14, 14)));
        return panel;
    }

    private JPanel tablePanel(String title, JTable table) {
        var panel = card(new BorderLayout(0, 8));
        panel.add(section(title), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel insightsPanel(String title) {
        var panel = card(new BorderLayout(0, 8));
        panel.add(section(title), BorderLayout.NORTH);
        var list = new JList<>(insightModel);
        list.setCellRenderer(new InsightRenderer());
        list.setVisibleRowCount(8);
        list.setFixedCellHeight(-1);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }

    private List<Insight> insights() {
        var rows = new ArrayList<Insight>();
        for (var i = 0; i < insightModel.size(); i++) {
            rows.add(insightModel.get(i));
        }
        return rows;
    }

    private void refreshInsights() {
        var rows = advisor.scan(transactions.rows(), products.rows(), customers.rows());
        insightModel.clear();
        for (var insight : rows) {
            insightModel.addElement(insight);
        }
    }

    private JButton promptButton(String text) {
        var button = secondaryButton(text);
        button.addActionListener(e -> {
            assistantQuestion.setText(text);
            answerAssistant();
        });
        return button;
    }

    private void appendChat(String role, String body) {
        var clean = body == null ? "" : body.trim();
        if (clean.isBlank()) {
            return;
        }
        chatTurns.add(new ChatTurn(role, clean));
        if (!assistantAnswer.getText().isBlank()) {
            assistantAnswer.append("\n\n");
        }
        assistantAnswer.append(role + "\n");
        assistantAnswer.append(clean);
        assistantAnswer.setCaretPosition(assistantAnswer.getDocument().getLength());
    }

    private JLabel section(String text) {
        var label = new JLabel(text);
        label.setForeground(INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        label.setBorder(new EmptyBorder(14, 0, 8, 0));
        return label;
    }

    private void addMetric(JPanel parent, GridBagConstraints c, int x, String label, JLabel value) {
        c.gridx = x;
        parent.add(metric(label, value), c);
    }

    private JPanel metric(String label, JLabel value) {
        var panel = card(new BorderLayout());
        var title = new JLabel(label);
        title.setForeground(MUTED);
        value.setForeground(INK);
        panel.add(title, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel metricValue() {
        var label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        return label;
    }

    private JTable table(AbstractTableModel model) {
        var table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return table;
    }

    private JPanel actionRow(Component... components) {
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        row.setOpaque(false);
        for (var component : components) {
            row.add(component);
        }
        return row;
    }

    private GridBagConstraints constraints() {
        var c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        return c;
    }

    private void addField(JPanel panel, GridBagConstraints base, int index, String label, Component field) {
        var c = (GridBagConstraints) base.clone();
        c.gridx = index * 2;
        c.gridy = 0;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = index * 2 + 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private JButton primaryButton(String text) {
        var button = new JButton(text);
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
    }

    private JButton secondaryButton(String text) {
        var button = new JButton(text);
        button.setFocusPainted(false);
        return button;
    }

    private JButton dangerButton(String text) {
        var button = new JButton(text);
        button.setForeground(DANGER);
        button.setFocusPainted(false);
        return button;
    }

    private static JSpinner moneySpinner() {
        return new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1_000_000_000.0, 10.0));
    }

    private static BigDecimal money(JSpinner spinner) {
        return BigDecimal.valueOf(((Number) spinner.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal parseMoney(String value) {
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String currency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        var value = (double) bytes;
        var units = new String[] {"KB", "MB", "GB", "TB"};
        var index = -1;
        do {
            value = value / 1024.0;
            index++;
        } while (value >= 1024.0 && index < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[index]);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Biashara AI Desktop", JOptionPane.ERROR_MESSAGE);
    }
}

enum TransactionType {
    INCOME,
    EXPENSE,
    RETURN,
    CREDIT,
    REPAYMENT
}

record Product(String id, String name, String category, BigDecimal cost, BigDecimal price, int stock, String barcode) {
}

record Transaction(String id, LocalDate date, TransactionType type, BigDecimal amount, String description, String customerId) {
}

record Customer(String id, String name, String phone, String notes, BigDecimal balance) {
}

record CartLine(String productId, String name, int quantity, BigDecimal price) {
    BigDecimal total() {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}

record ProductRef(String id, String name, BigDecimal price, int stock) {
    @Override
    public String toString() {
        return id.isBlank() ? name : name + " - " + price + " (" + stock + " left)";
    }
}

record CustomerRef(String id, String name) {
    @Override
    public String toString() {
        return name;
    }
}

record ChatTurn(String role, String text) {
}

record Insight(String priority, String title, String body, String action) {
    @Override
    public String toString() {
        return priority + " - " + title + "\n" + body + "\nAction: " + action;
    }
}

final class InsightRenderer extends JPanel implements ListCellRenderer<Insight> {
    private final JLabel priority = new JLabel();
    private final JLabel title = new JLabel();
    private final JTextArea body = new JTextArea();

    InsightRenderer() {
        setLayout(new BorderLayout(8, 4));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(Color.WHITE);
        priority.setOpaque(true);
        priority.setBorder(new EmptyBorder(3, 8, 3, 8));
        priority.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setForeground(new Color(71, 85, 105));
        add(priority, BorderLayout.WEST);
        var text = new JPanel(new BorderLayout(0, 3));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(body, BorderLayout.CENTER);
        add(text, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends Insight> list,
        Insight value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        var bg = isSelected ? new Color(239, 246, 255) : Color.WHITE;
        setBackground(bg);
        priority.setText(value.priority());
        priority.setBackground(priorityColor(value.priority()));
        title.setText(value.title());
        body.setText(value.body() + "\nAction: " + value.action());
        return this;
    }

    private Color priorityColor(String value) {
        return switch (value) {
            case "High" -> new Color(185, 28, 28);
            case "Medium" -> new Color(146, 64, 14);
            default -> new Color(37, 99, 235);
        };
    }
}

final class DesktopAdvisor {
    String welcome() {
        return "I am watching the local desktop records for cash flow, stock, customer debt, and sales patterns. Ask a question, or run an autonomous scan to get prioritized actions.";
    }

    List<Insight> scan(List<Transaction> txs, List<Product> products, List<Customer> customers) {
        var rows = new ArrayList<Insight>();
        var summary = Summary.from(txs, products, customers);
        var month = YearMonth.now();
        var monthTx = txs.stream().filter(tx -> YearMonth.from(tx.date()).equals(month)).toList();
        var monthSummary = Summary.from(monthTx, products, customers);

        if (summary.netCash().signum() < 0) {
            rows.add(new Insight(
                "High",
                "Cash flow is negative",
                "Recorded expenses are higher than cash revenue by " + currency(summary.netCash().abs()) + ".",
                "Delay non-urgent purchases and check the largest expense entries today."
            ));
        }

        var lowStock = products.stream()
            .filter(product -> product.stock() <= 3)
            .sorted(Comparator.comparingInt(Product::stock))
            .limit(6)
            .toList();
        if (!lowStock.isEmpty()) {
            rows.add(new Insight(
                "High",
                "Stock-out risk",
                lowStock.stream().map(p -> p.name() + " (" + p.stock() + ")").reduce((a, b) -> a + ", " + b).orElse("Low stock products found") + ".",
                "Reorder the products that still sell regularly before the next rush period."
            ));
        }

        if (summary.customerDebt().compareTo(BigDecimal.ZERO) > 0) {
            var topDebtor = customers.stream()
                .filter(customer -> customer.balance().signum() > 0)
                .max(Comparator.comparing(Customer::balance));
            rows.add(new Insight(
                summary.customerDebt().compareTo(summary.cashRevenue().multiply(BigDecimal.valueOf(0.25))) > 0 ? "High" : "Medium",
                "Customer debt needs follow-up",
                "Open customer credit totals " + currency(summary.customerDebt()) +
                    topDebtor.map(customer -> "; largest balance is " + customer.name() + " at " + currency(customer.balance())).orElse("") + ".",
                "Send repayment reminders and avoid adding new credit to overdue customers."
            ));
        }

        var weakMargins = products.stream()
            .filter(product -> product.price().compareTo(BigDecimal.ZERO) > 0)
            .filter(product -> product.price().subtract(product.cost()).compareTo(product.price().multiply(BigDecimal.valueOf(0.10))) < 0)
            .limit(5)
            .toList();
        if (!weakMargins.isEmpty()) {
            rows.add(new Insight(
                "Medium",
                "Thin product margins",
                weakMargins.stream().map(Product::name).reduce((a, b) -> a + ", " + b).orElse("Some products") + " have less than 10% gross margin.",
                "Review prices or supplier cost before selling more volume."
            ));
        }

        if (monthSummary.cashRevenue().signum() == 0 && !products.isEmpty()) {
            rows.add(new Insight(
                "Medium",
                "No cash sales this month",
                "There are products in inventory, but no cash revenue has been recorded in " + month + ".",
                "Use POS for every sale so the assistant can forecast and warn earlier."
            ));
        }

        if (txs.isEmpty() && products.isEmpty() && customers.isEmpty()) {
            rows.add(new Insight(
                "Low",
                "Start with setup",
                "No local business records are stored yet.",
                "Add products, record one sale, and add customers with outstanding balances."
            ));
        }

        if (rows.isEmpty()) {
            rows.add(new Insight(
                "Low",
                "Business records look stable",
                "No urgent local risk was detected from cash flow, debt, or stock levels.",
                "Keep recording sales and expenses daily so the model can catch changes."
            ));
        }
        return rows;
    }

    String autonomyReport(List<Insight> insights) {
        var out = new StringBuilder("Autonomous business review\n");
        var index = 1;
        for (var insight : insights) {
            out.append(index++)
                .append(". ")
                .append(insight.priority())
                .append(": ")
                .append(insight.title())
                .append(" - ")
                .append(insight.action())
                .append("\n");
        }
        return out.toString().trim();
    }

    String answer(
        String rawQuestion,
        List<Transaction> txs,
        List<Product> products,
        List<Customer> customers,
        List<Insight> insights,
        List<ChatTurn> chatTurns
    ) {
        var q = rawQuestion == null ? "" : rawQuestion.trim().toLowerCase(Locale.ROOT);
        var summary = Summary.from(txs, products, customers);
        if (q.contains("today") || q.contains("what should i do") || q.contains("priority")) {
            return autonomyReport(insights);
        }
        if (q.contains("low") || q.contains("stock") || q.contains("reorder")) {
            return stockAnswer(products);
        }
        if (q.contains("debt") || q.contains("credit") || q.contains("customer owe")) {
            return debtAnswer(customers, summary);
        }
        if (q.contains("expense") || q.contains("spend") || q.contains("cost")) {
            return expenseAnswer(txs, summary);
        }
        if (q.contains("month") || q.contains("perform")) {
            var ym = YearMonth.now();
            var month = Summary.from(txs.stream().filter(tx -> YearMonth.from(tx.date()).equals(ym)).toList(), products, customers);
            return ym + ": cash revenue " + currency(month.cashRevenue()) +
                ", expenses " + currency(month.expenses()) +
                ", net cash " + currency(month.netCash()) +
                ", customer debt " + currency(month.customerDebt()) + ".";
        }
        if (q.contains("profit") || q.contains("net") || q.contains("cash flow")) {
            return "Net cash is " + currency(summary.netCash()) + ". Cash revenue is " +
                currency(summary.cashRevenue()) + ", expenses are " + currency(summary.expenses()) +
                ", and open customer debt is " + currency(summary.customerDebt()) + ".";
        }
        if (q.contains("memory") || q.contains("remember")) {
            return "This desktop build keeps the current chat transcript in memory and keeps business records on disk. It uses the last " +
                Math.min(chatTurns.size(), 12) + " turns for conversational context while answering from local data.";
        }
        return "From local records: cash revenue " + currency(summary.cashRevenue()) +
            ", expenses " + currency(summary.expenses()) +
            ", net cash " + currency(summary.netCash()) +
            ", customer debt " + currency(summary.customerDebt()) +
            ", inventory value " + currency(summary.inventoryValue()) +
            ". Main recommendation: " + insights.stream().findFirst().map(Insight::action).orElse("keep records updated daily") + ".";
    }

    private String stockAnswer(List<Product> products) {
        var low = products.stream()
            .filter(product -> product.stock() <= 3)
            .sorted(Comparator.comparingInt(Product::stock))
            .map(product -> product.name() + " (" + product.stock() + " left)")
            .toList();
        if (low.isEmpty()) {
            return "No product is at or below 3 units. Keep POS recording active so stock falls automatically when sales are recorded.";
        }
        return "Reorder risk: " + String.join(", ", low) + ". Start with items that have both low stock and strong margins.";
    }

    private String debtAnswer(List<Customer> customers, Summary summary) {
        var debtors = customers.stream()
            .filter(customer -> customer.balance().signum() > 0)
            .sorted(Comparator.comparing(Customer::balance).reversed())
            .limit(5)
            .map(customer -> customer.name() + " " + currency(customer.balance()))
            .toList();
        if (debtors.isEmpty()) {
            return "No open customer debt is recorded.";
        }
        return "Open customer debt totals " + currency(summary.customerDebt()) + ". Priority follow-up: " + String.join(", ", debtors) + ".";
    }

    private String expenseAnswer(List<Transaction> txs, Summary summary) {
        var latest = txs.stream()
            .filter(tx -> tx.type() == TransactionType.EXPENSE)
            .sorted(Comparator.comparing(Transaction::date).reversed())
            .limit(5)
            .map(tx -> tx.date() + " " + tx.description() + " " + currency(tx.amount()))
            .toList();
        if (latest.isEmpty()) {
            return "No expenses are recorded yet.";
        }
        return "Expenses total " + currency(summary.expenses()) + ". Recent expenses: " + String.join("; ", latest) + ".";
    }

    private static String currency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(amount);
    }
}

record Summary(
    BigDecimal cashRevenue,
    BigDecimal expenses,
    BigDecimal inventoryValue,
    BigDecimal customerDebt,
    int lowStockCount
) {
    static Summary from(List<Transaction> txs, List<Product> products, List<Customer> customers) {
        var revenue = BigDecimal.ZERO;
        var expenses = BigDecimal.ZERO;
        for (var tx : txs) {
            if (tx.type() == TransactionType.INCOME || tx.type() == TransactionType.REPAYMENT || tx.type() == TransactionType.RETURN) {
                revenue = revenue.add(tx.amount());
            } else if (tx.type() == TransactionType.EXPENSE) {
                expenses = expenses.add(tx.amount());
            }
        }
        var stock = BigDecimal.ZERO;
        var low = 0;
        for (var product : products) {
            stock = stock.add(product.cost().multiply(BigDecimal.valueOf(product.stock())));
            if (product.stock() <= 3) {
                low++;
            }
        }
        var debt = BigDecimal.ZERO;
        for (var customer : customers) {
            if (customer.balance().signum() > 0) {
                debt = debt.add(customer.balance());
            }
        }
        return new Summary(revenue, expenses, stock, debt, low);
    }

    BigDecimal netCash() {
        return cashRevenue.subtract(expenses);
    }
}

interface RowBackedModel<T> {
    List<T> rows();

    void remove(int row);
}

final class ProductTableModel extends AbstractTableModel implements RowBackedModel<Product> {
    private final List<Product> rows;
    private final String[] columns = {"Name", "Category", "Cost", "Price", "Stock", "Barcode"};

    ProductTableModel(List<Product> rows) {
        this.rows = new ArrayList<>(rows);
    }

    @Override
    public List<Product> rows() {
        return List.copyOf(rows);
    }

    void add(Product product) {
        rows.add(Objects.requireNonNull(product));
        fireTableDataChanged();
    }

    void upsertByBarcode(Product product) {
        var incoming = Objects.requireNonNull(product);
        var barcode = incoming.barcode().trim();
        if (!barcode.isBlank()) {
            for (var i = 0; i < rows.size(); i++) {
                var current = rows.get(i);
                if (barcode.equals(current.barcode().trim())) {
                    rows.set(i, new Product(
                        current.id(),
                        incoming.name(),
                        incoming.category(),
                        incoming.cost(),
                        incoming.price(),
                        incoming.stock(),
                        barcode
                    ));
                    fireTableRowsUpdated(i, i);
                    return;
                }
            }
        }
        add(incoming);
    }

    Optional<Product> findById(String id) {
        return rows.stream().filter(row -> row.id().equals(id)).findFirst();
    }

    Optional<Product> findByBarcode(String barcode) {
        var clean = barcode == null ? "" : barcode.trim();
        if (clean.isBlank()) {
            return Optional.empty();
        }
        return rows.stream().filter(row -> clean.equals(row.barcode().trim())).findFirst();
    }

    void adjustStock(String productId, int delta) {
        for (var i = 0; i < rows.size(); i++) {
            var p = rows.get(i);
            if (p.id().equals(productId)) {
                rows.set(i, new Product(p.id(), p.name(), p.category(), p.cost(), p.price(), Math.max(0, p.stock() + delta), p.barcode()));
                fireTableRowsUpdated(i, i);
                return;
            }
        }
    }

    @Override
    public void remove(int row) {
        if (row >= 0 && row < rows.size()) {
            rows.remove(row);
            fireTableDataChanged();
        }
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var p = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> p.name();
            case 1 -> p.category();
            case 2 -> p.cost();
            case 3 -> p.price();
            case 4 -> p.stock();
            case 5 -> p.barcode();
            default -> "";
        };
    }
}

final class TransactionTableModel extends AbstractTableModel implements RowBackedModel<Transaction> {
    private final List<Transaction> rows;
    private final String[] columns = {"Date", "Type", "Amount", "Description"};

    TransactionTableModel(List<Transaction> rows) {
        this.rows = new ArrayList<>(rows);
        sortNewestFirst();
    }

    @Override
    public List<Transaction> rows() {
        return List.copyOf(rows);
    }

    void add(Transaction transaction) {
        rows.add(Objects.requireNonNull(transaction));
        sortNewestFirst();
        fireTableDataChanged();
    }

    void sortNewestFirst() {
        rows.sort(Comparator.comparing(Transaction::date).reversed());
    }

    @Override
    public void remove(int row) {
        if (row >= 0 && row < rows.size()) {
            rows.remove(row);
            fireTableDataChanged();
        }
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var tx = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> tx.date();
            case 1 -> tx.type();
            case 2 -> tx.amount();
            case 3 -> tx.description();
            default -> "";
        };
    }
}

final class CustomerTableModel extends AbstractTableModel implements RowBackedModel<Customer> {
    private final List<Customer> rows;
    private final String[] columns = {"Name", "Phone", "Notes", "Balance"};

    CustomerTableModel(List<Customer> rows) {
        this.rows = new ArrayList<>(rows);
    }

    @Override
    public List<Customer> rows() {
        return List.copyOf(rows);
    }

    void add(Customer customer) {
        rows.add(Objects.requireNonNull(customer));
        fireTableDataChanged();
    }

    void adjustBalance(String id, BigDecimal delta) {
        for (var i = 0; i < rows.size(); i++) {
            var c = rows.get(i);
            if (c.id().equals(id)) {
                rows.set(i, new Customer(c.id(), c.name(), c.phone(), c.notes(), c.balance().add(delta).setScale(2, RoundingMode.HALF_UP)));
                fireTableRowsUpdated(i, i);
                return;
            }
        }
    }

    @Override
    public void remove(int row) {
        if (row >= 0 && row < rows.size()) {
            rows.remove(row);
            fireTableDataChanged();
        }
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var c = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> c.name();
            case 1 -> c.phone();
            case 2 -> c.notes();
            case 3 -> c.balance();
            default -> "";
        };
    }
}

final class CartTableModel extends AbstractTableModel {
    private final List<CartLine> rows = new ArrayList<>();
    private final String[] columns = {"Item", "Qty", "Price", "Total"};

    List<CartLine> lines() {
        return List.copyOf(rows);
    }

    void add(CartLine line) {
        for (var i = 0; i < rows.size(); i++) {
            var current = rows.get(i);
            if (current.productId().equals(line.productId())) {
                rows.set(i, new CartLine(current.productId(), current.name(), current.quantity() + line.quantity(), current.price()));
                fireTableRowsUpdated(i, i);
                return;
            }
        }
        rows.add(line);
        fireTableDataChanged();
    }

    int quantityForProduct(String productId) {
        return rows.stream()
            .filter(line -> line.productId().equals(productId))
            .mapToInt(CartLine::quantity)
            .sum();
    }

    void clear() {
        rows.clear();
        fireTableDataChanged();
    }

    BigDecimal total() {
        var sum = BigDecimal.ZERO;
        for (var line : rows) {
            sum = sum.add(line.total());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var line = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> line.name();
            case 1 -> line.quantity();
            case 2 -> line.price();
            case 3 -> line.total();
            default -> "";
        };
    }
}

record ModelInstall(Path file, String source) {
}

record ModelDownloadProgress(long downloadedBytes, long totalBytes, int percent, long bytesPerSecond) {
}

interface ModelProgressSink {
    void onProgress(ModelDownloadProgress progress);
}

final class DesktopModelManager {
    private static final String METADATA_FILE = "model.properties";
    private static final int BUFFER_SIZE = 1024 * 128;

    private final Path modelsDir;
    private final Path metadataFile;

    DesktopModelManager(Path appDir) {
        this.modelsDir = appDir.resolve("models");
        this.metadataFile = modelsDir.resolve(METADATA_FILE);
    }

    Path modelsDir() {
        return modelsDir;
    }

    Optional<ModelInstall> installed() {
        if (!Files.exists(metadataFile)) {
            return Optional.empty();
        }
        var properties = new Properties();
        try (var input = new FileInputStream(metadataFile.toFile())) {
            properties.load(input);
            var fileName = safeFileName(properties.getProperty("fileName", ""));
            if (fileName.isBlank()) {
                return Optional.empty();
            }
            var file = modelsDir.resolve(fileName).normalize();
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            return Optional.of(new ModelInstall(file, properties.getProperty("source", "")));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    String assistantStatus() {
        var model = installed();
        if (model.isPresent()) {
            return "Local AI model is installed: " + model.get().file().getFileName() +
                ". It is stored at " + model.get().file().toAbsolutePath() +
                ". Use the AI Model tab to replace it, import a different file, or resume a direct URL download.";
        }
        return "No desktop AI model is installed yet. Open the AI Model tab, paste a direct model URL and use Download / resume, or import a local model file. Interrupted downloads keep a .part file and continue when the server supports byte-range resume.";
    }

    Path installFromFile(Path source) throws IOException {
        Files.createDirectories(modelsDir);
        var fileName = safeFileName(source.getFileName().toString());
        if (fileName.isBlank()) {
            throw new IOException("Model file has no usable name.");
        }
        var target = modelsDir.resolve(fileName).normalize();
        if (!source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        writeMetadata(target, "local file");
        return target;
    }

    Path download(String rawUrl, ModelProgressSink progressSink) throws IOException {
        Files.createDirectories(modelsDir);
        var fileName = fileNameFromUrl(rawUrl);
        var target = modelsDir.resolve(fileName).normalize();
        var partial = modelsDir.resolve(fileName + ".part").normalize();
        var existingBytes = Files.exists(partial) ? Files.size(partial) : 0L;

        HttpURLConnection connection = null;
        try {
            URL url = URI.create(rawUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "BiasharaAI-Desktop/1.0");
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
            }

            var status = connection.getResponseCode();
            if (status >= 400) {
                throw new IOException("Server returned HTTP " + status);
            }

            var append = existingBytes > 0 && status == HttpURLConnection.HTTP_PARTIAL;
            if (existingBytes > 0 && !append) {
                Files.deleteIfExists(partial);
                existingBytes = 0L;
            }

            var responseBytes = connection.getContentLengthLong();
            var totalBytes = responseBytes > 0 ? existingBytes + responseBytes : -1L;
            var downloadedBytes = existingBytes;
            var started = System.nanoTime();
            var lastProgress = started;
            if (progressSink != null) {
                progressSink.onProgress(progress(downloadedBytes, totalBytes, 0L));
            }

            var openOptions = append
                ? new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                : new StandardOpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

            try (var input = connection.getInputStream();
                 var output = Files.newOutputStream(partial, openOptions)) {
                var buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Download cancelled.");
                    }
                    output.write(buffer, 0, read);
                    downloadedBytes += read;
                    var now = System.nanoTime();
                    if (now - lastProgress > 350_000_000L && progressSink != null) {
                        progressSink.onProgress(progress(downloadedBytes, totalBytes, started));
                        lastProgress = now;
                    }
                }
            }

            if (progressSink != null) {
                progressSink.onProgress(progress(downloadedBytes, totalBytes, started));
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            writeMetadata(target, rawUrl);
            return target;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid model URL.", ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeMetadata(Path modelFile, String source) throws IOException {
        Files.createDirectories(modelsDir);
        var properties = new Properties();
        properties.setProperty("fileName", modelFile.getFileName().toString());
        properties.setProperty("source", source == null ? "" : source);
        properties.setProperty("installedAt", LocalDate.now().toString());
        try (var output = new FileOutputStream(metadataFile.toFile())) {
            properties.store(output, "Biashara AI Desktop model metadata");
        }
    }

    private static ModelDownloadProgress progress(long downloadedBytes, long totalBytes, long startedNanos) {
        var elapsed = startedNanos == 0L ? 0.0 : (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        var speed = elapsed <= 0.0 ? 0L : Math.round(downloadedBytes / elapsed);
        var percent = totalBytes > 0 ? (int) Math.min(100, Math.round(downloadedBytes * 100.0 / totalBytes)) : 0;
        return new ModelDownloadProgress(downloadedBytes, totalBytes, percent, speed);
    }

    private static String fileNameFromUrl(String rawUrl) {
        try {
            var uri = URI.create(rawUrl);
            var path = uri.getPath();
            if (path != null && !path.isBlank()) {
                var fileName = Path.of(path).getFileName();
                if (fileName != null) {
                    var clean = safeFileName(fileName.toString());
                    if (!clean.isBlank()) {
                        return clean;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // A clearer invalid URL error is raised when the connection is opened.
        }
        return "desktop-ai-model.bin";
    }

    private static String safeFileName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

final class DesktopDataStore {
    private static final String PRODUCTS_HEADER = "id\tname\tcategory\tcost\tprice\tstock\tbarcode";
    private static final String TX_HEADER = "id\tdate\ttype\tamount\tdescription\tcustomerId";
    private static final String CUSTOMERS_HEADER = "id\tname\tphone\tnotes\tbalance";

    private final Path dir;
    private final Path productsFile;
    private final Path transactionsFile;
    private final Path customersFile;

    private DesktopDataStore(Path dir) {
        this.dir = dir;
        this.productsFile = dir.resolve("products.tsv");
        this.transactionsFile = dir.resolve("transactions.tsv");
        this.customersFile = dir.resolve("customers.tsv");
    }

    static DesktopDataStore openDefault() {
        var home = Path.of(System.getProperty("user.home", "."));
        return new DesktopDataStore(home.resolve(".biasharaai-desktop-standalone"));
    }

    Path dir() {
        return dir;
    }

    List<Product> products() {
        return read(productsFile, PRODUCTS_HEADER).stream().map(DesktopDataStore::parseProduct).flatMap(Optional::stream).toList();
    }

    List<Transaction> transactions() {
        return read(transactionsFile, TX_HEADER).stream().map(DesktopDataStore::parseTransaction).flatMap(Optional::stream).toList();
    }

    List<Customer> customers() {
        return read(customersFile, CUSTOMERS_HEADER).stream().map(DesktopDataStore::parseCustomer).flatMap(Optional::stream).toList();
    }

    void save(List<Product> products, List<Transaction> transactions, List<Customer> customers) throws IOException {
        writeProducts(productsFile, products);
        writeTransactions(transactionsFile, transactions);
        writeCustomers(customersFile, customers);
    }

    void exportTo(Path targetDir, List<Product> products, List<Transaction> transactions, List<Customer> customers) throws IOException {
        Files.createDirectories(targetDir);
        writeProducts(targetDir.resolve("products.tsv"), products);
        writeTransactions(targetDir.resolve("transactions.tsv"), transactions);
        writeCustomers(targetDir.resolve("customers.tsv"), customers);
    }

    private static List<String[]> read(Path file, String header) {
        if (!Files.exists(file)) {
            return List.of();
        }
        var rows = new ArrayList<String[]>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            var first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (line.equals(header)) {
                        continue;
                    }
                }
                rows.add(split(line));
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return rows;
    }

    private static void writeProducts(Path path, List<Product> rows) throws IOException {
        write(path, PRODUCTS_HEADER, rows.stream().map(p -> new String[] {
            p.id(), p.name(), p.category(), p.cost().toPlainString(), p.price().toPlainString(),
            String.valueOf(p.stock()), p.barcode()
        }).toList());
    }

    private static void writeTransactions(Path path, List<Transaction> rows) throws IOException {
        write(path, TX_HEADER, rows.stream().map(tx -> new String[] {
            tx.id(), tx.date().toString(), tx.type().name(), tx.amount().toPlainString(),
            tx.description(), tx.customerId()
        }).toList());
    }

    private static void writeCustomers(Path path, List<Customer> rows) throws IOException {
        write(path, CUSTOMERS_HEADER, rows.stream().map(c -> new String[] {
            c.id(), c.name(), c.phone(), c.notes(), c.balance().toPlainString()
        }).toList());
    }

    private static void write(Path path, String header, List<String[]> rows) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(header);
            writer.newLine();
            for (var row : rows) {
                for (var i = 0; i < row.length; i++) {
                    if (i > 0) {
                        writer.write('\t');
                    }
                    writer.write(escape(row[i]));
                }
                writer.newLine();
            }
        }
    }

    private static Optional<Product> parseProduct(String[] row) {
        if (row.length < 7) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Product(
                row[0],
                row[1],
                row[2],
                new BigDecimal(row[3]).setScale(2, RoundingMode.HALF_UP),
                new BigDecimal(row[4]).setScale(2, RoundingMode.HALF_UP),
                Integer.parseInt(row[5]),
                row[6]
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<Transaction> parseTransaction(String[] row) {
        if (row.length < 6) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Transaction(
                row[0],
                LocalDate.parse(row[1]),
                TransactionType.valueOf(row[2]),
                new BigDecimal(row[3]).setScale(2, RoundingMode.HALF_UP),
                row[4],
                row[5]
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<Customer> parseCustomer(String[] row) {
        if (row.length < 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Customer(
                row[0],
                row[1],
                row[2],
                row[3],
                new BigDecimal(row[4]).setScale(2, RoundingMode.HALF_UP)
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String[] split(String line) {
        var raw = line.split("\t", -1);
        var out = new String[raw.length];
        for (var i = 0; i < raw.length; i++) {
            out[i] = unescape(raw[i]);
        }
        return out;
    }

    private static String escape(String value) {
        return value == null
            ? ""
            : value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        var out = new StringBuilder();
        var escaping = false;
        for (var i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            if (escaping) {
                switch (ch) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case '\\' -> out.append('\\');
                    default -> out.append(ch);
                }
                escaping = false;
            } else if (ch == '\\') {
                escaping = true;
            } else {
                out.append(ch);
            }
        }
        if (escaping) {
            out.append('\\');
        }
        return out.toString();
    }
}
