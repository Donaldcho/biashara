package com.biasharaai.desktop;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.UUID;

public final class BiasharaDesktopApp {
    private BiasharaDesktopApp() {
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default Swing look and feel is acceptable.
            }
            var store = new LedgerStore(defaultDataDir().resolve("ledger.tsv"));
            var frame = new BiasharaDesktopFrame(store);
            frame.setVisible(true);
        });
    }

    private static Path defaultDataDir() {
        var home = Path.of(System.getProperty("user.home", "."));
        return home.resolve(".biasharaai-desktop");
    }
}

final class BiasharaDesktopFrame extends JFrame {
    private static final Color INK = new Color(24, 32, 45);
    private static final Color MUTED = new Color(95, 105, 120);
    private static final Color SURFACE = new Color(247, 249, 252);
    private static final Color ACCENT = new Color(37, 99, 235);

    private final LedgerStore store;
    private final LedgerTableModel ledgerTableModel;
    private final PosCartTableModel cartTableModel = new PosCartTableModel();
    private final JLabel revenueValue = valueLabel();
    private final JLabel expenseValue = valueLabel();
    private final JLabel netValue = valueLabel();
    private final JLabel transactionsValue = valueLabel();
    private final JLabel dataPathLabel = new JLabel();
    private final JTextArea assistantAnswer = new JTextArea();

    private final JTextField ledgerDate = new JTextField(LocalDate.now().toString(), 10);
    private final JComboBox<EntryType> ledgerType = new JComboBox<>(EntryType.values());
    private final JTextField ledgerAmount = new JTextField(10);
    private final JTextField ledgerDescription = new JTextField(28);

