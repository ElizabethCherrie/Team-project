package ipossa;

import static ipossa.DatabaseSupport.*;

import com.sun.net.httpserver.Headers;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * SQLite-backed service layer for the IPOS-SA subsystem.
 *
 * <p>This class owns schema creation and delegates all domain logic to
 * focused service classes while preserving the original public API so that
 * {@code ApiHandler} and {@code ServerApp} remain completely untouched.</p>
 */
final class Database {
    private final Path dbPath;
    private final IntegrationClient integrationClient;

    private final AuthService authService;
    private final UserService userService;
    private final MerchantService merchantService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ReportService reportService;
    private final ApplicationService applicationService;

    /**
     * Creates a database service for the supplied SQLite database file.
     *
     * @param dbPath the path to the SQLite database file
     */
    Database(Path dbPath, IntegrationClient integrationClient) {
        this.dbPath = dbPath;
        this.integrationClient = integrationClient;

        this.merchantService = new MerchantService(dbPath);
        this.productService = new ProductService(dbPath);
        this.userService = new UserService(dbPath);
        this.authService = new AuthService(dbPath, merchantService, productService);
        this.orderService = new OrderService(dbPath, merchantService, productService, integrationClient);
        this.paymentService = new PaymentService(dbPath, merchantService);
        this.reportService = new ReportService(dbPath);
        this.applicationService = new ApplicationService(dbPath, integrationClient);
    }


