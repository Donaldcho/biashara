package com.biasharaai.desktop.v2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

final class SqliteDesktopStateRepository implements DesktopStateRepository {
    static final String DATABASE_FILE = "biashara-desktop.db";
    private static final int SCHEMA_VERSION = 1;

    private final Path databasePath;

    SqliteDesktopStateRepository(Path dataDir) {
        this.databasePath = dataDir.resolve(DATABASE_FILE).toAbsolutePath().normalize();
    }

    @Override
    public boolean exists() {
        return Files.isRegularFile(databasePath);
    }

    @Override
    public synchronized AppState load(Path dataDir) {
        AppState state = new AppState(dataDir);
        try (Connection connection = open()) {
            initialize(connection);
            loadSettings(connection, state.settings);
            loadProducts(connection, state);
            loadServices(connection, state);
            loadCustomers(connection, state);
            loadTransactions(connection, state);
            loadSaleLines(connection, state);
            loadServiceTickets(connection, state);
            loadScans(connection, state);
            loadStockSync(connection, state);
            loadProductSync(connection, state);
            loadSyncInbox(connection, state);
            loadStockMovements(connection, state);
            return state;
        } catch (SQLException ex) {
            throw new DesktopPersistenceException("Could not load desktop SQLite data from " + databasePath, ex);
        }
    }

    @Override
    public synchronized void save(AppState state) {
        try (Connection connection = open()) {
            initialize(connection);
            connection.setAutoCommit(false);
            try {
                replaceSettings(connection, state.settings);
                replaceProducts(connection, state.products);
                replaceServices(connection, state.services);
                replaceCustomers(connection, state.customers);
                replaceTransactions(connection, state.transactions);
                replaceSaleLines(connection, state.saleLines);
                replaceServiceTickets(connection, state.serviceTickets);
                replaceScans(connection, state.scanEvents);
                replaceStockSync(connection, state.stockSyncItems);
                replaceProductSync(connection, state.productSyncItems);
                replaceSyncInbox(connection, state.syncInboxEntries);
                replaceStockMovements(connection, state.stockMovements);
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                rollbackQuietly(connection);
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new DesktopPersistenceException("Could not save desktop SQLite data to " + databasePath, ex);
        }
    }

    @Override
    public synchronized void backupTo(Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        try {
            if (normalized.getParent() != null) {
                Files.createDirectories(normalized.getParent());
            }
            Files.deleteIfExists(normalized);
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                initialize(connection);
                String escaped = normalized.toString().replace("'", "''");
                statement.execute("VACUUM INTO '" + escaped + "'");
            }
        } catch (IOException | SQLException ex) {
            throw new DesktopPersistenceException("Could not create a consistent SQLite backup", ex);
        }
    }

    Path databasePath() {
        return databasePath;
    }