    private final JTextField itemName = new JTextField(18);
    private final JSpinner itemQty = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner itemPrice = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1_000_000.0, 10.0));
    private final JLabel cartTotal = valueLabel();
    private final JTextField customerName = new JTextField(18);

    BiasharaDesktopFrame(LedgerStore store) {
        super("Biashara AI Desktop");
        this.store = store;
        this.ledgerTableModel = new LedgerTableModel(store.entries());

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));
        setLocationByPlatform(true);
        setContentPane(createContent());
        refreshAll();
        pack();
    }

    private JPanel createContent() {
        var root = new JPanel(new BorderLayout());
        root.setBackground(SURFACE);
        root.add(createHeader(), BorderLayout.NORTH);

        var tabs = new JTabbedPane();
        tabs.addTab("Dashboard", createDashboard());
        tabs.addTab("Ledger", createLedger());
        tabs.addTab("POS", createPos());
        tabs.addTab("Assistant", createAssistant());
        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    private JPanel createHeader() {
        var panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(16, 20, 12, 20));
        panel.setBackground(Color.WHITE);

        var title = new JLabel("Biashara AI Desktop");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(INK);

        dataPathLabel.setForeground(MUTED);
        dataPathLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        panel.add(title, BorderLayout.WEST);
        panel.add(dataPathLabel, BorderLayout.EAST);
        return panel;
    }

    private JPanel createDashboard() {
        var panel = contentPanel();
        var metrics = new JPanel(new GridBagLayout());
        metrics.setOpaque(false);

        var c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.gridy = 0;

        c.gridx = 0;
        metrics.add(metricCard("Revenue", revenueValue), c);
        c.gridx = 1;
        metrics.add(metricCard("Expenses", expenseValue), c);
        c.gridx = 2;
        metrics.add(metricCard("Net cash", netValue), c);
        c.gridx = 3;
        metrics.add(metricCard("Entries", transactionsValue), c);

        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        actions.setOpaque(false);
        var export = primaryButton("Export ledger");
        export.addActionListener(e -> exportLedger());
        var openData = secondaryButton("Open data folder");
        openData.addActionListener(e -> openDataFolder());
        actions.add(export);
        actions.add(openData);

        var recent = new JTable(ledgerTableModel);
        recent.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recent.setFillsViewportHeight(true);

        panel.add(metrics);
        panel.add(actions);
        panel.add(sectionTitle("Recent activity"));
        panel.add(new JScrollPane(recent));
        return panel;
    }

    private JPanel createLedger() {
        var panel = contentPanel();
        var form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(cardBorder());
        var c = fieldConstraints();

        addField(form, c, 0, "Date", ledgerDate);
        addField(form, c, 1, "Type", ledgerType);
        addField(form, c, 2, "Amount", ledgerAmount);
        addField(form, c, 3, "Description", ledgerDescription);

        var add = primaryButton("Add entry");
        add.addActionListener(e -> addLedgerEntry());
        c.gridx = 8;
        c.gridy = 0;
        c.gridheight = 2;
        c.anchor = GridBagConstraints.SOUTH;
        form.add(add, c);

        var table = new JTable(ledgerTableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        var delete = secondaryButton("Delete selected");
        delete.addActionListener(e -> {
            var row = table.getSelectedRow();
            if (row >= 0) {
                var modelRow = table.convertRowIndexToModel(row);
                ledgerTableModel.remove(modelRow);
                saveAndRefresh();
            }
        });
        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        actions.add(delete);

        panel.add(form);
        panel.add(actions);
        panel.add(new JScrollPane(table));
        return panel;
    }

    private JPanel createPos() {
        var panel = contentPanel();
        var form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(cardBorder());
        var c = fieldConstraints();

        addField(form, c, 0, "Item", itemName);
        addField(form, c, 1, "Qty", itemQty);
        addField(form, c, 2, "Price", itemPrice);

        var addItem = primaryButton("Add item");
        addItem.addActionListener(e -> addCartItem());
        c.gridx = 6;
        c.gridy = 0;
        c.gridheight = 2;
        form.add(addItem, c);

        var customerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        customerPanel.setOpaque(false);
        customerPanel.add(new JLabel("Customer"));
        customerPanel.add(customerName);
        customerPanel.add(new JLabel("Total"));
        customerPanel.add(cartTotal);

        var recordSale = primaryButton("Record sale");
        recordSale.addActionListener(e -> recordSale());
        var clear = secondaryButton("Clear cart");
        clear.addActionListener(e -> {
            cartTableModel.clear();
            updateCartTotal();
        });
        customerPanel.add(recordSale);
        customerPanel.add(clear);

        var table = new JTable(cartTableModel);
        table.setFillsViewportHeight(true);

        panel.add(form);
        panel.add(customerPanel);
        panel.add(new JScrollPane(table));
        return panel;
    }

    private JPanel createAssistant() {
        var panel = contentPanel();
        var question = new JTextField();
        var ask = primaryButton("Ask");
        ask.addActionListener(e -> answerQuestion(question.getText()));
        question.addActionListener(e -> answerQuestion(question.getText()));

        var input = new JPanel(new BorderLayout(8, 0));
        input.setOpaque(false);
        input.add(question, BorderLayout.CENTER);
        input.add(ask, BorderLayout.EAST);

        assistantAnswer.setEditable(false);
        assistantAnswer.setLineWrap(true);
        assistantAnswer.setWrapStyleWord(true);
        assistantAnswer.setFont(assistantAnswer.getFont().deriveFont(15f));
        assistantAnswer.setBorder(new EmptyBorder(12, 12, 12, 12));

        panel.add(input);
        panel.add(new JScrollPane(assistantAnswer));
        return panel;
    }

    private void addLedgerEntry() {
        var parsed = parseLedgerForm();
        if (parsed.isEmpty()) {
            return;
        }
        ledgerTableModel.add(parsed.get());
        ledgerAmount.setText("");
        ledgerDescription.setText("");
        saveAndRefresh();
    }

    private Optional<LedgerEntry> parseLedgerForm() {
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
        return Optional.of(new LedgerEntry(UUID.randomUUID().toString(), date, (EntryType) ledgerType.getSelectedItem(), amount, description));
    }

    private void addCartItem() {
        var name = itemName.getText().trim();
        if (name.isEmpty()) {
            showError("Item name is required.");
            return;
        }
        var qty = ((Number) itemQty.getValue()).intValue();
        var price = BigDecimal.valueOf(((Number) itemPrice.getValue()).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        if (price.signum() <= 0) {
            showError("Price must be greater than zero.");
            return;
        }
        cartTableModel.add(new CartItem(name, qty, price));
        itemName.setText("");
        itemQty.setValue(1);
        itemPrice.setValue(0.0);
        updateCartTotal();
    }

    private void recordSale() {
        if (cartTableModel.items().isEmpty()) {
            showError("Cart is empty.");
            return;
        }
        var total = cartTableModel.total();
        var customer = customerName.getText().trim();
        var label = customer.isEmpty() ? "POS sale" : "POS sale - " + customer;
        ledgerTableModel.add(new LedgerEntry(UUID.randomUUID().toString(), LocalDate.now(), EntryType.INCOME, total, label));
        cartTableModel.clear();
        customerName.setText("");
        updateCartTotal();
        saveAndRefresh();
    }

    private void answerQuestion(String raw) {
        var q = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        var summary = Summary.from(ledgerTableModel.entries());
        String answer;
        if (q.contains("profit") || q.contains("net")) {
            answer = "Net cash is " + money(summary.net()) + ". Revenue is " + money(summary.revenue()) + " and expenses are " + money(summary.expenses()) + ".";
        } else if (q.contains("expense") || q.contains("spend")) {
            answer = "Expenses total " + money(summary.expenses()) + " across " + summary.expenseCount() + " entries.";
        } else if (q.contains("sale") || q.contains("income") || q.contains("revenue")) {
            answer = "Revenue totals " + money(summary.revenue()) + " across " + summary.incomeCount() + " income entries.";
        } else if (q.contains("month")) {
            var ym = YearMonth.now();
            var monthSummary = Summary.from(ledgerTableModel.entries().stream().filter(e -> YearMonth.from(e.date()).equals(ym)).toList());
            answer = ym + ": revenue " + money(monthSummary.revenue()) + ", expenses " + money(monthSummary.expenses()) + ", net " + money(monthSummary.net()) + ".";
        } else {
            answer = "You have " + summary.count() + " ledger entries. Revenue: " + money(summary.revenue()) + ". Expenses: " + money(summary.expenses()) + ". Net: " + money(summary.net()) + ".";
        }
        assistantAnswer.setText(answer);
        assistantAnswer.setCaretPosition(0);
    }

    private void refreshAll() {
        ledgerTableModel.sortNewestFirst();
        var summary = Summary.from(ledgerTableModel.entries());
        revenueValue.setText(money(summary.revenue()));
        expenseValue.setText(money(summary.expenses()));
        netValue.setText(money(summary.net()));
        transactionsValue.setText(String.valueOf(summary.count()));
        dataPathLabel.setText(store.path().toAbsolutePath().toString());
        updateCartTotal();
    }

    private void saveAndRefresh() {
        try {
            store.save(ledgerTableModel.entries());
            refreshAll();
        } catch (IOException ex) {
            showError("Could not save ledger: " + ex.getMessage());
        }
    }

    private void exportLedger() {
        var chooser = new JFileChooser();
        chooser.setSelectedFile(Path.of("biasharaai-ledger-export.tsv").toFile());
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            LedgerStore.saveTo(chooser.getSelectedFile().toPath(), ledgerTableModel.entries());
        } catch (IOException ex) {
            showError("Could not export ledger: " + ex.getMessage());
        }
    }

    private void openDataFolder() {
        try {
            Files.createDirectories(store.path().getParent());
            Desktop.getDesktop().open(store.path().getParent().toFile());
        } catch (Exception ex) {
            showError("Could not open data folder: " + ex.getMessage());
        }
    }

    private void updateCartTotal() {
        cartTotal.setText(money(cartTableModel.total()));
    }

    private JPanel contentPanel() {
        var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(16, 20, 20, 20));
        return panel;
    }

    private JPanel metricCard(String title, JLabel value) {
        var panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(cardBorder());
        var label = new JLabel(title);
        label.setForeground(MUTED);
        value.setForeground(INK);
        panel.add(label, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel valueLabel() {
        var label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        return label;
    }

    private JLabel sectionTitle(String text) {
        var label = new JLabel(text);
        label.setAlignmentX(0f);
        label.setBorder(new EmptyBorder(12, 0, 8, 0));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        return label;
    }

    private EmptyBorder cardBorder() {
        return new EmptyBorder(14, 14, 14, 14);
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

    private GridBagConstraints fieldConstraints() {
        var c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        return c;
    }

    private void addField(JPanel panel, GridBagConstraints base, int fieldIndex, String label, java.awt.Component field) {
        var c = (GridBagConstraints) base.clone();
        c.gridx = fieldIndex * 2;
        c.gridy = 0;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = fieldIndex * 2 + 1;
        c.gridy = 0;
        c.weightx = 1;
        panel.add(field, c);
    }

    private BigDecimal parseMoney(String raw) {
        try {
            return new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private String money(BigDecimal value) {
        var nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
        return nf.format(value);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Biashara AI Desktop", JOptionPane.ERROR_MESSAGE);
    }
}

enum EntryType {
    INCOME,
    EXPENSE
}

record LedgerEntry(String id, LocalDate date, EntryType type, BigDecimal amount, String description) {
}

record CartItem(String name, int quantity, BigDecimal price) {
    BigDecimal total() {
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}

record Summary(BigDecimal revenue, BigDecimal expenses, int incomeCount, int expenseCount, int count) {
    static Summary from(List<LedgerEntry> entries) {
        var revenue = BigDecimal.ZERO;
        var expenses = BigDecimal.ZERO;
        var incomeCount = 0;
        var expenseCount = 0;
        for (var entry : entries) {
            if (entry.type() == EntryType.INCOME) {
                revenue = revenue.add(entry.amount());
                incomeCount++;
            } else {
                expenses = expenses.add(entry.amount());
                expenseCount++;
            }
        }
        return new Summary(revenue, expenses, incomeCount, expenseCount, entries.size());
    }

    BigDecimal net() {
        return revenue.subtract(expenses);
    }
}

final class LedgerTableModel extends AbstractTableModel {
    private final List<LedgerEntry> entries;
    private final String[] columns = {"Date", "Type", "Amount", "Description"};

    LedgerTableModel(List<LedgerEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    void add(LedgerEntry entry) {
        entries.add(Objects.requireNonNull(entry));
        sortNewestFirst();
        fireTableDataChanged();
    }

    void remove(int index) {
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            fireTableDataChanged();
        }
    }

    void sortNewestFirst() {
        entries.sort(Comparator.comparing(LedgerEntry::date).reversed());
    }

    @Override
    public int getRowCount() {
        return entries.size();
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
        var entry = entries.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> entry.date();
            case 1 -> entry.type();
            case 2 -> entry.amount();
            case 3 -> entry.description();
            default -> "";
        };
    }
}

final class PosCartTableModel extends AbstractTableModel {
    private final List<CartItem> items = new ArrayList<>();
    private final String[] columns = {"Item", "Qty", "Price", "Total"};

    List<CartItem> items() {
        return List.copyOf(items);
    }

    void add(CartItem item) {
        items.add(item);
        fireTableDataChanged();
    }

    void clear() {
        items.clear();
        fireTableDataChanged();
    }

    BigDecimal total() {
        var sum = BigDecimal.ZERO;
        for (var item : items) {
            sum = sum.add(item.total());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public int getRowCount() {
        return items.size();
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
        var item = items.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> item.name();
            case 1 -> item.quantity();
            case 2 -> item.price();
            case 3 -> item.total();
            default -> "";
        };
    }
}

final class LedgerStore {
    private static final String HEADER = "id\tdate\ttype\tamount\tdescription";
    private final Path path;

    LedgerStore(Path path) {
        this.path = path;
    }

    Path path() {
        return path;
    }

    List<LedgerEntry> entries() {
        if (!Files.exists(path)) {
            return List.of();
        }
        var entries = new ArrayList<LedgerEntry>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            var first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (line.equals(HEADER)) {
                        continue;
                    }
                }
                parse(line).ifPresent(entries::add);
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return entries;
    }

    void save(List<LedgerEntry> entries) throws IOException {
        saveTo(path, entries);
    }

    static void saveTo(Path output, List<LedgerEntry> entries) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (var entry : entries) {
                writer.write(escape(entry.id()));
                writer.write('\t');
                writer.write(entry.date().toString());
                writer.write('\t');
                writer.write(entry.type().name());
                writer.write('\t');
                writer.write(entry.amount().toPlainString());
                writer.write('\t');
                writer.write(escape(entry.description()));
                writer.newLine();
            }
        }
    }

    private static Optional<LedgerEntry> parse(String line) {
        var parts = line.split("\t", -1);
        if (parts.length < 5) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                new LedgerEntry(
                    unescape(parts[0]),
                    LocalDate.parse(parts[1]),
                    EntryType.valueOf(parts[2]),
                    new BigDecimal(parts[3]).setScale(2, RoundingMode.HALF_UP),
                    unescape(parts[4])
                )
            );
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
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