    /**
     * Initializes the database schema and seeds the database with default data.
     */
    void bootstrap() throws SQLException {
        try (Connection connection = connect(dbPath); Statement statement = connection.createStatement()) {
            // initialisation
            statement.execute("PRAGMA foreign_keys = ON");
            // creates all relevent tables (if they dont already exist)
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        username TEXT PRIMARY KEY,
                        email TEXT NOT NULL,
                        password TEXT NOT NULL,
                        role TEXT NOT NULL,
                        merchant_id TEXT,
                        active INTEGER NOT NULL DEFAULT 1,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS merchants (
                        merchant_id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        email TEXT NOT NULL,
                        address TEXT NOT NULL,
                        phone TEXT,
                        credit_limit REAL NOT NULL,
                        balance REAL NOT NULL DEFAULT 0,
                        account_status TEXT NOT NULL,
                        discount_type TEXT,
                        fixed_discount_rate REAL NOT NULL DEFAULT 0,
                        flexible_rate_tier1 REAL NOT NULL DEFAULT 1,
                        flexible_rate_tier2 REAL NOT NULL DEFAULT 2,
                        flexible_rate_tier3 REAL NOT NULL DEFAULT 3,
                        pending_discount_credit REAL NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        product_id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        package_type TEXT,
                        unit TEXT,
                        units_in_pack INTEGER,
                        unit_price REAL NOT NULL,
                        stock_level INTEGER NOT NULL,
                        minimum_stock_level INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        merchant_id TEXT NOT NULL,
                        order_date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        subtotal REAL NOT NULL,
                        discount_amount REAL NOT NULL,
                        total_amount REAL NOT NULL,
                        dispatched_by TEXT,
                        dispatch_date TEXT,
                        courier TEXT,
                        tracking_number TEXT,
                        expected_delivery TEXT,
                        delivered_date TEXT,
                        FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS order_items (
                        order_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id INTEGER NOT NULL,
                        product_id TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        line_total REAL NOT NULL,
                        FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
                        FOREIGN KEY (product_id) REFERENCES products(product_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS invoices (
                        invoice_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        order_id INTEGER NOT NULL UNIQUE,
                        merchant_id TEXT NOT NULL,
                        issue_date TEXT NOT NULL,
                        due_date TEXT NOT NULL,
                        total_amount REAL NOT NULL,
                        paid_amount REAL NOT NULL DEFAULT 0,
                        status TEXT NOT NULL,
                        FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
                        FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS payments (
                        payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        merchant_id TEXT NOT NULL,
                        amount REAL NOT NULL,
                        method TEXT NOT NULL,
                        reference TEXT,
                        payment_date TEXT NOT NULL,
                        notes TEXT,
                        FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        movement_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_id TEXT NOT NULL,
                        movement_type TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        happened_at TEXT NOT NULL,
                        reference_type TEXT,
                        reference_id TEXT,
                        FOREIGN KEY (product_id) REFERENCES products(product_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS email_log (
                        email_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        recipient TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        body TEXT NOT NULL,
                        sent_at TEXT NOT NULL,
                        delivery_mode TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS non_commercial_applications (
                        application_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        email TEXT NOT NULL,
                        member_type TEXT,
                        account_no TEXT,
                        company_name TEXT,
                        company_address TEXT,
                        company_registration TEXT,
                        status TEXT NOT NULL,
                        generated_password TEXT,
                        outcome_message TEXT,
                        created_at TEXT NOT NULL,
                        processed_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        session_token TEXT PRIMARY KEY,
                        username TEXT NOT NULL,
                        role TEXT NOT NULL,
                        merchant_id TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
                    )
                    """);
            ensureColumn(statement, "non_commercial_applications", "decided_at", "TEXT");
            ensureColumn(statement, "non_commercial_applications", "notes", "TEXT");
            ensureColumn(statement, "products", "package_type", "TEXT");
            ensureColumn(statement, "products", "unit", "TEXT");
            ensureColumn(statement, "products", "units_in_pack", "INTEGER");
            ensureColumn(statement, "non_commercial_applications", "member_type", "TEXT");
            ensureColumn(statement, "non_commercial_applications", "account_no", "TEXT");
            ensureColumn(statement, "non_commercial_applications", "company_name", "TEXT");
            ensureColumn(statement, "non_commercial_applications", "company_address", "TEXT");
            ensureColumn(statement, "non_commercial_applications", "company_registration", "TEXT");
            ensureColumn(statement, "users", "email", "TEXT NOT NULL DEFAULT ''");
            // Migrate legacy ACCEPTED status to PENDING
            statement.execute("UPDATE orders SET status = 'PENDING' WHERE status = 'ACCEPTED'");
        }
        // runs seed function to populate db with initial data
        seed();
    }


    Map<String, Object> login(String username, String password) throws SQLException {
        return authService.login(username, password);
    }

    Map<String, Object> getSession(Headers headers) throws SQLException {
        return authService.getSession(headers);
    }

    AuthContext authorize(Headers headers, String... allowedRoles) throws SQLException {
        return authService.authorize(headers, allowedRoles);
    }


    List<Map<String, Object>> listUsers() throws SQLException {
        return userService.listUsers();
    }

    Map<String, Object> createUser(Map<String, Object> body) throws SQLException {
        return userService.createUser(body);
    }

    Map<String, Object> updateUser(String username, Map<String, Object> body) throws SQLException {
        return userService.updateUser(username, body);
    }

    Map<String, Object> deleteUser(String username) throws SQLException {
        return userService.deleteUser(username);
    }


    List<Map<String, Object>> listMerchants(Headers headers, Map<String, String> query) throws SQLException {
        return merchantService.listMerchants(headers, query);
    }

    Map<String, Object> getMerchant(Headers headers, String merchantId) throws SQLException {
        return merchantService.getMerchant(headers, merchantId);
    }

    Map<String, Object> createMerchant(Map<String, Object> body) throws SQLException {
        return merchantService.createMerchant(body);
    }

    Map<String, Object> updateMerchant(String merchantId, Map<String, Object> body) throws SQLException {
        return merchantService.updateMerchant(merchantId, body);
    }

    Map<String, Object> deleteMerchant(String merchantId) throws SQLException {
        return merchantService.deleteMerchant(merchantId);
    }

    Map<String, Object> getMerchantBalance(Headers headers, String merchantId) throws SQLException {
        return merchantService.getMerchantBalance(headers, merchantId);
    }

    Map<String, Object> updateDiscountPlan(String merchantId, Map<String, Object> body) throws SQLException {
        return merchantService.updateDiscountPlan(merchantId, body);
    }

    Map<String, Object> deleteDiscountPlan(String merchantId) throws SQLException {
        return merchantService.deleteDiscountPlan(merchantId);
    }

    Map<String, Object> restoreMerchant(String merchantId, Map<String, Object> body) throws SQLException {
        return merchantService.restoreMerchant(merchantId, body);
    }

    Map<String, Object> evaluateMerchantAccount(String merchantId) throws SQLException {
        return merchantService.evaluateMerchantAccount(merchantId);
    }

    void runAccountStatusSweep() {
        merchantService.runAccountStatusSweep();
    }

    Map<String, Object> processMonthlyFlexibleDiscounts() throws SQLException {
        return merchantService.processMonthlyFlexibleDiscounts();
    }


    List<Map<String, Object>> listProducts() throws SQLException {
        return productService.listProducts();
    }

    List<Map<String, Object>> searchProducts(String query) throws SQLException {
        return productService.searchProducts(query);
    }

    Map<String, Object> getProduct(String productId) throws SQLException {
        return productService.getProduct(productId);
    }

    Map<String, Object> createProduct(Map<String, Object> body) throws SQLException {
        return productService.createProduct(body);
    }

    Map<String, Object> updateProduct(String productId, Map<String, Object> body) throws SQLException {
        return productService.updateProduct(productId, body);
    }

    Map<String, Object> deleteProduct(String productId) throws SQLException {
        return productService.deleteProduct(productId);
    }

    Map<String, Object> addStock(String productId, Map<String, Object> body) throws SQLException {
        return productService.addStock(productId, body);
    }

    Map<String, Object> updateMinimumStock(String productId, Map<String, Object> body) throws SQLException {
        return productService.updateMinimumStock(productId, body);
    }

    Map<String, Object> getFormattedLowStockReport() throws SQLException {
        return productService.getFormattedLowStockReport();
    }


    Map<String, Object> createOrder(Headers headers, Map<String, Object> body) throws SQLException {
        return orderService.createOrder(headers, body);
    }

    List<Map<String, Object>> listOrders(Headers headers, Map<String, String> query) throws SQLException {
        return orderService.listOrders(headers, query);
    }

    Map<String, Object> getOrder(Headers headers, long orderId) throws SQLException {
        return orderService.getOrder(headers, orderId);
    }

    List<Map<String, Object>> listPendingOrders() throws SQLException {
        return orderService.listPendingOrders();
    }

    Map<String, Object> updateOrderStatus(long orderId, Map<String, Object> body) throws SQLException {
        return orderService.updateOrderStatus(orderId, body);
    }

    Map<String, Object> generateInvoice(long orderId) throws SQLException {
        return orderService.generateInvoice(orderId);
    }

    List<Map<String, Object>> listInvoices(Headers headers, Map<String, String> query) throws SQLException {
        return orderService.listInvoices(headers, query);
    }

    Map<String, Object> getInvoice(Headers headers, long invoiceId) throws SQLException {
        return orderService.getInvoice(headers, invoiceId);
    }


    List<Map<String, Object>> listPayments(Map<String, String> query) throws SQLException {
        return paymentService.listPayments(query);
    }

    Map<String, Object> recordPayment(Map<String, Object> body) throws SQLException {
        return paymentService.recordPayment(body);
    }


    Map<String, Object> describeIntegrations() {
        return integrationClient.describeConfiguration();
    }

    Map<String, Object> sendPuMail(Map<String, Object> body) {
        String sender = JsonUtil.requireString(body, "sender");
        List<Object> rawReceivers = JsonUtil.requireArray(body, "receivers");
        if (rawReceivers.isEmpty()) {
            throw new ApiException(400, "receivers must contain at least one recipient");
        }
        List<String> receivers = new ArrayList<>();
        for (Object rawReceiver : rawReceivers) {
            receivers.add(String.valueOf(rawReceiver));
        }
        return integrationClient.sendPuMail(
                sender,
                receivers,
                JsonUtil.requireString(body, "subject"),
                JsonUtil.requireString(body, "body")
        );
    }

    Map<String, Object> sendPuPayment(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amount", JsonUtil.requireDouble(body, "amount"));
        payload.put("senderName", JsonUtil.requireString(body, "senderName"));
        payload.put("senderCardNumber", JsonUtil.requireString(body, "senderCardNumber"));
        payload.put("senderCVV", JsonUtil.requireString(body, "senderCVV"));
        payload.put("senderExpiryDate", JsonUtil.requireString(body, "senderExpiryDate"));
        payload.put("senderBillingAddress", JsonUtil.requireString(body, "senderBillingAddress"));
        payload.put("senderEmail", JsonUtil.requireString(body, "senderEmail"));
        payload.put("receiverName", JsonUtil.requireString(body, "receiverName"));
        payload.put("receiverBankName", JsonUtil.optionalString(body, "receiverBankName"));
        payload.put("receiverAccountNumber", JsonUtil.requireString(body, "receiverAccountNumber"));
        payload.put("receiverSortCode", JsonUtil.requireString(body, "receiverSortCode"));
        return integrationClient.sendPuPayment(payload);
    }

    Map<String, Object> sendCaStock(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", JsonUtil.requireString(body, "name"));
        payload.put("packageType", JsonUtil.requireString(body, "packageType"));
        payload.put("units", JsonUtil.requireString(body, "units"));
        payload.put("unitsInAPack", JsonUtil.requireInt(body, "unitsInAPack"));
        payload.put("bulkCost", JsonUtil.requireDouble(body, "bulkCost"));
        payload.put("markupRate", body.containsKey("markupRate") ? JsonUtil.requireInt(body, "markupRate") : 2);
        payload.put("quantity", JsonUtil.requireInt(body, "quantity"));
        payload.put("stockLimit", JsonUtil.requireInt(body, "stockLimit"));
        return integrationClient.sendCaStockItem(payload);
    }


    Map<String, Object> turnoverReport(Map<String, String> query) throws SQLException {
        return reportService.turnoverReport(query);
    }

    Map<String, Object> stockTurnoverReport(Map<String, String> query) throws SQLException {
        return reportService.stockTurnoverReport(query);
    }

    Map<String, Object> lowStockReport() throws SQLException {
        return reportService.lowStockReport();
    }

    Map<String, Object> debtorRemindersReport() throws SQLException {
        return reportService.debtorRemindersReport();
    }

    Map<String, Object> merchantOrdersReport(Map<String, String> query) throws SQLException {
        return reportService.merchantOrdersReport(query);
    }

    Map<String, Object> merchantActivityReport(Map<String, String> query) throws SQLException {
        return reportService.merchantActivityReport(query);
    }

    Map<String, Object> merchantInvoicesReport(Map<String, String> query) throws SQLException {
        return reportService.merchantInvoicesReport(query);
    }

    Map<String, Object> companyInvoicesReport(Map<String, String> query) throws SQLException {
        return reportService.companyInvoicesReport(query);
    }


    Map<String, Object> createNonCommercialApplication(Map<String, Object> body) throws SQLException {
        return applicationService.createNonCommercialApplication(body);
    }

    List<Map<String, Object>> listApplications() throws SQLException {
        return applicationService.listApplications();
    }

    Map<String, Object> decideApplication(long applicationId, Map<String, Object> body) throws SQLException {
        return applicationService.decideApplication(applicationId, body);
    }


    Map<String, Object> testCaConnection() {
        return integrationClient.testConnection("CA");
    }

    Map<String, Object> testPuConnection() {
        return integrationClient.testConnection("PU");
    }


    private void seed() throws SQLException {
        try (Connection connection = connect(dbPath)) {
            // seeds database if database empty
            if (count(connection, "users") == 0) {
                insertUser(connection, "Sysdba", "sysdba@infopharma.local", "London_weighting", "ADMINISTRATOR", null);
                insertUser(connection, "manager", "ops.director@infopharma.local", "Get_it_done", "MANAGER", null);
                insertUser(connection, "accountant", "accountant@infopharma.local", "Count_money", "ACCOUNTING_STAFF", null);
                insertUser(connection, "clerk", "clerk@infopharma.local", "Paperwork", "ACCOUNTING_STAFF", null);
                insertUser(connection, "warehouse1", "warehouse1@infopharma.local", "Get_a_beer", "OPERATIONS_STAFF", null);
                insertUser(connection, "warehouse2", "warehouse2@infopharma.local", "Lot_smell", "OPERATIONS_STAFF", null);
                insertUser(connection, "delivery", "delivery@infopharma.local", "Too_dark", "OPERATIONS_STAFF", null);
                insertUser(connection, "city", "citypharmacy@example.com", "demo123", "MERCHANT", "ACC0001");
                insertUser(connection, "cosymed", "cosymed@example.com", "bondstreet", "MERCHANT", "ACC0002");
                insertUser(connection, "hello", "hello@example.com", "there", "MERCHANT", "ACC0003");
            }
            if (count(connection, "merchants") == 0) {
                insertMerchant(connection, "ACC0001", "CityPharmacy", "citypharmacy@example.com",
                        "Northampton Square, London EC1V 0HB", "0207 040 8000",
                        10_000, "FIXED", 3, 0, 1, 2, "2026-02-01T09:00:00");
                insertMerchant(connection, "ACC0002", "Cosymed Ltd", "cosymed@example.com",
                        "25, Bond Street, London WC1V 8LS", "0207 321 8001",
                        5_000, "FLEXIBLE", 0, 0, 1, 2, "2026-02-01T09:05:00");
                insertMerchant(connection, "ACC0003", "HelloPharmacy", "hello@example.com",
                        "12, Bond Street, London WC1V 9NS", "0207 321 8002",
                        5_000, "FLEXIBLE", 0, 0, 1, 3, "2026-02-01T09:10:00");
            }
            if (count(connection, "products") == 0) {
                seedProduct(connection, "10000001", "Paracetamol", "Box", "Caps", 20, 0.10, 10345, 300);
                seedProduct(connection, "10000002", "Aspirin", "Box", "Caps", 20, 0.50, 12453, 500);
                seedProduct(connection, "10000003", "Analgin", "Box", "Caps", 10, 1.20, 4235, 200);
                seedProduct(connection, "10000004", "Celebrex, caps 100 mg", "Box", "Caps", 10, 10.00, 3420, 200);
                seedProduct(connection, "10000005", "Celebrex, caps 200 mg", "Box", "Caps", 10, 18.50, 1450, 150);
                seedProduct(connection, "10000006", "Retin-A Tretin, 30 g", "Box", "Caps", 20, 25.00, 2013, 200);
                seedProduct(connection, "10000007", "Lipitor TB, 20 mg", "Box", "Caps", 30, 15.50, 1562, 200);
                seedProduct(connection, "10000008", "Claritin CR, 60g", "Box", "Caps", 20, 19.50, 2540, 200);
                seedProduct(connection, "20000004", "Iodine tincture", "Bottle", "Ml", 100, 0.30, 22134, 200);
                seedProduct(connection, "20000005", "Rhynol", "Bottle", "Ml", 200, 2.50, 1908, 300);
                seedProduct(connection, "30000001", "Ospen", "Box", "Caps", 20, 10.50, 809, 200);
                seedProduct(connection, "30000002", "Amopen", "Box", "Caps", 30, 15.00, 1340, 300);
                seedProduct(connection, "40000001", "Vitamin C", "Box", "Caps", 30, 1.20, 3258, 300);
                seedProduct(connection, "40000002", "Vitamin B12", "Box", "Caps", 30, 1.30, 2673, 300);
            }
            backfillProductMetadata(connection);
            if (count(connection, "orders") == 0) {
                seedHistoricalOrder(connection, "ACC0001", "2026-02-20T09:30:00", "2026-02-23T15:00:00",
                        "delivery", "InfoPharma Courier Service", "INF-SA-0001", "2026-02-23T15:00:00",
                        List.of(
                                line("10000001", 10, 0.10),
                                line("10000003", 20, 1.20),
                                line("20000004", 12, 0.30),
                                line("20000005", 10, 2.50),
                                line("30000001", 10, 10.50),
                                line("30000002", 20, 15.00),
                                line("40000001", 20, 1.20),
                                line("40000002", 20, 1.30)
                        ));
                seedHistoricalOrder(connection, "ACC0002", "2026-02-25T11:15:00", "2026-02-26T17:00:00",
                        "delivery", "DHL", "DHL-SA-0002", "2026-02-26T17:00:00",
                        List.of(
                                line("10000001", 10, 0.10),
                                line("10000003", 20, 1.20),
                                line("20000005", 10, 2.50),
                                line("30000002", 20, 15.00),
                                line("40000002", 20, 1.30)
                        ));
                seedHistoricalOrder(connection, "ACC0003", "2026-02-25T13:40:00", "2026-02-27T10:00:00",
                        "delivery", "DHL", "DHL-SA-0003", "2026-02-27T10:00:00",
                        List.of(
                                line("10000003", 20, 1.20),
                                line("20000004", 12, 0.30),
                                line("30000001", 3, 10.50),
                                line("30000002", 10, 15.00),
                                line("40000001", 20, 1.20),
                                line("40000002", 20, 1.30)
                        ));
                seedHistoricalOrder(connection, "ACC0002", "2026-03-10T09:20:00", "2026-03-12T11:00:00",
                        "delivery", "InfoPharma Courier Service", "INF-SA-0004", "2026-03-12T11:00:00",
                        List.of(
                                line("20000005", 10, 2.50),
                                line("30000001", 10, 10.50),
                                line("30000002", 20, 15.00)
                        ));
                seedHistoricalOrder(connection, "ACC0003", "2026-03-25T14:05:00", "2026-03-27T10:00:00",
                        "delivery", "InfoPharma Courier Service", "INF-SA-0005", "2026-03-27T10:00:00",
                        List.of(
                                line("10000003", 20, 1.20),
                                line("10000004", 5, 10.00),
                                line("10000005", 5, 18.50),
                                line("10000006", 5, 25.00),
                                line("10000007", 10, 15.50),
                                line("30000001", 10, 10.50),
                                line("30000002", 20, 15.00),
                                line("40000002", 20, 1.30)
                        ));
                seedHistoricalOrder(connection, "ACC0003", "2026-04-01T09:10:00", "2026-04-03T10:00:00",
                        "delivery", "InfoPharma Courier Service", "INF-SA-0006", "2026-04-03T10:00:00",
                        List.of(
                                line("10000003", 20, 1.20),
                                line("10000004", 5, 10.00),
                                line("10000005", 5, 18.50),
                                line("10000006", 5, 25.00),
                                line("10000007", 10, 15.50),
                                line("30000001", 10, 10.50),
                                line("40000002", 20, 1.30)
                        ));
            }
            if (count(connection, "payments") == 0) {
                seedHistoricalPayment(connection, "ACC0003", 259.10, "BANK_TRANSFER", "HELLO-CLR-20260305",
                        "2026-03-05", "Historical balance clearance recorded from sample data");
                seedHistoricalPayment(connection, "ACC0001", 508.60, "BANK_TRANSFER", "CITY-CLR-20260315",
                        "2026-03-15", "Full payment cleared by bank transfer");
                seedHistoricalPayment(connection, "ACC0002", 806.00, "CARD", "COSY-CLR-20260315",
                        "2026-03-15", "Full payment cleared by company credit card");
            }
            if (count(connection, "non_commercial_applications") == 0) {
                applicationService.seedApplication(connection, "cool@example.com", "NON_COMMERCIAL", "PU0001", null, null, null,
                        "APPROVED", "2026-02-25T10:00:00", "2026-02-26T09:00:00", "Imported from IPOS-PU sample data");
                applicationService.seedApplication(connection, "cool1@example.com", "NON_COMMERCIAL", "PU0002", null, null, null,
                        "PENDING", "2026-02-25T10:05:00", null, "Imported from IPOS-PU sample data");
                applicationService.seedApplication(connection, "pondpharma@example.com", "COMMERCIAL", "PU0003", "Pond Pharmacy",
                        "Chislehurst, 25 High Street, BR7 5BN", "UK10003429CompH",
                        "PENDING", "2026-02-25T10:10:00", null, "Commercial member application imported from IPOS-PU sample data");
            }
            applicationService.backfillApplicationMetadata(connection);
        }
    }

    private void insertUser(Connection connection, String username, String email, String password, String role, String merchantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO users (username, email, password, role, merchant_id, active, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, password);
            ps.setString(4, role);
            ps.setString(5, merchantId);
            ps.setString(6, now());
            ps.executeUpdate();
        }
    }

    private void insertMerchant(Connection connection, String merchantId, String name, String email, String address, String phone,
                                double creditLimit, String discountType, double fixedRate, double tier1, double tier2, double tier3,
                                String createdAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO merchants (
                    merchant_id, name, email, address, phone, credit_limit, balance, account_status,
                    discount_type, fixed_discount_rate, flexible_rate_tier1, flexible_rate_tier2, flexible_rate_tier3,
                    pending_discount_credit, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, 'NORMAL', ?, ?, ?, ?, ?, 0, ?, ?)
                """)) {
            ps.setString(1, merchantId);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, address);
            ps.setString(5, phone);
            ps.setDouble(6, creditLimit);
            ps.setString(7, discountType);
            ps.setDouble(8, fixedRate);
            ps.setDouble(9, tier1);
            ps.setDouble(10, tier2);
            ps.setDouble(11, tier3);
            ps.setString(12, createdAt);
            ps.setString(13, createdAt);
            ps.executeUpdate();
        }
    }

    private void seedProduct(Connection connection, String id, String name, String packageType, String unit, int unitsInPack,
                             double price, int stock, int minimumStock) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO products (
                    product_id, name, package_type, unit, units_in_pack,
                    unit_price, stock_level, minimum_stock_level, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            String ts = now();
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, packageType);
            ps.setString(4, unit);
            ps.setInt(5, unitsInPack);
            ps.setDouble(6, price);
            ps.setInt(7, stock);
            ps.setInt(8, minimumStock);
            ps.setString(9, ts);
            ps.setString(10, ts);
            ps.executeUpdate();
        }
    }

    private void seedHistoricalOrder(Connection connection, String merchantId, String orderDateTime, String dispatchDateTime,
                                     String dispatchedBy, String courier, String trackingNumber, String deliveredDateTime,
                                     List<OrderSeedLine> items) throws SQLException {
        double subtotal = items.stream().mapToDouble(item -> item.unitPrice() * item.quantity()).sum();
        long orderId;
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO orders (
                    merchant_id, order_date, status, subtotal, discount_amount, total_amount,
                    dispatched_by, dispatch_date, courier, tracking_number, expected_delivery, delivered_date
                ) VALUES (?, ?, 'DELIVERED', ?, 0, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, merchantId);
            insert.setString(2, orderDateTime);
            insert.setDouble(3, subtotal);
            insert.setDouble(4, subtotal);
            insert.setString(5, dispatchedBy);
            insert.setString(6, dispatchDateTime);
            insert.setString(7, courier);
            insert.setString(8, trackingNumber);
            insert.setString(9, deliveredDateTime);
            insert.setString(10, deliveredDateTime);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                orderId = keys.getLong(1);
            }
        }

        for (OrderSeedLine item : items) {
            double lineTotal = item.unitPrice() * item.quantity();
            try (PreparedStatement itemInsert = connection.prepareStatement("""
                    INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                itemInsert.setLong(1, orderId);
                itemInsert.setString(2, item.productId());
                itemInsert.setInt(3, item.quantity());
                itemInsert.setDouble(4, item.unitPrice());
                itemInsert.setDouble(5, lineTotal);
                itemInsert.executeUpdate();
            }
            try (PreparedStatement updateProduct = connection.prepareStatement("""
                    UPDATE products SET stock_level = stock_level - ?, updated_at = ? WHERE product_id = ?
                    """)) {
                updateProduct.setInt(1, item.quantity());
                updateProduct.setString(2, orderDateTime);
                updateProduct.setString(3, item.productId());
                updateProduct.executeUpdate();
            }
            productService.insertStockMovementAt(connection, item.productId(), "SALE", item.quantity(), orderDateTime, "ORDER", Long.toString(orderId));
        }

        LocalDate issueDate = LocalDate.parse(orderDateTime.substring(0, 10));
        try (PreparedStatement invoiceInsert = connection.prepareStatement("""
                INSERT INTO invoices (order_id, merchant_id, issue_date, due_date, total_amount, paid_amount, status)
                VALUES (?, ?, ?, ?, ?, 0, 'ISSUED')
                """)) {
            invoiceInsert.setLong(1, orderId);
            invoiceInsert.setString(2, merchantId);
            invoiceInsert.setString(3, issueDate.toString());
            invoiceInsert.setString(4, issueDate.withDayOfMonth(issueDate.lengthOfMonth()).toString());
            invoiceInsert.setDouble(5, subtotal);
            invoiceInsert.executeUpdate();
        }

        merchantService.updateMerchantBalance(connection, merchantId);
    }

    private void seedHistoricalPayment(Connection connection, String merchantId, double amount, String method, String reference,
                                       String paymentDate, String notes) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO payments (merchant_id, amount, method, reference, payment_date, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, merchantId);
            insert.setDouble(2, amount);
            insert.setString(3, method);
            insert.setString(4, reference);
            insert.setString(5, paymentDate);
            insert.setString(6, notes);
            insert.executeUpdate();
        }
        paymentService.applyPaymentToInvoices(connection, merchantId, amount);
        merchantService.updateMerchantBalance(connection, merchantId);
        merchantService.evaluateMerchantAccount(connection, merchantId);
    }

    private void backfillProductMetadata(Connection connection) throws SQLException {
        record ProductMeta(String packageType, String unit, int unitsInPack) {}
        Map<String, ProductMeta> metadata = Map.ofEntries(
                Map.entry("10000001", new ProductMeta("Box", "Caps", 20)),
                Map.entry("10000002", new ProductMeta("Box", "Caps", 20)),
                Map.entry("10000003", new ProductMeta("Box", "Caps", 10)),
                Map.entry("10000004", new ProductMeta("Box", "Caps", 10)),
                Map.entry("10000005", new ProductMeta("Box", "Caps", 10)),
                Map.entry("10000006", new ProductMeta("Box", "Caps", 20)),
                Map.entry("10000007", new ProductMeta("Box", "Caps", 30)),
                Map.entry("10000008", new ProductMeta("Box", "Caps", 20)),
                Map.entry("20000004", new ProductMeta("Bottle", "Ml", 100)),
                Map.entry("20000005", new ProductMeta("Bottle", "Ml", 200)),
                Map.entry("30000001", new ProductMeta("Box", "Caps", 20)),
                Map.entry("30000002", new ProductMeta("Box", "Caps", 30)),
                Map.entry("40000001", new ProductMeta("Box", "Caps", 30)),
                Map.entry("40000002", new ProductMeta("Box", "Caps", 30))
        );
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE products
                SET package_type = COALESCE(package_type, ?),
                    unit = COALESCE(unit, ?),
                    units_in_pack = COALESCE(units_in_pack, ?),
                    updated_at = ?
                WHERE product_id = ?
                """)) {
            String updatedAt = now();
            for (Map.Entry<String, ProductMeta> entry : metadata.entrySet()) {
                ps.setString(1, entry.getValue().packageType());
                ps.setString(2, entry.getValue().unit());
                ps.setInt(3, entry.getValue().unitsInPack());
                ps.setString(4, updatedAt);
                ps.setString(5, entry.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }


    private record OrderSeedLine(String productId, int quantity, double unitPrice) {}

    private static OrderSeedLine line(String productId, int quantity, double unitPrice) {
        return new OrderSeedLine(productId, quantity, unitPrice);
    }
}
