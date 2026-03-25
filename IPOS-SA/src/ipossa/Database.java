package ipossa;

import com.sun.net.httpserver.Headers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class Database {
    private final Path dbPath;

    Database(Path dbPath) {
        this.dbPath = dbPath;
    }

    void bootstrap() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        username TEXT PRIMARY KEY,
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
                        status TEXT NOT NULL,
                        generated_password TEXT,
                        outcome_message TEXT,
                        created_at TEXT NOT NULL,
                        processed_at TEXT
                    )
                    """);
        }
        seed();
    }

    Map<String, Object> login(String username, String password) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT username, role, merchant_id, active
                     FROM users
                     WHERE username = ? AND password = ?
                     """)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(401, "Invalid credentials");
                }
                if (rs.getInt("active") != 1) {
                    throw new ApiException(403, "Account is inactive");
                }
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("username", rs.getString("username"));
                response.put("role", rs.getString("role"));
                response.put("merchantId", rs.getString("merchant_id"));
                if ("MERCHANT".equals(rs.getString("role")) && rs.getString("merchant_id") != null) {
                    response.put("merchant", getMerchantById(connection, rs.getString("merchant_id")));
                    response.put("warnings", evaluateMerchantAccount(connection, rs.getString("merchant_id")).get("warnings"));
                } else if ("ADMINISTRATOR".equals(rs.getString("role")) || "MANAGER".equals(rs.getString("role"))) {
                    response.put("warnings", lowStockRows(connection));
                } else {
                    response.put("warnings", List.of());
                }
                return response;
            }
        }
    }

    List<Map<String, Object>> listUsers() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT username, role, merchant_id, active, created_at FROM users ORDER BY username")) {
            return rows(rs);
        }
    }

    Map<String, Object> createUser(Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO users (username, password, role, merchant_id, active, created_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, JsonUtil.requireString(body, "username"));
            ps.setString(2, JsonUtil.requireString(body, "password"));
            ps.setString(3, JsonUtil.requireUpper(body, "role"));
            ps.setString(4, JsonUtil.optionalString(body, "merchantId"));
            ps.setInt(5, body.containsKey("active") && !JsonUtil.requireBoolean(body, "active") ? 0 : 1);
            ps.setString(6, now());
            ps.executeUpdate();
        }
        return Map.of("message", "User created");
    }

    Map<String, Object> updateUser(String username, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE users
                     SET password = COALESCE(?, password),
                         role = COALESCE(?, role),
                         merchant_id = COALESCE(?, merchant_id),
                         active = COALESCE(?, active)
                     WHERE username = ?
                     """)) {
            setNullable(ps, 1, body.containsKey("password") ? ipossa.JsonUtil.requireString(body, "password") : null);
            setNullable(ps, 2, body.containsKey("role") ? ipossa.JsonUtil.requireUpper(body, "role") : null);
            setNullable(ps, 3, body.containsKey("merchantId") ? JsonUtil.optionalString(body, "merchantId") : null);
            setNullable(ps, 4, body.containsKey("active") ? (JsonUtil.requireBoolean(body, "active") ? 1 : 0) : null);
            ps.setString(5, username);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "User not found");
            }
        }
        return Map.of("message", "User updated");
    }

    Map<String, Object> deleteUser(String username) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM users WHERE username = ?")) {
            ps.setString(1, username);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "User not found");
            }
        }
        return Map.of("message", "User deleted");
    }

    List<Map<String, Object>> listMerchants(Headers headers) throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM merchants ORDER BY merchant_id")) {
            List<Map<String, Object>> merchants = rows(rs);
            if (isMerchant(headers)) {
                String merchantId = authenticatedMerchantId(headers);
                merchants.removeIf(row -> !merchantId.equals(row.get("merchant_id")));
            }
            return merchants;
        }
    }

    Map<String, Object> getMerchant(Headers headers, String merchantId) throws SQLException {
        if (isMerchant(headers) && !authenticatedMerchantId(headers).equals(merchantId)) {
            throw new ApiException(403, "Merchants can only view their own account");
        }
        try (Connection connection = connect()) {
            Map<String, Object> merchant = getMerchantById(connection, merchantId);
            merchant.put("warnings", evaluateMerchantAccount(connection, merchantId).get("warnings"));
            return merchant;
        }
    }

    Map<String, Object> createMerchant(Map<String, Object> body) throws SQLException {
        String merchantId = JsonUtil.requireString(body, "merchantId");
        String username = JsonUtil.requireString(body, "username");
        String password = JsonUtil.requireString(body, "password");
        String now = now();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement merchantPs = connection.prepareStatement("""
                        INSERT INTO merchants (
                            merchant_id, name, email, address, phone, credit_limit, balance, account_status,
                            discount_type, fixed_discount_rate, flexible_rate_tier1, flexible_rate_tier2, flexible_rate_tier3,
                            pending_discount_credit, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, 0, 'NORMAL', ?, ?, ?, ?, ?, 0, ?, ?)
                        """)) {
                    merchantPs.setString(1, merchantId);
                    merchantPs.setString(2, JsonUtil.requireString(body, "name"));
                    merchantPs.setString(3, JsonUtil.requireString(body, "email"));
                    merchantPs.setString(4, JsonUtil.requireString(body, "address"));
                    merchantPs.setString(5, JsonUtil.optionalString(body, "phone"));
                    merchantPs.setDouble(6, JsonUtil.requireDouble(body, "creditLimit"));
                    merchantPs.setString(7, JsonUtil.optionalString(body, "discountType"));
                    merchantPs.setDouble(8, JsonUtil.optionalDouble(body, "fixedDiscountRate", 0));
                    merchantPs.setDouble(9, JsonUtil.optionalDouble(body, "flexibleRateTier1", 1));
                    merchantPs.setDouble(10, JsonUtil.optionalDouble(body, "flexibleRateTier2", 2));
                    merchantPs.setDouble(11, JsonUtil.optionalDouble(body, "flexibleRateTier3", 3));
                    merchantPs.setString(12, now);
                    merchantPs.setString(13, now);
                    merchantPs.executeUpdate();
                }
                try (PreparedStatement userPs = connection.prepareStatement("""
                        INSERT INTO users (username, password, role, merchant_id, active, created_at)
                        VALUES (?, ?, 'MERCHANT', ?, 1, ?)
                        """)) {
                    userPs.setString(1, username);
                    userPs.setString(2, password);
                    userPs.setString(3, merchantId);
                    userPs.setString(4, now);
                    userPs.executeUpdate();
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return Map.of("message", "Merchant created", "merchantId", merchantId);
    }

    Map<String, Object> updateMerchant(String merchantId, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE merchants
                     SET name = COALESCE(?, name),
                         email = COALESCE(?, email),
                         address = COALESCE(?, address),
                         phone = COALESCE(?, phone),
                         credit_limit = COALESCE(?, credit_limit),
                         updated_at = ?
                     WHERE merchant_id = ?
                     """)) {
            setNullable(ps, 1, body.get("name"));
            setNullable(ps, 2, body.get("email"));
            setNullable(ps, 3, body.get("address"));
            setNullable(ps, 4, body.get("phone"));
            setNullable(ps, 5, body.containsKey("creditLimit") ? JsonUtil.requireDouble(body, "creditLimit") : null);
            ps.setString(6, now());
            ps.setString(7, merchantId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Merchant not found");
            }
        }
        if (body.containsKey("discountType") || body.containsKey("fixedDiscountRate")) {
            updateDiscountPlan(merchantId, body);
        }
        return Map.of("message", "Merchant updated");
    }

    Map<String, Object> deleteMerchant(String merchantId) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM users WHERE merchant_id = ?");
                     PreparedStatement deleteMerchant = connection.prepareStatement("DELETE FROM merchants WHERE merchant_id = ?")) {
                    deleteUser.setString(1, merchantId);
                    deleteUser.executeUpdate();
                    deleteMerchant.setString(1, merchantId);
                    if (deleteMerchant.executeUpdate() == 0) {
                        connection.rollback();
                        throw new ApiException(404, "Merchant not found");
                    }
                }
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return Map.of("message", "Merchant deleted with cascaded orders/invoices/payments");
    }

    Map<String, Object> getMerchantBalance(Headers headers, String merchantId) throws SQLException {
        if (isMerchant(headers) && !authenticatedMerchantId(headers).equals(merchantId)) {
            throw new ApiException(403, "Merchants can only view their own balance");
        }
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("SELECT balance, account_status FROM merchants WHERE merchant_id = ?")) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "Merchant not found");
                }
                return Map.of("merchantId", merchantId, "balance", rs.getDouble(1), "accountStatus", rs.getString(2));
            }
        }
    }

    Map<String, Object> updateDiscountPlan(String merchantId, Map<String, Object> body) throws SQLException {
        String type = JsonUtil.requireUpper(body, "discountType");
        if (!List.of("FIXED", "FLEXIBLE").contains(type)) {
            throw new ApiException(400, "discountType must be FIXED or FLEXIBLE");
        }
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE merchants
                     SET discount_type = ?, fixed_discount_rate = ?, flexible_rate_tier1 = ?,
                         flexible_rate_tier2 = ?, flexible_rate_tier3 = ?, updated_at = ?
                     WHERE merchant_id = ?
                     """)) {
            ps.setString(1, type);
            ps.setDouble(2, JsonUtil.optionalDouble(body, "fixedDiscountRate", 0));
            ps.setDouble(3, JsonUtil.optionalDouble(body, "flexibleRateTier1", 1));
            ps.setDouble(4, JsonUtil.optionalDouble(body, "flexibleRateTier2", 2));
            ps.setDouble(5, JsonUtil.optionalDouble(body, "flexibleRateTier3", 3));
            ps.setString(6, now());
            ps.setString(7, merchantId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Merchant not found");
            }
        }
        return Map.of("message", "Discount plan updated", "discountType", type);
    }

    Map<String, Object> deleteDiscountPlan(String merchantId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE merchants
                     SET discount_type = NULL, fixed_discount_rate = 0, updated_at = ?
                     WHERE merchant_id = ?
                     """)) {
            ps.setString(1, now());
            ps.setString(2, merchantId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Merchant not found");
            }
        }
        return Map.of("message", "Discount plan removed");
    }

    Map<String, Object> restoreMerchant(String merchantId, Map<String, Object> body) throws SQLException {
        if (!JsonUtil.requireBoolean(body, "directorApproved")) {
            throw new ApiException(400, "Director approval is required");
        }
        String newStatus = JsonUtil.requireUpper(body, "newStatus");
        if (!List.of("NORMAL", "SUSPENDED").contains(newStatus)) {
            throw new ApiException(400, "newStatus must be NORMAL or SUSPENDED");
        }
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE merchants
                     SET account_status = ?, updated_at = ?
                     WHERE merchant_id = ?
                     """)) {
            ps.setString(1, newStatus);
            ps.setString(2, now());
            ps.setString(3, merchantId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Merchant not found");
            }
        }
        return Map.of("message", "Merchant restored", "newStatus", newStatus);
    }

    Map<String, Object> evaluateMerchantAccount(String merchantId) throws SQLException {
        try (Connection connection = connect()) {
            return evaluateMerchantAccount(connection, merchantId);
        }
    }

    List<Map<String, Object>> listProducts() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM products ORDER BY product_id")) {
            return rows(rs);
        }
    }

    List<Map<String, Object>> searchProducts(String query) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT *
                     FROM products
                     WHERE LOWER(name) LIKE ? OR product_id LIKE ?
                     ORDER BY product_id
                     """)) {
            String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
            ps.setString(1, pattern);
            ps.setString(2, "%" + query + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        }
    }

    Map<String, Object> getProduct(String productId) throws SQLException {
        try (Connection connection = connect()) {
            return getProductById(connection, productId);
        }
    }

    Map<String, Object> createProduct(Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO products (product_id, name, unit_price, stock_level, minimum_stock_level, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            String now = now();
            ps.setString(1, JsonUtil.requireString(body, "productId"));
            ps.setString(2, JsonUtil.requireString(body, "name"));
            ps.setDouble(3, JsonUtil.requireDouble(body, "unitPrice"));
            ps.setInt(4, JsonUtil.requireInt(body, "stockLevel"));
            ps.setInt(5, JsonUtil.requireInt(body, "minimumStockLevel"));
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
        }
        return Map.of("message", "Product created");
    }

    Map<String, Object> updateProduct(String productId, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE products
                     SET name = COALESCE(?, name),
                         unit_price = COALESCE(?, unit_price),
                         stock_level = COALESCE(?, stock_level),
                         minimum_stock_level = COALESCE(?, minimum_stock_level),
                         updated_at = ?
                     WHERE product_id = ?
                     """)) {
            setNullable(ps, 1, body.get("name"));
            setNullable(ps, 2, body.containsKey("unitPrice") ? JsonUtil.requireDouble(body, "unitPrice") : null);
            setNullable(ps, 3, body.containsKey("stockLevel") ? JsonUtil.requireInt(body, "stockLevel") : null);
            setNullable(ps, 4, body.containsKey("minimumStockLevel") ? JsonUtil.requireInt(body, "minimumStockLevel") : null);
            ps.setString(5, now());
            ps.setString(6, productId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Product not found");
            }
        }
        return Map.of("message", "Product updated");
    }

    Map<String, Object> deleteProduct(String productId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
            ps.setString(1, productId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Product not found");
            }
        }
        return Map.of("message", "Product deleted");
    }

    Map<String, Object> addStock(String productId, Map<String, Object> body) throws SQLException {
        int quantity = JsonUtil.requireInt(body, "quantity");
        if (quantity <= 0) {
            throw new ApiException(400, "quantity must be greater than 0");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                int currentStock = ((Number) getProductById(connection, productId).get("stock_level")).intValue();
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE products SET stock_level = ?, updated_at = ? WHERE product_id = ?
                        """)) {
                    ps.setInt(1, currentStock + quantity);
                    ps.setString(2, now());
                    ps.setString(3, productId);
                    ps.executeUpdate();
                }
                insertStockMovement(connection, productId, "RESTOCK", quantity, "MANUAL_RESTOCK", JsonUtil.optionalString(body, "reference"));
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return Map.of("message", "Stock increased");
    }

    Map<String, Object> updateMinimumStock(String productId, Map<String, Object> body) throws SQLException {
        int minimumStock = JsonUtil.requireInt(body, "minimumStockLevel");
        if (minimumStock < 0) {
            throw new ApiException(400, "minimumStockLevel must be non-negative");
        }
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE products SET minimum_stock_level = ?, updated_at = ? WHERE product_id = ?
                     """)) {
            ps.setInt(1, minimumStock);
            ps.setString(2, now());
            ps.setString(3, productId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Product not found");
            }
        }
        return Map.of("message", "Minimum stock level updated");
    }

    Map<String, Object> createOrder(Headers headers, Map<String, Object> body) throws SQLException {
        String merchantId = JsonUtil.requireString(body, "merchantId");
        if (!authenticatedMerchantId(headers).equals(merchantId)) {
            throw new ApiException(403, "Merchants can only place orders for their own account");
        }
        List<Object> requestedItems = JsonUtil.requireArray(body, "items");
        if (requestedItems.isEmpty()) {
            throw new ApiException(400, "Order must contain at least one item");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                String accountStatus = Objects.toString(evaluateMerchantAccount(connection, merchantId).get("accountStatus"), "NORMAL");
                if (!"NORMAL".equals(accountStatus)) {
                    throw new ApiException(400, "Merchant account is not allowed to place orders: " + accountStatus);
                }
                Map<String, Object> merchant = getMerchantById(connection, merchantId);
                double subtotal = 0;
                List<Map<String, Object>> items = new ArrayList<>();
                for (Object itemObject : requestedItems) {
                    Map<String, Object> requested = JsonUtil.asObject(itemObject);
                    String productId = JsonUtil.requireString(requested, "productId");
                    int quantity = JsonUtil.requireInt(requested, "quantity");
                    if (quantity <= 0) {
                        throw new ApiException(400, "quantity must be greater than 0");
                    }
                    Map<String, Object> product = getProductById(connection, productId);
                    if (((Number) product.get("stock_level")).intValue() < quantity) {
                        throw new ApiException(400, "Insufficient stock for product " + productId);
                    }
                    double unitPrice = ((Number) product.get("unit_price")).doubleValue();
                    double lineTotal = unitPrice * quantity;
                    subtotal += lineTotal;
                    items.add(Map.of("productId", productId, "quantity", quantity, "unitPrice", unitPrice, "lineTotal", lineTotal));
                }

                double discountAmount = calculateDiscount(merchant, subtotal);
                double totalAmount = Math.max(0, subtotal - discountAmount);
                double creditLimit = ((Number) merchant.get("credit_limit")).doubleValue();
                double balance = ((Number) merchant.get("balance")).doubleValue();
                if (balance + totalAmount > creditLimit) {
                    throw new ApiException(400, "Credit limit exceeded");
                }

                long orderId;
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO orders (merchant_id, order_date, status, subtotal, discount_amount, total_amount)
                        VALUES (?, ?, 'ACCEPTED', ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, merchantId);
                    insert.setString(2, now());
                    insert.setDouble(3, subtotal);
                    insert.setDouble(4, discountAmount);
                    insert.setDouble(5, totalAmount);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        orderId = keys.getLong(1);
                    }
                }

                for (Map<String, Object> item : items) {
                    try (PreparedStatement itemInsert = connection.prepareStatement("""
                            INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
                            VALUES (?, ?, ?, ?, ?)
                            """)) {
                        itemInsert.setLong(1, orderId);
                        itemInsert.setString(2, Objects.toString(item.get("productId")));
                        itemInsert.setInt(3, ((Number) item.get("quantity")).intValue());
                        itemInsert.setDouble(4, ((Number) item.get("unitPrice")).doubleValue());
                        itemInsert.setDouble(5, ((Number) item.get("lineTotal")).doubleValue());
                        itemInsert.executeUpdate();
                    }
                    try (PreparedStatement updateProduct = connection.prepareStatement("""
                            UPDATE products SET stock_level = stock_level - ?, updated_at = ? WHERE product_id = ?
                            """)) {
                        updateProduct.setInt(1, ((Number) item.get("quantity")).intValue());
                        updateProduct.setString(2, now());
                        updateProduct.setString(3, Objects.toString(item.get("productId")));
                        updateProduct.executeUpdate();
                    }
                    insertStockMovement(connection, Objects.toString(item.get("productId")), "SALE", ((Number) item.get("quantity")).intValue(), "ORDER", Long.toString(orderId));
                }

                try (PreparedStatement updateMerchant = connection.prepareStatement("""
                        UPDATE merchants SET balance = ?, pending_discount_credit = 0, updated_at = ? WHERE merchant_id = ?
                        """)) {
                    updateMerchant.setDouble(1, balance + totalAmount);
                    updateMerchant.setString(2, now());
                    updateMerchant.setString(3, merchantId);
                    updateMerchant.executeUpdate();
                }

                Map<String, Object> invoice = generateInvoice(connection, orderId);
                connection.commit();
                return Map.of("message", "Order created", "orderId", orderId, "invoice", invoice, "totalAmount", totalAmount);
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    List<Map<String, Object>> listOrders(Headers headers, Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM orders WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            String merchantId = query.get("merchantId");
            if (isMerchant(headers)) {
                merchantId = authenticatedMerchantId(headers);
            }
            if (merchantId != null && !merchantId.isBlank()) {
                sql.append(" AND merchant_id = ?");
                params.add(merchantId);
            }
            if (query.containsKey("status")) {
                sql.append(" AND status = ?");
                params.add(query.get("status").toUpperCase(Locale.ROOT));
            }
            sql.append(" ORDER BY order_id DESC");
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> orders = rows(rs);
                    for (Map<String, Object> order : orders) {
                        order.put("items", getOrderItems(connection, ((Number) order.get("order_id")).longValue()));
                    }
                    return orders;
                }
            }
        }
    }

    Map<String, Object> getOrder(Headers headers, long orderId) throws SQLException {
        try (Connection connection = connect()) {
            Map<String, Object> order = getOrderById(connection, orderId);
            if (isMerchant(headers) && !authenticatedMerchantId(headers).equals(order.get("merchant_id"))) {
                throw new ApiException(403, "Merchants can only view their own orders");
            }
            order.put("items", getOrderItems(connection, orderId));
            return order;
        }
    }

    List<Map<String, Object>> listPendingOrders() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT * FROM orders WHERE status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY order_id DESC
                     """);
             ResultSet rs = ps.executeQuery()) {
            return rows(rs);
        }
    }

    Map<String, Object> updateOrderStatus(long orderId, Map<String, Object> body) throws SQLException {
        String newStatus = JsonUtil.requireUpper(body, "status");
        if (!List.of("ACCEPTED", "PROCESSING", "DISPATCHED", "DELIVERED").contains(newStatus)) {
            throw new ApiException(400, "Invalid order status");
        }
        if ("DISPATCHED".equals(newStatus)) {
            JsonUtil.requireString(body, "courier");
            JsonUtil.requireString(body, "trackingNumber");
            JsonUtil.requireString(body, "expectedDelivery");
        }
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE orders
                     SET status = ?, dispatched_by = COALESCE(?, dispatched_by),
                         dispatch_date = COALESCE(?, dispatch_date), courier = COALESCE(?, courier),
                         tracking_number = COALESCE(?, tracking_number), expected_delivery = COALESCE(?, expected_delivery),
                         delivered_date = CASE WHEN ? = 'DELIVERED' THEN ? ELSE delivered_date END
                     WHERE order_id = ?
                     """)) {
            ps.setString(1, newStatus);
            setNullable(ps, 2, JsonUtil.optionalString(body, "dispatchedBy"));
            setNullable(ps, 3, JsonUtil.optionalString(body, "dispatchDate"));
            setNullable(ps, 4, JsonUtil.optionalString(body, "courier"));
            setNullable(ps, 5, JsonUtil.optionalString(body, "trackingNumber"));
            setNullable(ps, 6, JsonUtil.optionalString(body, "expectedDelivery"));
            ps.setString(7, newStatus);
            ps.setString(8, "DELIVERED".equals(newStatus) ? now() : null);
            ps.setLong(9, orderId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Order not found");
            }
        }
        return Map.of("message", "Order status updated", "status", newStatus);
    }

    Map<String, Object> generateInvoice(long orderId) throws SQLException {
        try (Connection connection = connect()) {
            return generateInvoice(connection, orderId);
        }
    }

    List<Map<String, Object>> listInvoices(Headers headers, Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            StringBuilder sql = new StringBuilder("SELECT * FROM invoices WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            String merchantId = query.get("merchantId");
            if (isMerchant(headers)) {
                merchantId = authenticatedMerchantId(headers);
            }
            if (merchantId != null && !merchantId.isBlank()) {
                sql.append(" AND merchant_id = ?");
                params.add(merchantId);
            }
            if (query.containsKey("start")) {
                sql.append(" AND issue_date >= ?");
                params.add(query.get("start"));
            }
            if (query.containsKey("end")) {
                sql.append(" AND issue_date <= ?");
                params.add(query.get("end"));
            }
            sql.append(" ORDER BY invoice_id DESC");
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rows(rs);
                }
            }
        }
    }

    Map<String, Object> getInvoice(Headers headers, long invoiceId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM invoices WHERE invoice_id = ?")) {
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "Invoice not found");
                }
                Map<String, Object> invoice = row(rs);
                if (isMerchant(headers) && !authenticatedMerchantId(headers).equals(invoice.get("merchant_id"))) {
                    throw new ApiException(403, "Merchants can only view their own invoices");
                }
                invoice.put("order", getOrderById(connection, ((Number) invoice.get("order_id")).longValue()));
                invoice.put("printableText", invoicePrintableText(connection, invoice));
                return invoice;
            }
        }
    }

    List<Map<String, Object>> listPayments(Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            String sql = query.containsKey("merchantId")
                    ? "SELECT * FROM payments WHERE merchant_id = ? ORDER BY payment_id DESC"
                    : "SELECT * FROM payments ORDER BY payment_id DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (query.containsKey("merchantId")) {
                    ps.setString(1, query.get("merchantId"));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rows(rs);
                }
            }
        }
    }

    Map<String, Object> recordPayment(Map<String, Object> body) throws SQLException {
        String merchantId = JsonUtil.requireString(body, "merchantId");
        double amount = JsonUtil.requireDouble(body, "amount");
        if (amount <= 0) {
            throw new ApiException(400, "amount must be greater than 0");
        }
        String method = JsonUtil.requireUpper(body, "method");
        if (!List.of("BANK_TRANSFER", "CARD", "CHEQUE").contains(method)) {
            throw new ApiException(400, "method must be BANK_TRANSFER, CARD or CHEQUE");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long paymentId;
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO payments (merchant_id, amount, method, reference, payment_date, notes)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, merchantId);
                    insert.setDouble(2, amount);
                    insert.setString(3, method);
                    insert.setString(4, JsonUtil.optionalString(body, "reference"));
                    insert.setString(5, JsonUtil.optionalString(body, "paymentDate", now()));
                    insert.setString(6, JsonUtil.optionalString(body, "notes"));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        paymentId = keys.getLong(1);
                    }
                }

                applyPaymentToInvoices(connection, merchantId, amount);
                updateMerchantBalance(connection, merchantId);
                Map<String, Object> evaluation = evaluateMerchantAccount(connection, merchantId);
                connection.commit();
                return Map.of("message", "Payment recorded", "paymentId", paymentId, "accountEvaluation", evaluation);
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    Map<String, Object> turnoverReport(Map<String, String> query) throws SQLException {
        Range range = requiredRange(query);
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT p.product_id, p.name, SUM(oi.quantity) AS quantity_sold, SUM(oi.line_total) AS revenue
                     FROM order_items oi
                     JOIN orders o ON o.order_id = oi.order_id
                     JOIN products p ON p.product_id = oi.product_id
                     WHERE o.order_date >= ? AND o.order_date <= ?
                     GROUP BY p.product_id, p.name
                     ORDER BY p.product_id
                     """)) {
            ps.setString(1, range.start.toString());
            ps.setString(2, range.end.plusDays(1).toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Turnover Report", data, printableTurnover(range, data));
            }
        }
    }

    Map<String, Object> stockTurnoverReport(Map<String, String> query) throws SQLException {
        Range range = requiredRange(query);
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT p.product_id, p.name,
                            SUM(CASE WHEN sm.movement_type = 'SALE' THEN sm.quantity ELSE 0 END) AS sold_quantity,
                            SUM(CASE WHEN sm.movement_type = 'RESTOCK' THEN sm.quantity ELSE 0 END) AS received_quantity
                     FROM stock_movements sm
                     JOIN products p ON p.product_id = sm.product_id
                     WHERE sm.happened_at >= ? AND sm.happened_at <= ?
                     GROUP BY p.product_id, p.name
                     ORDER BY p.product_id
                     """)) {
            ps.setString(1, range.start.toString());
            ps.setString(2, range.end.plusDays(1).toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Stock Turnover Report", data, printableStockTurnover(range, data));
            }
        }
    }

    Map<String, Object> lowStockReport() throws SQLException {
        try (Connection connection = connect()) {
            List<Map<String, Object>> data = lowStockRows(connection);
            return report("Low Stock Report", data, printableLowStock(data));
        }
    }

    Map<String, Object> merchantOrdersReport(Map<String, String> query) throws SQLException {
        String merchantId = requireQuery(query, "merchantId");
        Range range = requiredRange(query);
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT o.order_id, o.order_date, o.total_amount, o.dispatch_date,
                            CASE WHEN i.status = 'PAID' THEN 'PAID' ELSE 'PENDING' END AS payment_status
                     FROM orders o
                     LEFT JOIN invoices i ON i.order_id = o.order_id
                     WHERE o.merchant_id = ? AND o.order_date >= ? AND o.order_date <= ?
                     ORDER BY o.order_id
                     """)) {
            ps.setString(1, merchantId);
            ps.setString(2, range.start.toString());
            ps.setString(3, range.end.plusDays(1).toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Merchant Orders Report", data, printableMerchantOrders(merchantId, range, data));
            }
        }
    }

    Map<String, Object> merchantActivityReport(Map<String, String> query) throws SQLException {
        String merchantId = requireQuery(query, "merchantId");
        Range range = requiredRange(query);
        try (Connection connection = connect()) {
            List<Map<String, Object>> orders = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT * FROM orders
                    WHERE merchant_id = ? AND order_date >= ? AND order_date <= ?
                    ORDER BY order_id
                    """)) {
                ps.setString(1, merchantId);
                ps.setString(2, range.start.toString());
                ps.setString(3, range.end.plusDays(1).toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> order = row(rs);
                        order.put("items", getOrderItems(connection, ((Number) order.get("order_id")).longValue()));
                        orders.add(order);
                    }
                }
            }
            return report("Merchant Activity Report", orders, printableMerchantActivity(merchantId, range, orders));
        }
    }

    Map<String, Object> merchantInvoicesReport(Map<String, String> query) throws SQLException {
        String merchantId = requireQuery(query, "merchantId");
        Range range = requiredRange(query);
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT * FROM invoices
                     WHERE merchant_id = ? AND issue_date >= ? AND issue_date <= ?
                     ORDER BY invoice_id
                     """)) {
            ps.setString(1, merchantId);
            ps.setString(2, range.start.toString());
            ps.setString(3, range.end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Merchant Invoices Report", data, printableMerchantInvoices(merchantId, range, data));
            }
        }
    }

    Map<String, Object> companyInvoicesReport(Map<String, String> query) throws SQLException {
        Range range = requiredRange(query);
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT * FROM invoices
                     WHERE issue_date >= ? AND issue_date <= ?
                     ORDER BY invoice_id
                     """)) {
            ps.setString(1, range.start.toString());
            ps.setString(2, range.end.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Company Invoices Report", data, printableCompanyInvoices(range, data));
            }
        }
    }

    Map<String, Object> createNonCommercialApplication(Map<String, Object> body) throws SQLException {
        long applicationId;
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO non_commercial_applications (email, status, created_at)
                     VALUES (?, 'PENDING', ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, JsonUtil.requireString(body, "email"));
            ps.setString(2, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                applicationId = keys.getLong(1);
            }
        }
        return Map.of("message", "Application received", "applicationId", applicationId);
    }

    List<Map<String, Object>> listApplications() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM non_commercial_applications ORDER BY application_id DESC")) {
            return rows(rs);
        }
    }

    Map<String, Object> decideApplication(long applicationId, Map<String, Object> body) throws SQLException {
        boolean approved = JsonUtil.requireBoolean(body, "approved");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                String email;
                try (PreparedStatement find = connection.prepareStatement("SELECT email FROM non_commercial_applications WHERE application_id = ?")) {
                    find.setLong(1, applicationId);
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) {
                            throw new ApiException(404, "Application not found");
                        }
                        email = rs.getString(1);
                    }
                }
                String password = approved ? "PU!" + applicationId + "Ab9$" : null;
                String message = approved
                        ? "Approved. Temporary password: " + password
                        : "Rejected. Please contact InfoPharma support if you need clarification.";
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE non_commercial_applications
                        SET status = ?, generated_password = ?, outcome_message = ?, processed_at = ?
                        WHERE application_id = ?
                        """)) {
                    update.setString(1, approved ? "APPROVED" : "REJECTED");
                    update.setString(2, password);
                    update.setString(3, message);
                    update.setString(4, now());
                    update.setLong(5, applicationId);
                    update.executeUpdate();
                }
                logEmail(connection, email, approved ? "IPOS-PU membership approved" : "IPOS-PU membership rejected", message);
                connection.commit();
                return Map.of("message", "Application processed", "emailLogged", true);
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void seed() throws SQLException {
        try (Connection connection = connect()) {
            if (count(connection, "users") == 0) {
                insertUser(connection, "admin", "admin123", "ADMINISTRATOR", null);
                insertUser(connection, "manager", "manager123", "MANAGER", null);
                insertUser(connection, "ops", "ops123", "OPERATIONS_STAFF", null);
                insertUser(connection, "accounts", "accounts123", "ACCOUNTING_STAFF", null);
                insertUser(connection, "merchant1", "merchant123", "MERCHANT", "M0001");
            }
            if (count(connection, "merchants") == 0) {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO merchants (
                            merchant_id, name, email, address, phone, credit_limit, balance, account_status,
                            discount_type, fixed_discount_rate, flexible_rate_tier1, flexible_rate_tier2, flexible_rate_tier3,
                            pending_discount_credit, created_at, updated_at
                        ) VALUES ('M0001', 'Cosymed Ltd.', 'orders@cosymed.example', '3 High Level Drive, Sydenham, SE26 3ET',
                                  '02087780124', 10000, 0, 'NORMAL', 'FIXED', 5, 1, 2, 3, 0, ?, ?)
                        """)) {
                    ps.setString(1, now());
                    ps.setString(2, now());
                    ps.executeUpdate();
                }
            }
            if (count(connection, "products") == 0) {
                seedProduct(connection, "10000001", "Paracetamol", 0.10, 10345, 300);
                seedProduct(connection, "10000002", "Aspirin", 0.50, 12453, 500);
                seedProduct(connection, "10000003", "Analgin", 1.20, 4235, 200);
                seedProduct(connection, "10000004", "Celebrex 100mg", 10.00, 3420, 200);
                seedProduct(connection, "10000005", "Celebrex 200mg", 18.50, 1450, 150);
                seedProduct(connection, "10000006", "Retin-A Tretin 30g", 25.00, 2013, 200);
                seedProduct(connection, "10000007", "Lipitor TB 20mg", 15.50, 1562, 200);
                seedProduct(connection, "10000008", "Claritin CR 60g", 19.50, 2540, 200);
                seedProduct(connection, "20000004", "Iodine tincture", 0.30, 2213, 200);
                seedProduct(connection, "20000005", "Rhynol", 2.50, 1908, 300);
                seedProduct(connection, "30000001", "Ospen", 10.50, 809, 200);
                seedProduct(connection, "30000002", "Amopen", 15.00, 1340, 300);
                seedProduct(connection, "40000001", "Vitamin C", 1.20, 3258, 300);
                seedProduct(connection, "40000002", "Vitamin B12", 1.30, 2673, 300);
            }
        }
    }

    private Map<String, Object> evaluateMerchantAccount(Connection connection, String merchantId) throws SQLException {
        Map<String, Object> merchant = getMerchantById(connection, merchantId);
        LocalDate today = LocalDate.now();
        long maxOverdueDays = 0;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT due_date FROM invoices WHERE merchant_id = ? AND status != 'PAID'
                """)) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate dueDate = LocalDate.parse(rs.getString(1));
                    if (dueDate.isBefore(today)) {
                        maxOverdueDays = Math.max(maxOverdueDays, ChronoUnit.DAYS.between(dueDate, today));
                    }
                }
            }
        }
        String currentStatus = Objects.toString(merchant.get("account_status"), "NORMAL");
        String newStatus = currentStatus;
        List<String> warnings = new ArrayList<>();
        if (maxOverdueDays > 30) {
            newStatus = "IN_DEFAULT";
            warnings.add("Account is in default and needs director approval.");
        } else if (maxOverdueDays > 15) {
            newStatus = "SUSPENDED";
            warnings.add("Account suspended because payment is 15-30 days overdue.");
        } else if (maxOverdueDays > 0) {
            if (!"IN_DEFAULT".equals(currentStatus)) {
                newStatus = "NORMAL";
            }
            warnings.add("Payment reminder is due on login.");
        } else if (!"IN_DEFAULT".equals(currentStatus)) {
            newStatus = "NORMAL";
        }
        if (!newStatus.equals(currentStatus)) {
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE merchants SET account_status = ?, updated_at = ? WHERE merchant_id = ?
                    """)) {
                ps.setString(1, newStatus);
                ps.setString(2, now());
                ps.setString(3, merchantId);
                ps.executeUpdate();
            }
        }
        return Map.of("merchantId", merchantId, "accountStatus", newStatus, "warnings", warnings, "maxOverdueDays", maxOverdueDays);
    }

    private Map<String, Object> generateInvoice(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement existing = connection.prepareStatement("SELECT * FROM invoices WHERE order_id = ?")) {
            existing.setLong(1, orderId);
            try (ResultSet rs = existing.executeQuery()) {
                if (rs.next()) {
                    return row(rs);
                }
            }
        }
        Map<String, Object> order = getOrderById(connection, orderId);
        long invoiceId;
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO invoices (order_id, merchant_id, issue_date, due_date, total_amount, paid_amount, status)
                VALUES (?, ?, ?, ?, ?, 0, 'ISSUED')
                """, Statement.RETURN_GENERATED_KEYS)) {
            LocalDate issueDate = LocalDate.now();
            insert.setLong(1, orderId);
            insert.setString(2, Objects.toString(order.get("merchant_id")));
            insert.setString(3, issueDate.toString());
            insert.setString(4, issueDate.withDayOfMonth(issueDate.lengthOfMonth()).toString());
            insert.setDouble(5, ((Number) order.get("total_amount")).doubleValue());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                invoiceId = keys.getLong(1);
            }
        }
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM invoices WHERE invoice_id = ?")) {
            select.setLong(1, invoiceId);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return row(rs);
            }
        }
    }

    private void applyPaymentToInvoices(Connection connection, String merchantId, double amount) throws SQLException {
        double remaining = amount;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT invoice_id, total_amount, paid_amount
                FROM invoices
                WHERE merchant_id = ? AND status != 'PAID'
                ORDER BY due_date, invoice_id
                """)) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && remaining > 0) {
                    long invoiceId = rs.getLong("invoice_id");
                    double total = rs.getDouble("total_amount");
                    double paid = rs.getDouble("paid_amount");
                    double applied = Math.min(total - paid, remaining);
                    remaining -= applied;
                    try (PreparedStatement update = connection.prepareStatement("""
                            UPDATE invoices
                            SET paid_amount = paid_amount + ?, status = CASE
                                WHEN paid_amount + ? >= total_amount THEN 'PAID'
                                WHEN paid_amount + ? > 0 THEN 'PART_PAID'
                                ELSE status
                            END
                            WHERE invoice_id = ?
                            """)) {
                        update.setDouble(1, applied);
                        update.setDouble(2, applied);
                        update.setDouble(3, applied);
                        update.setLong(4, invoiceId);
                        update.executeUpdate();
                    }
                }
            }
        }
    }

    private void updateMerchantBalance(Connection connection, String merchantId) throws SQLException {
        double balance;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(SUM(total_amount - paid_amount), 0)
                FROM invoices
                WHERE merchant_id = ? AND status != 'PAID'
                """)) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                balance = rs.next() ? rs.getDouble(1) : 0;
            }
        }
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE merchants SET balance = ?, updated_at = ? WHERE merchant_id = ?
                """)) {
            update.setDouble(1, balance);
            update.setString(2, now());
            update.setString(3, merchantId);
            update.executeUpdate();
        }
    }

    private double calculateDiscount(Map<String, Object> merchant, double subtotal) {
        double pendingCredit = ((Number) merchant.get("pending_discount_credit")).doubleValue();
        String discountType = Objects.toString(merchant.get("discount_type"), null);
        if ("FIXED".equalsIgnoreCase(discountType)) {
            return pendingCredit + subtotal * (((Number) merchant.get("fixed_discount_rate")).doubleValue() / 100.0);
        }
        return pendingCredit;
    }

    private void insertStockMovement(Connection connection, String productId, String type, int quantity, String referenceType, String referenceId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO stock_movements (product_id, movement_type, quantity, happened_at, reference_type, reference_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, productId);
            ps.setString(2, type);
            ps.setInt(3, quantity);
            ps.setString(4, now());
            ps.setString(5, referenceType);
            ps.setString(6, referenceId);
            ps.executeUpdate();
        }
    }

    private void logEmail(Connection connection, String recipient, String subject, String body) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO email_log (recipient, subject, body, sent_at, delivery_mode)
                VALUES (?, ?, ?, ?, 'SIMULATED_SMTP')
                """)) {
            ps.setString(1, recipient);
            ps.setString(2, subject);
            ps.setString(3, body);
            ps.setString(4, now());
            ps.executeUpdate();
        }
    }

    private String printableTurnover(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Turnover Report\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | ").append(row.get("name")).append(" | qty=").append(row.get("quantity_sold")).append(" | revenue=").append(row.get("revenue")).append("\n");
        }
        return builder.toString();
    }

    private String printableStockTurnover(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Stock Turnover Report\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | sold=").append(row.get("sold_quantity")).append(" | received=").append(row.get("received_quantity")).append("\n");
        }
        return builder.toString();
    }

    private String printableLowStock(List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Low Stock Report\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | ").append(row.get("name")).append(" | current=").append(row.get("stock_level")).append(" | min=").append(row.get("minimum_stock_level")).append(" | order=").append(row.get("recommended_min_order")).append("\n");
        }
        return builder.toString();
    }

    private String printableMerchantOrders(String merchantId, Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Merchant Orders Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Order ").append(row.get("order_id")).append(" | ordered=").append(row.get("order_date")).append(" | total=").append(row.get("total_amount")).append(" | dispatched=").append(row.get("dispatch_date")).append(" | payment=").append(row.get("payment_status")).append("\n");
        }
        return builder.toString();
    }

    private String printableMerchantActivity(String merchantId, Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Merchant Activity Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Order ").append(row.get("order_id")).append(" total=").append(row.get("total_amount")).append("\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) row.get("items");
            for (Map<String, Object> item : items) {
                builder.append("  - ").append(item.get("product_id")).append(" qty=").append(item.get("quantity")).append(" amount=").append(item.get("line_total")).append("\n");
            }
        }
        return builder.toString();
    }

    private String printableMerchantInvoices(String merchantId, Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Merchant Invoices Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Invoice ").append(row.get("invoice_id")).append(" | order=").append(row.get("order_id")).append(" | total=").append(row.get("total_amount")).append(" | paid=").append(row.get("paid_amount")).append(" | status=").append(row.get("status")).append("\n");
        }
        return builder.toString();
    }

    private String printableCompanyInvoices(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Company Invoices Report\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Invoice ").append(row.get("invoice_id")).append(" | merchant=").append(row.get("merchant_id")).append(" | total=").append(row.get("total_amount")).append(" | paid=").append(row.get("paid_amount")).append(" | status=").append(row.get("status")).append("\n");
        }
        return builder.toString();
    }

    private Map<String, Object> report(String title, Object data, String printableText) {
        return Map.of("title", title, "generatedAt", now(), "data", data, "printableText", printableText);
    }

    private List<Map<String, Object>> lowStockRows(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT product_id, name, stock_level, minimum_stock_level,
                       CAST(ROUND((minimum_stock_level * 1.1) - stock_level) AS INTEGER) AS recommended_min_order
                FROM products
                WHERE stock_level < minimum_stock_level
                ORDER BY product_id
                """);
             ResultSet rs = ps.executeQuery()) {
            return rows(rs);
        }
    }

    private Map<String, Object> getMerchantById(Connection connection, String merchantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM merchants WHERE merchant_id = ?")) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "Merchant not found");
                }
                return row(rs);
            }
        }
    }

    private Map<String, Object> getProductById(Connection connection, String productId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM products WHERE product_id = ?")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "Product not found");
                }
                return row(rs);
            }
        }
    }

    private Map<String, Object> getOrderById(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM orders WHERE order_id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "Order not found");
                }
                return row(rs);
            }
        }
    }

    private List<Map<String, Object>> getOrderItems(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM order_items WHERE order_id = ? ORDER BY order_item_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        }
    }

    private String invoicePrintableText(Connection connection, Map<String, Object> invoice) throws SQLException {
        List<Map<String, Object>> items = getOrderItems(connection, ((Number) invoice.get("order_id")).longValue());
        StringBuilder builder = new StringBuilder();
        builder.append("Invoice ").append(invoice.get("invoice_id")).append("\n");
        for (Map<String, Object> item : items) {
            builder.append(item.get("product_id")).append(" qty=").append(item.get("quantity")).append(" unit=").append(item.get("unit_price")).append(" amount=").append(item.get("line_total")).append("\n");
        }
        builder.append("Amount due: ").append(invoice.get("total_amount"));
        return builder.toString();
    }

    private void seedProduct(Connection connection, String id, String name, double price, int stock, int minimumStock) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO products (product_id, name, unit_price, stock_level, minimum_stock_level, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            String now = now();
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setDouble(3, price);
            ps.setInt(4, stock);
            ps.setInt(5, minimumStock);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
        }
    }

    private void insertUser(Connection connection, String username, String password, String role, String merchantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO users (username, password, role, merchant_id, active, created_at)
                VALUES (?, ?, ?, ?, 1, ?)
                """)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.setString(4, merchantId);
            ps.setString(5, now());
            ps.executeUpdate();
        }
    }

    private int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private static boolean isMerchant(Headers headers) {
        return "MERCHANT".equals(roleFrom(headers));
    }

    private static String roleFrom(Headers headers) {
        String role = headers.getFirst("X-Role");
        return role == null ? null : role.trim().toUpperCase(Locale.ROOT);
    }

    private static String authenticatedMerchantId(Headers headers) {
        String merchantId = headers.getFirst("X-Merchant-Id");
        if (merchantId == null || merchantId.isBlank()) {
            throw new ApiException(401, "Missing X-Merchant-Id header");
        }
        return merchantId;
    }

    private static String requireQuery(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "Missing query parameter: " + key);
        }
        return value;
    }

    private static Range requiredRange(Map<String, String> query) {
        return new Range(LocalDate.parse(requireQuery(query, "start")), LocalDate.parse(requireQuery(query, "end")));
    }

    private static String now() {
        return LocalDateTime.now().toString();
    }

    private static void setNullable(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setObject(index, value);
        }
    }

    private static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(row(rs));
        }
        return rows;
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        int count = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= count; i++) {
            row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), normalizeSqlValue(rs.getObject(i)));
        }
        return row;
    }

    private static Object normalizeSqlValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        return value;
    }

    private record Range(LocalDate start, LocalDate end) {}
}