    private Connection open() throws SQLException {
        try {
            Files.createDirectories(databasePath.getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (IOException | ClassNotFoundException ex) {
            throw new DesktopPersistenceException("SQLite runtime is not available", ex);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int currentVersion = 0;
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                if (version.next()) {
                    currentVersion = version.getInt(1);
                }
            }
            if (currentVersion > SCHEMA_VERSION) {
                throw new SQLException("Desktop database schema " + currentVersion + " is newer than supported schema " + SCHEMA_VERSION);
            }
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = FULL");
            statement.execute("CREATE TABLE IF NOT EXISTS app_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS products (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, sku TEXT NOT NULL, barcode TEXT NOT NULL, category TEXT NOT NULL, image_path TEXT NOT NULL, whatsapp_retailer_id TEXT NOT NULL, whatsapp_image_url TEXT NOT NULL, whatsapp_product_url TEXT NOT NULL, price_cents INTEGER NOT NULL, cost_cents INTEGER NOT NULL, stock INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name COLLATE NOCASE)");
            statement.execute("CREATE TABLE IF NOT EXISTS services (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, category TEXT NOT NULL, price_cents INTEGER NOT NULL, price_mode TEXT NOT NULL, duration_minutes INTEGER NOT NULL, warranty_days INTEGER NOT NULL, visible_in_kiosk INTEGER NOT NULL, updated_at INTEGER NOT NULL, source_device TEXT NOT NULL, mobile_service_id TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_services_name ON services(name COLLATE NOCASE)");
            statement.execute("CREATE TABLE IF NOT EXISTS customers (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, balance_cents INTEGER NOT NULL, visits INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name COLLATE NOCASE)");
            statement.execute("CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, created_at TEXT NOT NULL, type TEXT NOT NULL, customer_id TEXT NOT NULL, customer_name TEXT NOT NULL, description TEXT NOT NULL, payment_method TEXT NOT NULL, subtotal_cents INTEGER NOT NULL, tax_cents INTEGER NOT NULL, total_cents INTEGER NOT NULL, paid_cents INTEGER NOT NULL, balance_cents INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC)");
            statement.execute("CREATE TABLE IF NOT EXISTS sale_lines (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, transaction_id TEXT NOT NULL, kind TEXT NOT NULL, item_id TEXT NOT NULL, name TEXT NOT NULL, barcode TEXT NOT NULL, category TEXT NOT NULL, quantity INTEGER NOT NULL, unit_cents INTEGER NOT NULL, line_total_cents INTEGER NOT NULL, staff_name TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sale_lines_transaction ON sale_lines(transaction_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS service_tickets (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, token TEXT NOT NULL, created_at TEXT NOT NULL, started_at TEXT, completed_at TEXT, status TEXT NOT NULL, transaction_id TEXT NOT NULL, customer_id TEXT NOT NULL, customer_name TEXT NOT NULL, customer_phone TEXT NOT NULL, service_id TEXT NOT NULL, service_name TEXT NOT NULL, category TEXT NOT NULL, quantity INTEGER NOT NULL, unit_cents INTEGER NOT NULL, total_cents INTEGER NOT NULL, paid_cents INTEGER NOT NULL, payment_method TEXT NOT NULL, assigned_technician TEXT NOT NULL, active_technician TEXT NOT NULL, requirements TEXT NOT NULL, completion_notes TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_service_tickets_token ON service_tickets(token)");
            statement.execute("CREATE TABLE IF NOT EXISTS scan_events (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, created_at TEXT NOT NULL, source_device TEXT NOT NULL, kind TEXT NOT NULL, raw_value TEXT NOT NULL, status TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS stock_sync (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, created_at TEXT NOT NULL, source_device TEXT NOT NULL, product_id TEXT NOT NULL, product_name TEXT NOT NULL, barcode TEXT NOT NULL, category TEXT NOT NULL, quantity INTEGER NOT NULL, price_cents INTEGER NOT NULL, cost_cents INTEGER NOT NULL, image_path TEXT NOT NULL, status TEXT NOT NULL, mobile_product_id TEXT NOT NULL, stock_base_known INTEGER NOT NULL, stock_base INTEGER NOT NULL, source_stock INTEGER NOT NULL, mutation_id TEXT NOT NULL)");
            statement.execute("DROP INDEX IF EXISTS idx_stock_sync_mutation");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_sync_mutation_lookup ON stock_sync(mutation_id) WHERE mutation_id <> ''");
            statement.execute("CREATE TABLE IF NOT EXISTS product_sync (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, created_at TEXT NOT NULL, source_device TEXT NOT NULL, mobile_product_id TEXT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL, sku TEXT NOT NULL, barcode TEXT NOT NULL, category TEXT NOT NULL, stock INTEGER NOT NULL, price_cents INTEGER NOT NULL, cost_cents INTEGER NOT NULL, image_path TEXT NOT NULL, whatsapp_retailer_id TEXT NOT NULL, whatsapp_image_url TEXT NOT NULL, whatsapp_product_url TEXT NOT NULL, status TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_product_sync_mobile_id ON product_sync(source_device, mobile_product_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS sync_inbox (operation_id TEXT PRIMARY KEY, operation_type TEXT NOT NULL, source_device TEXT NOT NULL, received_at TEXT NOT NULL, payload_hash TEXT NOT NULL, http_status INTEGER NOT NULL, response_json TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sync_inbox_received_at ON sync_inbox(received_at DESC)");
            statement.execute("CREATE TABLE IF NOT EXISTS stock_movements (id TEXT PRIMARY KEY, sort_order INTEGER NOT NULL, operation_id TEXT NOT NULL, occurred_at TEXT NOT NULL, source_device TEXT NOT NULL, product_id TEXT NOT NULL, quantity_delta INTEGER NOT NULL, resulting_stock INTEGER NOT NULL, reason TEXT NOT NULL, reference_id TEXT NOT NULL)");
            statement.execute("DROP INDEX IF EXISTS idx_stock_movement_operation");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_movement_operation_lookup ON stock_movements(operation_id, product_id)");
            if (currentVersion < SCHEMA_VERSION) {
                statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            }
        }
    }

    private void loadSettings(Connection connection, Settings settings) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT key, value FROM settings")) {
            while (rows.next()) {
                String value = rows.getString(2);
                switch (rows.getString(1)) {
                    case "businessName" -> settings.businessName = value;
                    case "ownerName" -> settings.ownerName = value;
                    case "currency" -> settings.currency = value;
                    case "taxBasisPoints" -> settings.taxBasisPoints = parseLong(value);
                    case "receiptFooter" -> settings.receiptFooter = value;
                    case "modelPath" -> settings.modelPath = value;
                    case "aiProvider" -> settings.aiProvider = value;
                    case "lmStudioBaseUrl" -> settings.lmStudioBaseUrl = value;
                    case "lmStudioModel" -> settings.lmStudioModel = value;
                    case "phoneBridgeEnabled" -> settings.phoneBridgeEnabled = Boolean.parseBoolean(value);
                    case "whatsappPhoneNumberId" -> settings.whatsappPhoneNumberId = value;
                    case "whatsappCatalogId" -> settings.whatsappCatalogId = value;
                    case "whatsappDefaultCountryCode" -> settings.whatsappDefaultCountryCode = value;
                    case "whatsappAccessToken" -> settings.whatsappAccessToken = value;
                    case "whatsappGraphVersion" -> settings.whatsappGraphVersion = value;
                    case "settingsSyncFingerprint" -> settings.settingsSyncFingerprint = value;
                    default -> { }
                }
            }
        }
    }

    private void loadProducts(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,name,description,sku,barcode,category,image_path,whatsapp_retailer_id,whatsapp_image_url,whatsapp_product_url,price_cents,cost_cents,stock FROM products ORDER BY sort_order")) {
            while (r.next()) state.products.add(new Product(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getString(9), r.getString(10), r.getLong(11), r.getLong(12), r.getInt(13)));
        }
    }

    private void loadServices(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,name,description,category,price_cents,price_mode,duration_minutes,warranty_days,visible_in_kiosk,updated_at,source_device,mobile_service_id FROM services ORDER BY sort_order")) {
            while (r.next()) state.services.add(new ServiceItem(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getLong(5), r.getString(6), r.getInt(7), r.getInt(8), r.getInt(9) != 0, r.getLong(10), r.getString(11), r.getString(12)));
        }
    }

    private void loadCustomers(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,name,phone,balance_cents,visits FROM customers ORDER BY sort_order")) {
            while (r.next()) state.customers.add(new Customer(r.getString(1), r.getString(2), r.getString(3), r.getLong(4), r.getInt(5)));
        }
    }

    private void loadTransactions(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,created_at,type,customer_id,customer_name,description,payment_method,subtotal_cents,tax_cents,total_cents,paid_cents,balance_cents FROM transactions ORDER BY sort_order")) {
            while (r.next()) state.transactions.add(new Transaction(r.getString(1), Instant.parse(r.getString(2)), TransactionType.valueOf(r.getString(3)), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getLong(8), r.getLong(9), r.getLong(10), r.getLong(11), r.getLong(12)));
        }
    }

    private void loadSaleLines(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,transaction_id,kind,item_id,name,barcode,category,quantity,unit_cents,line_total_cents,staff_name FROM sale_lines ORDER BY sort_order")) {
            while (r.next()) state.saleLines.add(new DesktopSaleLine(r.getString(1), r.getString(2), CartLine.Kind.valueOf(r.getString(3)), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getInt(8), r.getLong(9), r.getLong(10), r.getString(11)));
        }
    }

    private void loadServiceTickets(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,token,created_at,started_at,completed_at,status,transaction_id,customer_id,customer_name,customer_phone,service_id,service_name,category,quantity,unit_cents,total_cents,paid_cents,payment_method,assigned_technician,active_technician,requirements,completion_notes FROM service_tickets ORDER BY sort_order")) {
            while (r.next()) state.serviceTickets.add(new ServiceTicket(r.getString(1), r.getString(2), Instant.parse(r.getString(3)), instantOrNull(r.getString(4)), instantOrNull(r.getString(5)), ServiceTicket.Status.valueOf(r.getString(6)), r.getString(7), r.getString(8), r.getString(9), r.getString(10), r.getString(11), r.getString(12), r.getString(13), r.getInt(14), r.getLong(15), r.getLong(16), r.getLong(17), r.getString(18), r.getString(19), r.getString(20), r.getString(21), r.getString(22)));
        }
    }

    private void loadScans(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,created_at,source_device,kind,raw_value,status FROM scan_events ORDER BY sort_order")) {
            while (r.next()) state.scanEvents.add(new ScanEvent(r.getString(1), Instant.parse(r.getString(2)), r.getString(3), r.getString(4), r.getString(5), r.getString(6)));
        }
    }

    private void loadStockSync(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,created_at,source_device,product_id,product_name,barcode,category,quantity,price_cents,cost_cents,image_path,status,mobile_product_id,stock_base_known,stock_base,source_stock,mutation_id FROM stock_sync ORDER BY sort_order")) {
            while (r.next()) {
                StockSyncItem item = new StockSyncItem(r.getString(1), Instant.parse(r.getString(2)), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getInt(8), r.getLong(9), r.getLong(10), r.getString(11), "", "", r.getString(12));
                item.mobileProductId = r.getString(13);
                item.stockBaseKnown = r.getInt(14) != 0;
                item.stockBase = r.getInt(15);
                item.sourceStock = r.getInt(16);
                item.mutationId = r.getString(17);
                state.stockSyncItems.add(item);
            }
        }
    }

    private void loadProductSync(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,created_at,source_device,mobile_product_id,name,description,sku,barcode,category,stock,price_cents,cost_cents,image_path,whatsapp_retailer_id,whatsapp_image_url,whatsapp_product_url,status FROM product_sync ORDER BY sort_order")) {
            while (r.next()) state.productSyncItems.add(new ProductSyncItem(r.getString(1), Instant.parse(r.getString(2)), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8), r.getString(9), r.getInt(10), r.getLong(11), r.getLong(12), r.getString(13), "", "", r.getString(14), r.getString(15), r.getString(16), r.getString(17)));
        }
    }

    private void loadSyncInbox(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT operation_id,operation_type,source_device,received_at,payload_hash,http_status,response_json FROM sync_inbox ORDER BY received_at")) {
            while (r.next()) state.syncInboxEntries.add(new SyncInboxEntry(r.getString(1), r.getString(2), r.getString(3), Instant.parse(r.getString(4)), r.getString(5), r.getInt(6), r.getString(7)));
        }
    }

    private void loadStockMovements(Connection c, AppState state) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT id,operation_id,occurred_at,source_device,product_id,quantity_delta,resulting_stock,reason,reference_id FROM stock_movements ORDER BY sort_order")) {
            while (r.next()) state.stockMovements.add(new StockMovement(r.getString(1), r.getString(2), Instant.parse(r.getString(3)), r.getString(4), r.getString(5), r.getInt(6), r.getInt(7), r.getString(8), r.getString(9)));
        }
    }

    private void replaceSettings(Connection c, Settings s) throws SQLException {
        delete(c, "settings");
        try (PreparedStatement p = c.prepareStatement("INSERT INTO settings(key,value) VALUES(?,?)")) {
            setting(p, "businessName", s.businessName); setting(p, "ownerName", s.ownerName);
            setting(p, "currency", s.currency); setting(p, "taxBasisPoints", Long.toString(s.taxBasisPoints));
            setting(p, "receiptFooter", s.receiptFooter); setting(p, "modelPath", s.modelPath);
            setting(p, "aiProvider", s.aiProvider); setting(p, "lmStudioBaseUrl", s.lmStudioBaseUrl);
            setting(p, "lmStudioModel", s.lmStudioModel); setting(p, "phoneBridgeEnabled", Boolean.toString(s.phoneBridgeEnabled));
            setting(p, "whatsappPhoneNumberId", s.whatsappPhoneNumberId); setting(p, "whatsappCatalogId", s.whatsappCatalogId);
            setting(p, "whatsappDefaultCountryCode", s.whatsappDefaultCountryCode); setting(p, "whatsappAccessToken", s.whatsappAccessToken);
            setting(p, "whatsappGraphVersion", s.whatsappGraphVersion); setting(p, "settingsSyncFingerprint", s.settingsSyncFingerprint);
            p.executeBatch();
        }
    }

    private void replaceProducts(Connection c, List<Product> rows) throws SQLException {
        replace(c, "products", "INSERT INTO products(id,sort_order,name,description,sku,barcode,category,image_path,whatsapp_retailer_id,whatsapp_image_url,whatsapp_product_url,price_cents,cost_cents,stock) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows, (p, v, i) -> { p.setString(1,v.id);p.setInt(2,i);p.setString(3,text(v.name));p.setString(4,text(v.description));p.setString(5,text(v.sku));p.setString(6,text(v.barcode));p.setString(7,text(v.category));p.setString(8,text(v.imagePath));p.setString(9,text(v.whatsappRetailerId));p.setString(10,text(v.whatsappImageUrl));p.setString(11,text(v.whatsappProductUrl));p.setLong(12,v.priceCents);p.setLong(13,v.costCents);p.setInt(14,v.stock); });
    }

    private void replaceServices(Connection c, List<ServiceItem> rows) throws SQLException {
        replace(c, "services", "INSERT INTO services(id,sort_order,name,description,category,price_cents,price_mode,duration_minutes,warranty_days,visible_in_kiosk,updated_at,source_device,mobile_service_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", rows, (p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,text(v.name));p.setString(4,text(v.description));p.setString(5,text(v.category));p.setLong(6,v.priceCents);p.setString(7,text(v.priceMode));p.setInt(8,v.durationMinutes);p.setInt(9,v.warrantyDays);p.setInt(10,v.visibleInKiosk?1:0);p.setLong(11,v.updatedAt);p.setString(12,text(v.sourceDevice));p.setString(13,text(v.mobileServiceId));});
    }

    private void replaceCustomers(Connection c, List<Customer> rows) throws SQLException {
        replace(c,"customers","INSERT INTO customers(id,sort_order,name,phone,balance_cents,visits) VALUES(?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,text(v.name));p.setString(4,text(v.phone));p.setLong(5,v.balanceCents);p.setInt(6,v.visits);});
    }

    private void replaceTransactions(Connection c, List<Transaction> rows) throws SQLException {
        replace(c,"transactions","INSERT INTO transactions(id,sort_order,created_at,type,customer_id,customer_name,description,payment_method,subtotal_cents,tax_cents,total_cents,paid_cents,balance_cents) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,v.createdAt.toString());p.setString(4,v.type.name());p.setString(5,text(v.customerId));p.setString(6,text(v.customerName));p.setString(7,text(v.description));p.setString(8,text(v.paymentMethod));p.setLong(9,v.subtotalCents);p.setLong(10,v.taxCents);p.setLong(11,v.totalCents);p.setLong(12,v.paidCents);p.setLong(13,v.balanceCents);});
    }

    private void replaceSaleLines(Connection c, List<DesktopSaleLine> rows) throws SQLException {
        replace(c,"sale_lines","INSERT INTO sale_lines(id,sort_order,transaction_id,kind,item_id,name,barcode,category,quantity,unit_cents,line_total_cents,staff_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,text(v.transactionId));p.setString(4,v.kind.name());p.setString(5,text(v.itemId));p.setString(6,text(v.name));p.setString(7,text(v.barcode));p.setString(8,text(v.category));p.setInt(9,v.quantity);p.setLong(10,v.unitCents);p.setLong(11,v.lineTotalCents);p.setString(12,text(v.staffName));});
    }

    private void replaceServiceTickets(Connection c, List<ServiceTicket> rows) throws SQLException {
        replace(c,"service_tickets","INSERT INTO service_tickets(id,sort_order,token,created_at,started_at,completed_at,status,transaction_id,customer_id,customer_name,customer_phone,service_id,service_name,category,quantity,unit_cents,total_cents,paid_cents,payment_method,assigned_technician,active_technician,requirements,completion_notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,text(v.token));p.setString(4,v.createdAt.toString());p.setString(5,instant(v.startedAt));p.setString(6,instant(v.completedAt));p.setString(7,v.status.name());p.setString(8,text(v.transactionId));p.setString(9,text(v.customerId));p.setString(10,text(v.customerName));p.setString(11,text(v.customerPhone));p.setString(12,text(v.serviceId));p.setString(13,text(v.serviceName));p.setString(14,text(v.category));p.setInt(15,v.quantity);p.setLong(16,v.unitCents);p.setLong(17,v.totalCents);p.setLong(18,v.paidCents);p.setString(19,text(v.paymentMethod));p.setString(20,text(v.assignedTechnician));p.setString(21,text(v.activeTechnician));p.setString(22,text(v.requirements));p.setString(23,text(v.completionNotes));});
    }

    private void replaceScans(Connection c, List<ScanEvent> rows) throws SQLException {
        replace(c,"scan_events","INSERT INTO scan_events(id,sort_order,created_at,source_device,kind,raw_value,status) VALUES(?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,v.createdAt.toString());p.setString(4,text(v.sourceDevice));p.setString(5,text(v.kind));p.setString(6,text(v.rawValue));p.setString(7,text(v.status));});
    }

    private void replaceStockSync(Connection c, List<StockSyncItem> rows) throws SQLException {
        replace(c,"stock_sync","INSERT INTO stock_sync(id,sort_order,created_at,source_device,product_id,product_name,barcode,category,quantity,price_cents,cost_cents,image_path,status,mobile_product_id,stock_base_known,stock_base,source_stock,mutation_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,v.createdAt.toString());p.setString(4,text(v.sourceDevice));p.setString(5,text(v.productId));p.setString(6,text(v.productName));p.setString(7,text(v.barcode));p.setString(8,text(v.category));p.setInt(9,v.quantity);p.setLong(10,v.priceCents);p.setLong(11,v.costCents);p.setString(12,text(v.imagePath));p.setString(13,text(v.status));p.setString(14,text(v.mobileProductId));p.setInt(15,v.stockBaseKnown?1:0);p.setInt(16,v.stockBase);p.setInt(17,v.sourceStock);p.setString(18,text(v.mutationId));});
    }

    private void replaceProductSync(Connection c, List<ProductSyncItem> rows) throws SQLException {
        replace(c,"product_sync","INSERT INTO product_sync(id,sort_order,created_at,source_device,mobile_product_id,name,description,sku,barcode,category,stock,price_cents,cost_cents,image_path,whatsapp_retailer_id,whatsapp_image_url,whatsapp_product_url,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,v.createdAt.toString());p.setString(4,text(v.sourceDevice));p.setString(5,text(v.mobileProductId));p.setString(6,text(v.name));p.setString(7,text(v.description));p.setString(8,text(v.sku));p.setString(9,text(v.barcode));p.setString(10,text(v.category));p.setInt(11,v.stock);p.setLong(12,v.priceCents);p.setLong(13,v.costCents);p.setString(14,text(v.imagePath));p.setString(15,text(v.whatsappRetailerId));p.setString(16,text(v.whatsappImageUrl));p.setString(17,text(v.whatsappProductUrl));p.setString(18,text(v.status));});
    }

    private void replaceSyncInbox(Connection c, List<SyncInboxEntry> rows) throws SQLException {
        replace(c,"sync_inbox","INSERT INTO sync_inbox(operation_id,operation_type,source_device,received_at,payload_hash,http_status,response_json) VALUES(?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.operationId);p.setString(2,text(v.operationType));p.setString(3,text(v.sourceDevice));p.setString(4,v.receivedAt.toString());p.setString(5,text(v.payloadHash));p.setInt(6,v.httpStatus);p.setString(7,text(v.responseJson));});
    }

    private void replaceStockMovements(Connection c, List<StockMovement> rows) throws SQLException {
        replace(c,"stock_movements","INSERT INTO stock_movements(id,sort_order,operation_id,occurred_at,source_device,product_id,quantity_delta,resulting_stock,reason,reference_id) VALUES(?,?,?,?,?,?,?,?,?,?)",rows,(p,v,i)->{p.setString(1,v.id);p.setInt(2,i);p.setString(3,text(v.operationId));p.setString(4,v.occurredAt.toString());p.setString(5,text(v.sourceDevice));p.setString(6,text(v.productId));p.setInt(7,v.quantityDelta);p.setInt(8,v.resultingStock);p.setString(9,text(v.reason));p.setString(10,text(v.referenceId));});
    }

    private <T> void replace(Connection c, String table, String sql, List<T> values, Binder<T> binder) throws SQLException {
        delete(c, table);
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            int order = 0;
            for (T value : values) {
                binder.bind(statement, value, order++);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void delete(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + table);
        }
    }

    private void setting(PreparedStatement statement, String key, String value) throws SQLException {
        statement.setString(1, key);
        statement.setString(2, text(value));
        statement.addBatch();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private Instant instantOrNull(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    @FunctionalInterface
    private interface Binder<T> {
        void bind(PreparedStatement statement, T value, int order) throws SQLException;
    }
}
