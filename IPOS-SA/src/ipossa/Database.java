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
import java.util.*;

/**
 * SQLite-backed service layer for the IPOS-SA subsystem.
 *
 * <p>This class owns schema creation, seed data, authentication sessions,
 * merchant/account logic, catalogue management, ordering, invoicing, payment
 * handling, reporting, and non-commercial application processing for the
 * prototype.</p>
 */
final class Database {
    private final Path dbPath;
    private final IntegrationClient integrationClient;

    /**
     * Creates a database service for the supplied SQLite database file.
     *
     * @param dbPath the path to the SQLite database file
     */
    Database(Path dbPath, IntegrationClient integrationClient) {
        this.dbPath = dbPath;
        this.integrationClient = integrationClient;
    }

    /**
     * Initializes the database schema and seeds the database with default data.
     * <p>
     * This method enables foreign key enforcement, creates all required tables if they do not already
     * exist, and then populates the database with any default records needed for application startup.
     *
     * @throws SQLException if a database access error occurs while creating the schema or seeding data
     */
    void bootstrap() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
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

    /**
     * Authenticates a user by username and password and creates a new session token.
     * <p>
     * If the credentials are valid and the account is active, any existing sessions for the user are removed,
     * a new session is stored, and the returned map contains the authenticated user's identity, role,
     * optional merchant details, and any account warnings.
     *
     * @param username the user's username
     * @param password the user's password
     * @return a map containing the login result, including a session token and user details
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> login(String username, String password) throws SQLException {
        try (Connection connection = connect();
             // retrieves user details
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT username, email, role, merchant_id, active
                     FROM users
                     WHERE username = ? AND password = ?
                     """)) {
            ps.setString(1, username);
            ps.setString(2, password);
            // checks if credentials valid
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(401, "Invalid credentials");
                }
                if (rs.getInt("active") != 1) {
                    throw new ApiException(403, "Account is inactive");
                }
                Map<String, Object> response = new LinkedHashMap<>();
                String sessionToken = UUID.randomUUID().toString();
                // handles sessions
                try (PreparedStatement deleteSessions = connection.prepareStatement("DELETE FROM sessions WHERE username = ?");
                     PreparedStatement insertSession = connection.prepareStatement("""
                             INSERT INTO sessions (session_token, username, role, merchant_id, created_at)
                             VALUES (?, ?, ?, ?, ?)
                             """)) {
                    deleteSessions.setString(1, rs.getString("username"));
                    deleteSessions.executeUpdate();
                    insertSession.setString(1, sessionToken);
                    insertSession.setString(2, rs.getString("username"));
                    insertSession.setString(3, rs.getString("role"));
                    insertSession.setString(4, rs.getString("merchant_id"));
                    insertSession.setString(5, now());
                    insertSession.executeUpdate();
                }
                // handels responses
                response.put("username", rs.getString("username"));
                response.put("email", rs.getString("email"));
                response.put("role", rs.getString("role"));
                response.put("merchantId", rs.getString("merchant_id"));
                response.put("sessionToken", sessionToken);
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

    /**
     * Retrieves the current authenticated session and returns the same shape used by login.
     * <p>
     * The session token is resolved from the request headers. For merchants, the response also
     * includes merchant details and account warnings. For administrator and manager roles, the
     * response includes low-stock warnings used by the dashboard.
     *
     * @param headers the HTTP headers containing the session token
     * @return a map containing the authenticated session details
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> getSession(Headers headers) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            // creates map for response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("username", auth.username());
            response.put("email", auth.email());
            response.put("role", auth.role());
            response.put("merchantId", auth.merchantId());
            response.put("sessionToken", headers.getFirst("X-Session-Token"));
            // handle auth
            if ("MERCHANT".equals(auth.role()) && auth.merchantId() != null) {
                response.put("merchant", getMerchantById(connection, auth.merchantId()));
                response.put("warnings", evaluateMerchantAccount(connection, auth.merchantId()).get("warnings"));
            } else if (List.of("ADMINISTRATOR", "MANAGER", "OPERATIONS_STAFF").contains(auth.role())) {
                // Operations Staff now also see low stock warnings
                response.put("warnings", lowStockRows(connection));
            } else {
                response.put("warnings", List.of());
            }
            return response;
        }
    }

    /**
     * Retrieves all users from the database ordered by username.
     * <p>
     * The returned list contains one map per user, with the selected user fields converted from the
     * result set into a JSON-friendly structure.
     *
     * @return a list of user records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listUsers() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT username, email, role, merchant_id, active, created_at FROM users ORDER BY username")) {
            return rows(rs);
        }
    }

    /**
     * Creates a new user record in the database.
     * <p>
     * The user is inserted with the provided username, password, role, optional merchant association,
     * active status, and the current timestamp.
     *
     * @param body a map containing the user data to persist
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> createUser(Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO users (username, email, password, role, merchant_id, active, created_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, JsonUtil.requireString(body, "username"));
            ps.setString(2, JsonUtil.requireString(body, "email"));
            ps.setString(3, JsonUtil.requireString(body, "password"));
            ps.setString(4, JsonUtil.requireUpper(body, "role"));
            ps.setString(5, JsonUtil.optionalString(body, "merchantId"));
            ps.setInt(6, body.containsKey("active") && !JsonUtil.requireBoolean(body, "active") ? 0 : 1);
            ps.setString(7, now());
            ps.executeUpdate();
        }
        return Map.of("message", "User created");
    }

    /**
     * Updates an existing user record identified by username.
     * <p>
     * Only the fields present in the provided body are updated; missing fields keep their current values.
     * If no user exists for the given username, an exception is thrown.
     *
     * @param username the username of the user to update
     * @param body a map containing the fields to update
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> updateUser(String username, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             //sql statement
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE users
                     SET email = COALESCE(?, email),
                         password = COALESCE(?, password),
                         role = COALESCE(?, role),
                         merchant_id = COALESCE(?, merchant_id),
                         active = COALESCE(?, active)
                     WHERE username = ?
                     """)) {
            // data to be updated
            setNullable(ps, 1, body.containsKey("email") ? JsonUtil.requireString(body, "email") : null);
            setNullable(ps, 2, body.containsKey("password") ? ipossa.JsonUtil.requireString(body, "password") : null);
            setNullable(ps, 3, body.containsKey("role") ? ipossa.JsonUtil.requireUpper(body, "role") : null);
            setNullable(ps, 4, body.containsKey("merchantId") ? JsonUtil.optionalString(body, "merchantId") : null);
            setNullable(ps, 5, body.containsKey("active") ? (JsonUtil.requireBoolean(body, "active") ? 1 : 0) : null);
            ps.setString(6, username);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "User not found");
            }
        }
        return Map.of("message", "User updated");
    }

    /**
     * Deletes a user record identified by username.
     * <p>
     * If no matching user exists, an exception is thrown.
     *
     * @param username the username of the user to delete
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Verifies that the current request is authenticated and has one of the allowed roles.
     * <p>
     * The session token is resolved from the request headers, and the resulting authentication context
     * is returned only if the user's role matches one of the permitted roles.
     *
     * @param headers the HTTP headers containing the session token
     * @param allowedRoles the roles permitted to perform the operation
     * @return the resolved authentication context for the current session
     * @throws SQLException if a database access error occurs
     */
    AuthContext authorize(Headers headers, String... allowedRoles) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            for (String allowedRole : allowedRoles) {
                if (allowedRole.equals(auth.role())) {
                    return auth;
                }
            }
            throw new ApiException(403, "Operation requires one of roles: " + String.join(", ", allowedRoles));
        }
    }

    /**
     * Retrieves merchants visible to the current user, optionally filtered by a search query.
     * <p>
     * Merchant users can only see their own merchant record, while other roles can see all merchants.
     * When a non-blank query is provided, the results are filtered by merchant ID, name, email, or
     * account status.
     *
     * @param headers the HTTP headers containing the session token
     * @param query the query parameters used for filtering results
     * @return a list of merchant records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listMerchants(Headers headers, Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            // begins building statement by selecting all form merchants
            StringBuilder sql = new StringBuilder("SELECT * FROM merchants WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            // Normalise optional search term for case insensitivity
            String search = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
            if ("MERCHANT".equals(auth.role())) {
                // Merchants are restricted to their own record.
                sql.append(" AND merchant_id = ?");
                params.add(auth.merchantId());
            }
            if (!search.isBlank()) {
                // Apply the search filter across key merchant fields.
                sql.append(" AND (LOWER(merchant_id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(account_status) LIKE ?)");
                String pattern = "%" + search + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }
            // order results
            sql.append(" ORDER BY merchant_id");
            // executes statement
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

    /**
     * Retrieves a merchant record by merchant ID and includes its current account warnings.
     * <p>
     * Merchant users may only access their own merchant account; other roles may access any merchant.
     *
     * @param headers the HTTP headers containing the session token
     * @param merchantId the merchant identifier to look up
     * @return a map containing the merchant details and warnings
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> getMerchant(Headers headers, String merchantId) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            if ("MERCHANT".equals(auth.role()) && !Objects.equals(auth.merchantId(), merchantId)) {
                throw new ApiException(403, "Merchants can only view their own account");
            }

            Map<String, Object> merchant = getMerchantById(connection, merchantId);
            merchant.put("warnings", evaluateMerchantAccount(connection, merchantId).get("warnings"));

            // Add discount information for merchant display
            if (merchant.containsKey("discount_type") && merchant.get("discount_type") != null) {
                String discountType = Objects.toString(merchant.get("discount_type"), "");
                if ("FIXED".equals(discountType)) {
                    merchant.put("discount_rate", merchant.get("fixed_discount_rate"));
                    merchant.put("discount_description", "Fixed discount of " + merchant.get("fixed_discount_rate") + "% on all orders");
                } else if ("FLEXIBLE".equals(discountType)) {
                    merchant.put("discount_description", "Flexible discount: 1% (under £1000), 2% (£1000-2000), 3% (over £2000) per month");
                    merchant.put("pending_credit", merchant.get("pending_discount_credit"));
                }
            }

            return merchant;  // FIXED: Return the merchant object, not empty map
        }
    }

    /**
     * Creates a new merchant account and its associated merchant user in a single transaction.
     * <p>
     * The merchant record is inserted first, followed by a linked user account with the MERCHANT role.
     * If either insert fails, the transaction is rolled back so no partial data is persisted.
     *
     * @param body a map containing the merchant and user details to create
     * @return a map containing a success message and the created merchant ID
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> createMerchant(Map<String, Object> body) throws SQLException {
        // Required fields
        String merchantId = JsonUtil.requireString(body, "merchantId");
        String name = JsonUtil.requireString(body, "name");
        String email = JsonUtil.requireString(body, "email");
        String address = JsonUtil.requireString(body, "address");
        double creditLimit = JsonUtil.requireDouble(body, "creditLimit");

        // Password - allow admin to set it, or use default
        String password = JsonUtil.optionalString(body, "password");
        if (password == null || password.isBlank()) {
            password = "Welcome123!";
        }

        // Optional fields
        String phone = JsonUtil.optionalString(body, "phone");
        String discountType = JsonUtil.optionalString(body, "discountType");
        double fixedDiscountRate = JsonUtil.optionalDouble(body, "fixedDiscountRate", 0);

        // Use email as username
        String username = email;

        String now = now();

        try (Connection connection = connect()) {
            // CHECK: Does merchant ID already exist?
            try (PreparedStatement check = connection.prepareStatement("SELECT merchant_id FROM merchants WHERE merchant_id = ?")) {
                check.setString(1, merchantId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        throw new ApiException(400, "Merchant ID '" + merchantId + "' is already taken. Please choose a different Merchant ID.");
                    }
                }
            }

            // CHECK: Does email/username already exist?
            try (PreparedStatement checkUser = connection.prepareStatement("SELECT username FROM users WHERE username = ?")) {
                checkUser.setString(1, username);
                try (ResultSet rs = checkUser.executeQuery()) {
                    if (rs.next()) {
                        throw new ApiException(400, "Email '" + email + "' is already registered. Please use a different email address.");
                    }
                }
            }

            connection.setAutoCommit(false);
            try {
                // Insert merchant record
                try (PreparedStatement merchantPs = connection.prepareStatement("""
                INSERT INTO merchants (
                    merchant_id, name, email, address, phone, credit_limit, balance, account_status,
                    discount_type, fixed_discount_rate, flexible_rate_tier1, flexible_rate_tier2, flexible_rate_tier3,
                    pending_discount_credit, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, 'NORMAL', ?, ?, ?, ?, ?, 0, ?, ?)
                """)) {
                    merchantPs.setString(1, merchantId);
                    merchantPs.setString(2, name);
                    merchantPs.setString(3, email);
                    merchantPs.setString(4, address);
                    merchantPs.setString(5, phone);
                    merchantPs.setDouble(6, creditLimit);
                    merchantPs.setString(7, discountType);
                    merchantPs.setDouble(8, fixedDiscountRate);
                    merchantPs.setDouble(9, 1.0);
                    merchantPs.setDouble(10, 2.0);
                    merchantPs.setDouble(11, 3.0);
                    merchantPs.setString(12, now);
                    merchantPs.setString(13, now);
                    merchantPs.executeUpdate();
                }

                // Create user account
                try (PreparedStatement userPs = connection.prepareStatement("""
                INSERT INTO users (username, email, password, role, merchant_id, active, created_at)
                VALUES (?, ?, ?, 'MERCHANT', ?, 1, ?)
                """)) {
                    userPs.setString(1, username);
                    userPs.setString(2, email);
                    userPs.setString(3, password);
                    userPs.setString(4, merchantId);
                    userPs.setString(5, now);
                    userPs.executeUpdate();
                }

                connection.commit();

                return Map.of(
                        "message", "Merchant account created successfully!",
                        "merchantId", merchantId,
                        "username", username,
                        "password", password,
                        "note", "Merchant can log in using username: " + username
                );

            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Updates an existing merchant record identified by merchant ID.
     * <p>
     * Only the provided fields are changed; omitted fields keep their current values.
     * If the request includes discount plan fields, the merchant's discount plan is updated as well.
     *
     * @param merchantId the merchant identifier to update
     * @param body a map containing the fields to update
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> updateMerchant(String merchantId, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             // create statement and set relecant data
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
                // catch exception when attempting to update non-existing merchant
                throw new ApiException(404, "Merchant not found");
            }
        }
        // updates discount plan
        if (body.containsKey("discountType") || body.containsKey("fixedDiscountRate")) {
            updateDiscountPlan(merchantId, body);
        }
        return Map.of("message", "Merchant updated");
    }

    /**
     * Deletes a merchant record and its associated user account in a single transaction.
     * <p>
     * If the merchant does not exist, the transaction is rolled back and an exception is thrown.
     * Related data that depends on the merchant may also be removed through database cascades.
     *
     * @param merchantId the merchant identifier to delete
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> deleteMerchant(String merchantId) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                // create delete statement and add relevant data
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
                //commit
                connection.commit();
                // catch exceptions and roll back then thrown exception
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return Map.of("message", "Merchant deleted with cascaded orders/invoices/payments");
    }

    /**
     * Retrieves the current balance and account status for a merchant.
     * <p>
     * Merchant users may only view their own balance. The returned result also includes any current
     * account warnings derived from the merchant's payment status.
     *
     * @param headers the HTTP headers containing the session token
     * @param merchantId the merchant identifier whose balance should be retrieved
     * @return a map containing the merchant ID, balance, account status, and warnings
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> getMerchantBalance(Headers headers, String merchantId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("SELECT balance, account_status FROM merchants WHERE merchant_id = ?")) {
            AuthContext auth = resolveAuth(connection, headers);
            if ("MERCHANT".equals(auth.role()) && !Objects.equals(auth.merchantId(), merchantId)) {
                throw new ApiException(403, "Merchants can only view their own balance");
            }
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "Merchant not found");
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("merchantId", merchantId);
                result.put("balance", rs.getDouble(1));
                result.put("accountStatus", rs.getString(2));
                result.put("warnings", evaluateMerchantAccount(connection, merchantId).get("warnings"));
                return result;
            }
        }
    }

    /**
     * Updates the discount plan settings for a merchant.
     * <p>
     * The discount type must be either FIXED or FLEXIBLE. The related discount rate values are stored
     * alongside the plan and the merchant's modification timestamp is refreshed.
     *
     * @param merchantId the merchant identifier whose discount plan should be updated
     * @param body a map containing the discount plan fields
     * @return a map containing a success message and the applied discount type
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> updateDiscountPlan(String merchantId, Map<String, Object> body) throws SQLException {
        String type = JsonUtil.requireUpper(body, "discountType");
        if (!List.of("FIXED", "FLEXIBLE").contains(type)) {
            throw new ApiException(400, "discountType must be FIXED or FLEXIBLE");
        }
        try (Connection connection = connect();
             // create sql statement and add relevant data
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

    /**
     * Removes the discount plan from a merchant.
     * <p>
     * This clears the discount type, resets the fixed discount rate to zero, and updates the merchant's
     * modification timestamp.
     *
     * @param merchantId the merchant identifier whose discount plan should be removed
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Restores a merchant's account status after the required approval check.
     * <p>
     * A director approval flag must be present and true. The new account status must be either NORMAL
     * or SUSPENDED. If the merchant does not exist, an exception is thrown.
     *
     * @param merchantId the merchant identifier whose status should be restored
     * @param body a map containing the approval flag and the new status
     * @return a map containing a success message and the applied status
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> restoreMerchant(String merchantId, Map<String, Object> body) throws SQLException {
        // throw error if not authorized
        if (!JsonUtil.requireBoolean(body, "directorApproved")) {
            throw new ApiException(400, "Director approval is required");
        }
        // defines new status
        String newStatus = JsonUtil.requireUpper(body, "newStatus");
        if (!List.of("NORMAL", "SUSPENDED").contains(newStatus)) {
            throw new ApiException(400, "newStatus must be NORMAL or SUSPENDED");
        }
        try (Connection connection = connect()) {
            Map<String, Object> merchant = getMerchantById(connection, merchantId);
            String currentStatus = Objects.toString(merchant.get("account_status"), "NORMAL");
            MerchantDebtStatus debtStatus = merchantDebtStatus(connection, merchantId);

            if ("NORMAL".equals(newStatus) && debtStatus.hasOverdueInvoices()) {
                throw new ApiException(400, "Merchant cannot be restored to NORMAL until all overdue invoices are cleared by payment");
            }
            if ("NORMAL".equals(newStatus) && debtStatus.outstandingBalance() > 0) {
                throw new ApiException(400, "Merchant cannot be restored to NORMAL while an outstanding balance remains");
            }
            if ("SUSPENDED".equals(newStatus) && !debtStatus.hasOverdueInvoices()) {
                throw new ApiException(400, "Merchant cannot remain SUSPENDED when no overdue invoices remain");
            }
            if (!"IN_DEFAULT".equals(currentStatus) && "NORMAL".equals(newStatus)) {
                throw new ApiException(400, "Restore flow is intended for merchants currently in default");
            }

            try (PreparedStatement ps = connection.prepareStatement("""
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
        }
        return Map.of("message", "Merchant restored", "newStatus", newStatus);
    }

    /**
     * Evaluates the current account status of a merchant based on overdue invoices.
     * <p>
     * The merchant's invoices are inspected to determine the maximum number of overdue days, and the
     * account status is updated accordingly. Any resulting warnings are returned with the evaluation.
     *
     * @param merchantId the merchant identifier to evaluate
     * @return a map containing the merchant ID, computed account status, warnings, and maximum overdue days
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> evaluateMerchantAccount(String merchantId) throws SQLException {
        try (Connection connection = connect()) {
            return evaluateMerchantAccount(connection, merchantId);
        }
    }

    /**
     * Retrieves all products from the database ordered by product ID.
     * <p>
     * The returned list contains one map per product, with the selected product fields converted into
     * a JSON-friendly structure.
     *
     * @return a list of product records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listProducts() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM products ORDER BY product_id")) {
            return rows(rs);
        }
    }

    /**
     * Searches for products whose name or product ID matches the supplied query.
     * <p>
     * The search is case-insensitive for product names and returns matching products ordered by
     * product ID.
     *
     * @param query the search text to match against product names and IDs
     * @return a list of matching product records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> searchProducts(String query) throws SQLException {
        try (Connection connection = connect();
             // it searches for a product, what do you expect the comments to say?
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

    /**
     * Retrieves a single product by its product ID.
     * <p>
     * The product is loaded from the database and returned as a JSON-friendly map.
     * If no product exists for the given ID, an exception is thrown.
     *
     * @param productId the product identifier to look up
     * @return a map containing the product details
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> getProduct(String productId) throws SQLException {
        try (Connection connection = connect()) {
            // this exists to overload prod
            return getProductById(connection, productId);
        }
    }

    /**
     * Creates a new product record in the database.
     * <p>
     * The product is inserted with the provided identifier, name, pricing, stock levels, and timestamps.
     *
     * @param body a map containing the product data to persist
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> createProduct(Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             // creates sql statement and adds all relevent data and then executes it and thats about it.
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO products (
                         product_id, name, package_type, unit, units_in_pack,
                         unit_price, stock_level, minimum_stock_level, created_at, updated_at
                     )
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            String now = now();
            ps.setString(1, JsonUtil.requireString(body, "productId"));
            ps.setString(2, JsonUtil.requireString(body, "name"));
            ps.setString(3, JsonUtil.optionalString(body, "packageType"));
            ps.setString(4, JsonUtil.optionalString(body, "unit"));
            setNullable(ps, 5, body.containsKey("unitsInPack") ? JsonUtil.requireInt(body, "unitsInPack") : null);
            ps.setDouble(6, JsonUtil.requireDouble(body, "unitPrice"));
            ps.setInt(7, JsonUtil.requireInt(body, "stockLevel"));
            ps.setInt(8, JsonUtil.requireInt(body, "minimumStockLevel"));
            ps.setString(9, now);
            ps.setString(10, now);
            ps.executeUpdate();
        }
        return Map.of("message", "Product created");
    }

    /**
     * Updates an existing product record identified by product ID.
     * <p>
     * Only the fields present in the request body are changed; omitted fields keep their current values.
     * If no matching product exists, an exception is thrown.
     *
     * @param productId the product identifier to update
     * @param body a map containing the fields to update
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> updateProduct(String productId, Map<String, Object> body) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE products
                     SET name = COALESCE(?, name),
                         package_type = COALESCE(?, package_type),
                         unit = COALESCE(?, unit),
                         units_in_pack = COALESCE(?, units_in_pack),
                         unit_price = COALESCE(?, unit_price),
                         stock_level = COALESCE(?, stock_level),
                         minimum_stock_level = COALESCE(?, minimum_stock_level),
                         updated_at = ?
                     WHERE product_id = ?
                     """)) {
            setNullable(ps, 1, body.get("name"));
            setNullable(ps, 2, body.containsKey("packageType") ? JsonUtil.optionalString(body, "packageType") : null);
            setNullable(ps, 3, body.containsKey("unit") ? JsonUtil.optionalString(body, "unit") : null);
            setNullable(ps, 4, body.containsKey("unitsInPack") ? JsonUtil.requireInt(body, "unitsInPack") : null);
            setNullable(ps, 5, body.containsKey("unitPrice") ? JsonUtil.requireDouble(body, "unitPrice") : null);
            setNullable(ps, 6, body.containsKey("stockLevel") ? JsonUtil.requireInt(body, "stockLevel") : null);
            setNullable(ps, 7, body.containsKey("minimumStockLevel") ? JsonUtil.requireInt(body, "minimumStockLevel") : null);
            ps.setString(8, now());
            ps.setString(9, productId);
            if (ps.executeUpdate() == 0) {
                throw new ApiException(404, "Product not found");
            }
        }
        return Map.of("message", "Product updated");
    }

    /**
     * Deletes a product record identified by product ID.
     * <p>
     * If no matching product exists, an exception is thrown.
     *
     * @param productId the product identifier to delete
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> deleteProduct(String productId) throws SQLException {
        try (Connection connection = connect()) {
            // Check if product has any order items
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT COUNT(*) FROM order_items WHERE product_id = ?")) {
                check.setString(1, productId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new ApiException(400, "Cannot delete product - it has " + rs.getInt(1) +
                                " existing orders. The product is archived instead.");
                    }
                }
            }

            try (PreparedStatement deleteMovements = connection.prepareStatement(
                    "DELETE FROM stock_movements WHERE product_id = ?")) {
                deleteMovements.setString(1, productId);
                deleteMovements.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM products WHERE product_id = ?")) {
                ps.setString(1, productId);
                if (ps.executeUpdate() == 0) {
                    throw new ApiException(404, "Product not found");
                }
            }
        }
        return Map.of("message", "Product deleted");
    }

    /**
     * Increases the stock level for a product and records the stock movement.
     * <p>
     * The supplied quantity must be greater than zero. The product stock is updated in a transaction and
     * a corresponding stock movement entry is created to reflect the restock.
     *
     * @param productId the product identifier whose stock should be increased
     * @param body a map containing the restock quantity and optional reference
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> addStock(String productId, Map<String, Object> body) throws SQLException {
        int quantity = JsonUtil.requireInt(body, "quantity");
        if (quantity <= 0) {
            throw new ApiException(400, "quantity must be greater than 0");
        }
        try (Connection connection = connect()) {
            // Update stock and movement history in one transaction.
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
                // commit sql statement
                insertStockMovement(connection, productId, "RESTOCK", quantity, "MANUAL_RESTOCK", JsonUtil.optionalString(body, "reference"));
                connection.commit();
            } catch (SQLException ex) {
                // if exception caught, roll back query and throw exception
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return Map.of("message", "Stock increased");
    }

    /**
     * Updates the minimum stock threshold for a product.
     * <p>
     * The supplied minimum stock level must be non-negative. If no matching product exists, an exception
     * is thrown.
     *
     * @param productId the product identifier whose minimum stock level should be updated
     * @param body a map containing the new minimum stock level
     * @return a map containing a success message
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> updateMinimumStock(String productId, Map<String, Object> body) throws SQLException {
        int minimumStock = JsonUtil.requireInt(body, "minimumStockLevel");
        if (minimumStock < 0) {
            throw new ApiException(400, "minimumStockLevel must be non-negative");
        }
        // updates stock level for given product ID
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE products SET minimum_stock_level = ?, updated_at = ? WHERE product_id = ?
                     """)) {
            ps.setInt(1, minimumStock);
            ps.setString(2, now());
            ps.setString(3, productId);
            if (ps.executeUpdate() == 0) {
                // thwos exception if you try and update stock for product that does not exist
                throw new ApiException(404, "Product not found");
            }
        }
        return Map.of("message", "Minimum stock level updated");
    }

    /**
     * Generates a formatted low stock report as per Appendix 3.
     */
    Map<String, Object> getFormattedLowStockReport() throws SQLException {
        try (Connection connection = connect()) {
            List<Map<String, Object>> lowStockItems = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT product_id, name, stock_level, minimum_stock_level, " +
                            "CAST(ROUND((minimum_stock_level * 1.1) - stock_level) AS INTEGER) AS recommended_min_order " +
                            "FROM products WHERE stock_level < minimum_stock_level ORDER BY product_id")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("item_id", rs.getString("product_id"));
                        item.put("description", rs.getString("name"));
                        item.put("availability", rs.getInt("stock_level"));
                        item.put("stock_limit", rs.getInt("minimum_stock_level"));
                        item.put("recommended_min_order", rs.getInt("recommended_min_order"));
                        lowStockItems.add(item);
                    }
                }
            }

            // Build formatted report as per Appendix 3
            StringBuilder report = new StringBuilder();
            report.append("Low Stock Level Report\n");
            report.append("Generated: ").append(LocalDate.now()).append("\n");
            report.append("By: System Administrator\n\n");
            report.append(String.format("%-12s %-30s %-12s %-12s %-18s\n",
                    "Item ID", "Description", "Availability", "Stock Limit", "Recommended Min Order"));
            report.append("-------------------------------------------------------------------------------\n");

            for (Map<String, Object> item : lowStockItems) {
                report.append(String.format("%-12s %-30s %-12d %-12d %-18d\n",
                        item.get("item_id"),
                        truncate(String.valueOf(item.get("description")), 30),
                        item.get("availability"),
                        item.get("stock_limit"),
                        item.get("recommended_min_order")));
            }

            return Map.of(
                    "title", "Low Stock Report",
                    "generatedAt", now(),
                    "data", lowStockItems,
                    "printableText", report.toString()
            );
        }
    }

    private String truncate(String str, int length) {
        if (str.length() <= length) return str;
        return str.substring(0, length - 3) + "...";
    }

    /**
     * Creates a new order for the authenticated merchant and generates an invoice for it.
     * <p>
     * The merchant may only place orders for their own account. Each requested item must have a positive
     * quantity and sufficient stock, and the merchant account must be in a valid state to place orders.
     * The order, order items, stock changes, merchant balance update, and invoice generation are handled
     * in a single transaction.
     *
     * @param headers the HTTP headers containing the session token
     * @param body a map containing the merchant ID and order items
     * @return a map containing a success message, order ID, generated invoice, and total amount
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> createOrder(Headers headers, Map<String, Object> body) throws SQLException {

        String merchantId = JsonUtil.requireString(body, "merchantId");
        List<Object> requestedItems = JsonUtil.requireArray(body, "items");
        // throws error if no items in order
        if (requestedItems.isEmpty()) {
            throw new ApiException(400, "Order must contain at least one item");
        }
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            // throws error if merchant tries to place order for any account other than their own
            if (!Objects.equals(auth.merchantId(), merchantId)) {
                throw new ApiException(403, "Merchants can only place orders for their own account");
            }
            connection.setAutoCommit(false);
            try {
                // gets merchant FIRST so we can check account status properly
                Map<String, Object> merchant = getMerchantById(connection, merchantId);

                // P0 FIX: Better account status checking with specific error messages
                String accountStatus = Objects.toString(merchant.get("account_status"), "NORMAL");

                // Check if account is suspended - no new orders allowed
                if ("SUSPENDED".equals(accountStatus)) {
                    throw new ApiException(403, "Account is SUSPENDED due to overdue payments (15-30 days). Please contact InfoPharma to restore your account.");
                }

                // Check if account is in default - needs director approval
                if ("IN_DEFAULT".equals(accountStatus)) {
                    throw new ApiException(403, "Account is IN DEFAULT (30+ days overdue). Please contact the Director of Operations to restore your account.");
                }

                // Normal account check
                if (!"NORMAL".equals(accountStatus)) {
                    throw new ApiException(400, "Merchant account is not allowed to place orders. Status: " + accountStatus);
                }

                double subtotal = 0;
                List<Map<String, Object>> items = new ArrayList<>();

                // Iterates through all items in order
                for (Object itemObject : requestedItems) {
                    // throws error if item quantity less than 0
                    Map<String, Object> requested = JsonUtil.asObject(itemObject);
                    String productId = JsonUtil.requireString(requested, "productId");
                    int quantity = JsonUtil.requireInt(requested, "quantity");
                    if (quantity <= 0) {
                        throw new ApiException(400, "quantity must be greater than 0");
                    }

                    // throws error if insufficient stock
                    Map<String, Object> product = getProductById(connection, productId);
                    int currentStock = ((Number) product.get("stock_level")).intValue();
                    if (currentStock < quantity) {
                        throw new ApiException(400, "Insufficient stock for product " + productId + ". Available: " + currentStock);
                    }

                    // adds price to subtotal
                    double unitPrice = ((Number) product.get("unit_price")).doubleValue();
                    double lineTotal = unitPrice * quantity;
                    subtotal += lineTotal;
                    items.add(Map.of("productId", productId, "quantity", quantity, "unitPrice", unitPrice, "lineTotal", lineTotal));
                }

                // P0 FIX: Calculate discount based on plan (including pending credit)
                double pendingCredit = ((Number) merchant.get("pending_discount_credit")).doubleValue();
                String discountType = Objects.toString(merchant.get("discount_type"), null);
                double discountAmount = 0;

                if ("FIXED".equalsIgnoreCase(discountType)) {
                    double fixedRate = ((Number) merchant.get("fixed_discount_rate")).doubleValue();
                    discountAmount = pendingCredit + (subtotal * (fixedRate / 100.0));
                } else if ("FLEXIBLE".equalsIgnoreCase(discountType)) {
                    // For flexible, only apply pending credit at order time
                    // Monthly discount is calculated separately at month end
                    discountAmount = pendingCredit;
                } else {
                    discountAmount = pendingCredit;
                }

                double totalAmount = Math.max(0, subtotal - discountAmount);

                double creditLimit = ((Number) merchant.get("credit_limit")).doubleValue();
                double balance = ((Number) merchant.get("balance")).doubleValue();
                if (balance + totalAmount > creditLimit) {
                    throw new ApiException(400, String.format(
                            "Credit limit would be exceeded. Current balance: £%.2f, Order total: £%.2f, Credit limit: £%.2f",
                            balance, totalAmount, creditLimit));
                }

                // sql statement to create order in db
                long orderId;
                try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO orders (merchant_id, order_date, status, subtotal, discount_amount, total_amount)
                    VALUES (?, ?, 'PENDING', ?, ?, ?)
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

                // adds items to order using table orderItems
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

                    // updates stock level in DB
                    try (PreparedStatement updateProduct = connection.prepareStatement("""
                        UPDATE products SET stock_level = stock_level - ?, updated_at = ? WHERE product_id = ?
                        """)) {
                        updateProduct.setInt(1, ((Number) item.get("quantity")).intValue());
                        updateProduct.setString(2, now());
                        updateProduct.setString(3, Objects.toString(item.get("productId")));
                        updateProduct.executeUpdate();
                    }
                    insertStockMovement(connection, Objects.toString(item.get("productId")), "SALE",
                            ((Number) item.get("quantity")).intValue(), "ORDER", Long.toString(orderId));
                }

                // P0 FIX: Reset pending discount credit after use
                try (PreparedStatement updateMerchant = connection.prepareStatement("""
                    UPDATE merchants SET balance = ?, pending_discount_credit = 0, updated_at = ? WHERE merchant_id = ?
                    """)) {
                    updateMerchant.setDouble(1, balance + totalAmount);
                    updateMerchant.setString(2, now());
                    updateMerchant.setString(3, merchantId);
                    updateMerchant.executeUpdate();
                }

                connection.commit();

                // P0 FIX: Return discount info in response for frontend display
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("message", "Order created");
                response.put("orderId", orderId);
                response.put("totalAmount", totalAmount);
                response.put("subtotal", subtotal);
                response.put("discountAmount", discountAmount);
                response.put("discountApplied", discountAmount > 0);
                response.put("invoiceGenerated", false);
                if ("FIXED".equalsIgnoreCase(discountType) && discountAmount > 0) {
                    response.put("discountRate", merchant.get("fixed_discount_rate"));
                    response.put("discountType", "FIXED");
                } else if (pendingCredit > 0) {
                    response.put("discountType", "PENDING_CREDIT");
                    response.put("pendingCreditUsed", pendingCredit);
                }

                return response;

            } catch (SQLException ex) {
                // if sql Exception arises, rollback all sql and then throw ex
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Retrieves orders visible to the current user, optionally filtered by merchant ID, order ID, and status.
     * <p>
     * Merchant users can only see their own orders. Each returned order includes its associated items and
     * the results are ordered by most recent order first.
     *
     * @param headers the HTTP headers containing the session token
     * @param query the query parameters used to filter the order list
     * @return a list of order records with their items
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listOrders(Headers headers, Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);
            StringBuilder sql = new StringBuilder("SELECT * FROM orders WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            String merchantId = query.get("merchantId");
            if ("MERCHANT".equals(auth.role())) {
                merchantId = auth.merchantId();
            }
            if (merchantId != null && !merchantId.isBlank()) {
                sql.append(" AND merchant_id = ?");
                params.add(merchantId);
            }
            if (query.containsKey("orderId") && !query.get("orderId").isBlank()) {
                sql.append(" AND order_id = ?");
                params.add(Long.parseLong(query.get("orderId")));
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

    /**
     * Retrieves a single order by ID and includes its line items.
     * <p>
     * Merchant users can only view their own orders. If the order does not exist, an exception is thrown.
     *
     * @param headers the HTTP headers containing the session token
     * @param orderId the order identifier to look up
     * @return a map containing the order details and its items
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> getOrder(Headers headers, long orderId) throws SQLException {
        try (Connection connection = connect()) {
            // get order by ID and auth
            Map<String, Object> order = getOrderById(connection, orderId);
            AuthContext auth = resolveAuth(connection, headers);

            // throws exception if merchant trys to view other merchants things
            if ("MERCHANT".equals(auth.role()) && !Objects.equals(auth.merchantId(), order.get("merchant_id"))) {
                throw new ApiException(403, "Merchants can only view their own orders");
            }
            order.put("items", getOrderItems(connection, orderId));
            return order;
        }
    }

    /**
     * Retrieves all orders that are not yet delivered or cancelled.
     * <p>
     * The results are returned in descending order by order ID.
     *
     * @return a list of pending order records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listPendingOrders() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT * FROM orders WHERE status NOT IN ('DELIVERED', 'CANCELLED') ORDER BY order_id DESC
                     """);
             ResultSet rs = ps.executeQuery()) {
            return rows(rs);
        }
    }

    /**
     * Updates the status of an existing order.
     * <p>
     * Only valid status transitions are allowed. When dispatching an order, courier and tracking details
     * are required; when delivering an order, the delivered date is recorded automatically.
     *
     * @param orderId the order identifier to update
     * @param body a map containing the new status and any related dispatch details
     * @return a map containing a success message and the updated status
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> updateOrderStatus(long orderId, Map<String, Object> body) throws SQLException {
        String newStatus = JsonUtil.requireUpper(body, "status");
        if (!List.of("PENDING", "PROCESSING", "DISPATCHED", "DELIVERED").contains(newStatus)) {
            throw new ApiException(400, "Invalid order status");
        }

        // Validate dispatch details when moving to DISPATCHED
        if ("DISPATCHED".equals(newStatus)) {
            // All these fields are required for dispatch
            JsonUtil.requireString(body, "courier");
            JsonUtil.requireString(body, "trackingNumber");
            JsonUtil.requireString(body, "expectedDelivery");
            JsonUtil.requireString(body, "dispatchedBy");

            // Validate expected delivery is a future date
            String expectedDelivery = JsonUtil.requireString(body, "expectedDelivery");
            try {
                LocalDate expectedDate = LocalDate.parse(expectedDelivery);
                if (expectedDate.isBefore(LocalDate.now())) {
                    throw new ApiException(400, "Expected delivery date must be today or in the future");
                }
            } catch (Exception e) {
                throw new ApiException(400, "Invalid expected delivery date format. Use YYYY-MM-DD");
            }
        }

        try (Connection connection = connect()) {
            Map<String, Object> order = getOrderById(connection, orderId);
            String currentStatus = Objects.toString(order.get("status"), "PENDING");

            if (!isValidOrderTransition(currentStatus, newStatus)) {
                throw new ApiException(400, "Invalid order transition from " + currentStatus + " to " + newStatus);
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE orders SET status = ?, dispatched_by = COALESCE(?, dispatched_by), " +
                            "dispatch_date = COALESCE(?, dispatch_date), courier = COALESCE(?, courier), " +
                            "tracking_number = COALESCE(?, tracking_number), expected_delivery = COALESCE(?, expected_delivery), " +
                            "delivered_date = CASE WHEN ? = 'DELIVERED' THEN ? ELSE delivered_date END " +
                            "WHERE order_id = ?")) {
                ps.setString(1, newStatus);
                setNullable(ps, 2, JsonUtil.optionalString(body, "dispatchedBy"));
                setNullable(ps, 3, "DISPATCHED".equals(newStatus) ? now() : JsonUtil.optionalString(body, "dispatchDate"));
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

            Map<String, Object> updatedOrder = getOrderById(connection, orderId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "Order status updated");
            response.put("status", newStatus);

            if ("DELIVERED".equals(newStatus)) {
                response.put("integration", integrationClient.notifyCaDelivery(updatedOrder, deliverySyncItems(connection, orderId)));
            }

            return response;
        }
    }

    /**
     * Generates or retrieves the invoice associated with a given order.
     * <p>
     * If an invoice already exists for the order, it is returned as-is; otherwise, a new invoice is created
     * using the order's total amount and merchant information.
     *
     * @param orderId the order identifier for which to generate an invoice
     * @return a map containing the invoice details
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> generateInvoice(long orderId) throws SQLException {
        try (Connection connection = connect()) {
            return generateInvoice(connection, orderId);
        }
    }

    /**
     * Retrieves invoices visible to the current user, optionally filtered by merchant ID and issue date range.
     * <p>
     * Merchant users can only see their own invoices. Results are returned in descending order by invoice ID.
     *
     * @param headers the HTTP headers containing the session token
     * @param query the query parameters used to filter the invoice list
     * @return a list of invoice records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listInvoices(Headers headers, Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            AuthContext auth = resolveAuth(connection, headers);

            // begins creating sql statement
            StringBuilder sql = new StringBuilder("SELECT * FROM invoices WHERE 1 = 1");
            List<Object> params = new ArrayList<>();
            String merchantId = query.get("merchantId");

            // if user merchant retrieve ID
            if ("MERCHANT".equals(auth.role())) {
                merchantId = auth.merchantId();
            }

            // if user is merchant only retreive invoices from that merchant
            if (merchantId != null && !merchantId.isBlank()) {
                sql.append(" AND merchant_id = ?");
                params.add(merchantId);
            }

            // bound by times if requested
            if (query.containsKey("start")) {
                sql.append(" AND issue_date >= ?");
                params.add(query.get("start"));
            }
            if (query.containsKey("end")) {
                sql.append(" AND issue_date <= ?");
                params.add(query.get("end"));
            }

            // order invoices by ID and run statement
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

    /**
     * Retrieves a single invoice by ID and includes its related order and printable text.
     * <p>
     * Merchant users can only view invoices belonging to their own merchant account. If the invoice does
     * not exist, an exception is thrown.
     *
     * @param headers the HTTP headers containing the session token
     * @param invoiceId the invoice identifier to look up
     * @return a map containing the invoice details, related order, and printable text
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> getInvoice(Headers headers, long invoiceId) throws SQLException {
        try (Connection connection = connect();
            // creates sql statement and adds relevent data
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM invoices WHERE invoice_id = ?")) {
            AuthContext auth = resolveAuth(connection, headers);
            ps.setLong(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                // throws error if invoice does not exist
                if (!rs.next()) {
                    throw new ApiException(404, "Invoice not found");
                }
                Map<String, Object> invoice = row(rs);
                if ("MERCHANT".equals(auth.role()) && !Objects.equals(auth.merchantId(), invoice.get("merchant_id"))) {
                    throw new ApiException(403, "Merchants can only view their own invoices");
                }
                invoice.put("order", getOrderById(connection, ((Number) invoice.get("order_id")).longValue()));
                invoice.put("printableText", invoicePrintableText(connection, invoice));
                return invoice;
            }
        }
    }

    /**
     * Retrieves payments from the database, optionally filtered by merchant ID.
     * <p>
     * Results are returned in descending order by payment ID.
     *
     * @param query the query parameters used to filter the payment list
     * @return a list of payment records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listPayments(Map<String, String> query) throws SQLException {
        try (Connection connection = connect()) {
            // creates sql and adds relevent data
            String sql = query.containsKey("merchantId")
                    ? "SELECT * FROM payments WHERE merchant_id = ? ORDER BY payment_id DESC"
                    : "SELECT * FROM payments ORDER BY payment_id DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (query.containsKey("merchantId")) {
                    // filters by merchant id if requested
                    ps.setString(1, query.get("merchantId"));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    return rows(rs);
                }
            }
        }
    }

    /**
     * Records a payment for a merchant and applies it to outstanding invoices.
     * <p>
     * The payment amount must be greater than zero, and the payment method must be one of the supported
     * values. The payment is stored in a transaction, then applied to invoices and used to refresh the
     * merchant's balance and account evaluation.
     *
     * @param body a map containing the payment details
     * @return a map containing a success message, payment ID, and account evaluation
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> recordPayment(Map<String, Object> body) throws SQLException {
        String merchantId = JsonUtil.requireString(body, "merchantId");
        double amount = JsonUtil.requireDouble(body, "amount");

        // throws error if ammount less than or equal to 0
        if (amount <= 0) {
            throw new ApiException(400, "amount must be greater than 0");
        }

        // throws error if payment is invalid type
        String method = JsonUtil.requireUpper(body, "method");
        if (!List.of("BANK_TRANSFER", "CARD", "CHEQUE").contains(method)) {
            throw new ApiException(400, "method must be BANK_TRANSFER, CARD or CHEQUE");
        }
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                // creates sql statement and adds relevent data
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

                // apply payment to invoice and updates merchant ballance
                applyPaymentToInvoices(connection, merchantId, amount);
                updateMerchantBalance(connection, merchantId);
                Map<String, Object> evaluation = evaluateMerchantAccount(connection, merchantId);
                connection.commit();
                return Map.of("message", "Payment recorded", "paymentId", paymentId, "accountEvaluation", evaluation);
            } catch (SQLException ex) {
                // if error caught roll back sql and throw exception
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Returns the currently configured cross-subsystem integration endpoints.
     *
     * @return a map describing the configured CA and PU integrations
     */
    Map<String, Object> describeIntegrations() {
        return integrationClient.describeConfiguration();
    }

    /**
     * Relays a mail request to Team C's IPOS-PU subsystem using the agreed JSON contract.
     *
     * @param body a map containing sender, receivers, subject, and body
     * @return a map describing the relay result
     */
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

    /**
     * Relays a payment request to Team C's IPOS-PU subsystem using the agreed JSON contract.
     *
     * @param body a map containing the payment details expected by Team C
     * @return a map describing the relay result
     */
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

    /**
     * Relays a single stock item to Team B's IPOS-CA subsystem using the agreed JSON contract.
     *
     * @param body a map containing the CA stock payload fields
     * @return a map describing the relay result
     */
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

    /**
     * Generates a turnover report for products sold within the requested date range.
     * <p>
     * The report aggregates sold quantity and revenue per product and returns both structured data and a
     * printable text representation.
     *
     * @param query the query parameters containing the required date range
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> turnoverReport(Map<String, String> query) throws SQLException {
        Range range = requiredRange(query);
        try (Connection connection = connect();
             // creates report sql query and adds relevant data
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

    /**
     * Generates a stock turnover report for the requested date range.
     * <p>
     * The report aggregates sold and received quantities per product from stock movement records and
     * returns both structured data and a printable text representation.
     *
     * @param query the query parameters containing the required date range
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> stockTurnoverReport(Map<String, String> query) throws SQLException {
        Range range = requiredRange(query);
        try (Connection connection = connect();
             //creates stock turnover report sql query and adds relevent data
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

    /**
     * Generates a low stock report for products currently below their minimum stock level.
     * <p>
     * The report includes structured data and a printable text representation of the low stock items.
     *
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> lowStockReport() throws SQLException {
        try (Connection connection = connect()) {
            List<Map<String, Object>> data = lowStockRows(connection);
            return report("Low Stock Report", data, printableLowStock(data));
        }
    }

    /**
     * Generates a debtor reminders report for merchants with overdue invoices.
     * <p>
     * The report lists merchants with unpaid invoices that are past due, sorted by the most overdue
     * accounts first, and returns both structured data and a printable text representation.
     *
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> debtorRemindersReport() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT m.merchant_id, m.name, m.account_status, m.balance,
                            MIN(i.due_date) AS oldest_due_date,
                            MAX(CAST(julianday('now') - julianday(i.due_date) AS INTEGER)) AS overdue_days
                     FROM merchants m
                     JOIN invoices i ON i.merchant_id = m.merchant_id
                     WHERE i.status != 'PAID' AND i.due_date < date('now')
                     GROUP BY m.merchant_id, m.name, m.account_status, m.balance
                     ORDER BY overdue_days DESC, m.merchant_id
                     """);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> data = rows(rs);
            StringBuilder printable = new StringBuilder("Debtor Reminders Report\n\n");
            for (Map<String, Object> row : data) {
                printable.append(row.get("merchant_id"))
                        .append(" | ").append(row.get("name"))
                        .append(" | status=").append(row.get("account_status"))
                        .append(" | overdueDays=").append(row.get("overdue_days"))
                        .append(" | balance=").append(row.get("balance"))
                        .append("\n");
            }
            return report("Debtor Reminders Report", data, printable.toString());
        }
    }

    /**
     * Generates a merchant orders report for a specific merchant and date range.
     * <p>
     * The report includes order totals and payment status, and returns both structured data and a
     * printable text representation.
     *
     * @param query the query parameters containing the merchant ID and required date range
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Generates a detailed activity report for a specific merchant over a date range.
     * <p>
     * The report includes the merchant's orders and each order's items, and returns both structured data
     * and a printable text representation.
     *
     * @param query the query parameters containing the merchant ID and required date range
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Generates a merchant invoices report for a specific merchant and date range.
     * <p>
     * The report lists invoices issued to the merchant and returns both structured data and a printable
     * text representation.
     *
     * @param query the query parameters containing the merchant ID and required date range
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Generates a company-wide invoices report for the requested date range.
     * <p>
     * The report lists all invoices issued during the range and returns both structured data and a
     * printable text representation.
     *
     * @param query the query parameters containing the required date range
     * @return a map containing the report title, generated timestamp, report data, and printable text
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Creates a new non-commercial application request.
     * <p>
     * The application is stored with a pending status and the current timestamp, and the generated
     * application ID is returned to the caller.
     *
     * @param body a map containing the applicant email
     * @return a map containing a success message and the generated application ID
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> createNonCommercialApplication(Map<String, Object> body) throws SQLException {
        long applicationId;
        try (Connection connection = connect();
             // creates sql statement and adds relevant data
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO non_commercial_applications (
                         email, member_type, account_no, company_name, company_address, company_registration, status, created_at
                     )
                     VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, JsonUtil.requireString(body, "email"));
            ps.setString(2, JsonUtil.optionalString(body, "memberType", "NON_COMMERCIAL"));
            ps.setString(3, JsonUtil.optionalString(body, "accountNo"));
            ps.setString(4, JsonUtil.optionalString(body, "companyName"));
            ps.setString(5, JsonUtil.optionalString(body, "companyAddress"));
            ps.setString(6, JsonUtil.optionalString(body, "companyRegistration"));
            ps.setString(7, now());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                applicationId = keys.getLong(1);
            }
        }
        return Map.of("message", "Application received", "applicationId", applicationId);
    }

    /**
     * Retrieves all non-commercial applications ordered by most recent first.
     * <p>
     * The returned list contains one map per application, with the selected fields converted into a
     * JSON-friendly structure.
     *
     * @return a list of non-commercial application records
     * @throws SQLException if a database access error occurs
     */
    List<Map<String, Object>> listApplications() throws SQLException {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM non_commercial_applications ORDER BY application_id DESC")) {
            return rows(rs);
        }
    }

    /**
     * Processes a non-commercial application decision and logs the outcome by email.
     * <p>
     * The application is marked as approved or rejected based on the supplied decision. When approved,
     * a temporary password is generated and stored; when rejected, only the status and outcome message
     * are updated. A notification email is logged for the applicant in either case.
     *
     * @param applicationId the application identifier to process
     * @param body a map containing the approval decision
     * @return a map containing a success message, local email logging flag, and PU mail relay result
     * @throws SQLException if a database access error occurs
     */
    Map<String, Object> decideApplication(long applicationId, Map<String, Object> body) throws SQLException {
        boolean approved = JsonUtil.requireBoolean(body, "approved");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                String email;
                // creates sql statement to retrieve email
                try (PreparedStatement find = connection.prepareStatement("SELECT email FROM non_commercial_applications WHERE application_id = ?")) {
                    find.setLong(1, applicationId);
                    try (ResultSet rs = find.executeQuery()) {
                        if (!rs.next()) {
                            // throws error if application not found
                            throw new ApiException(404, "Application not found");
                        }
                        email = rs.getString(1);
                    }
                }
                // creates temporary password
                String password = approved ? "PU!" + applicationId + "Ab9$" : null;
                String message = approved
                        ? "Approved. Temporary password: " + password
                        : "Rejected. Please contact InfoPharma support if you need clarification.";

                // creates sql statement to store outcomes and status
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE non_commercial_applications
                        SET status = ?, generated_password = ?, outcome_message = ?, processed_at = ?, decided_at = ?, notes = COALESCE(?, notes)
                        WHERE application_id = ?
                        """)) {
                    update.setString(1, approved ? "APPROVED" : "REJECTED");
                    update.setString(2, password);
                    update.setString(3, message);
                    update.setString(4, now());
                    update.setString(5, now());
                    setNullable(update, 6, body.get("notes"));
                    update.setLong(7, applicationId);
                    update.executeUpdate();
                }
                String subject = approved ? "IPOS-PU membership approved" : "IPOS-PU membership rejected";
                // logs email
                logEmail(connection, email, subject, message);
                connection.commit();
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("message", "Application processed");
                response.put("emailLogged", true);
                response.put("puMail", integrationClient.sendPuMail(
                        "ipos-sa@londonsoftwarehouse.local",
                        List.of(email),
                        subject,
                        message
                ));
                return response;
            } catch (SQLException ex) {
                // if error caught roll back sql and throw exception
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Seeds the database with default users, merchant records, and products when the tables are empty.
     * <p>
     * This method is intended to run during application startup so the system has a basic working dataset
     * available for login, browsing, and testing.
     *
     * @throws SQLException if a database access error occurs while seeding data
     */
    private void seed() throws SQLException {
        try (Connection connection = connect()) {
            // seeds database if database empty
            if (count(connection, "users") == 0) {
                insertUser(connection, "Sysdba", "sysdba@infopharma.local", "London_weighting", "ADMINISTRATOR", null);
                insertUser(connection, "manager", "ops.director@infopharma.local", "Get_it_done", "MANAGER", null);
                insertUser(connection, "accountant", "accountant@infopharma.local", "Count_money", "ACCOUNTING_STAFF", null);
                insertUser(connection, "clerk", "clerk@infopharma.local", "Paperwork", "ACCOUNTING_STAFF", null);
                insertUser(connection, "warehouse1", "warehouse1@infopharma.local", "Get_a_beer", "OPERATIONS_STAFF", null);
                insertUser(connection, "warehouse2", "warehouse2@infopharma.local", "Lot_smell", "OPERATIONS_STAFF", null);
                insertUser(connection, "delivery", "delivery@infopharma.local", "Too_dark", "OPERATIONS_STAFF", null);
                insertUser(connection, "city", "citypharmacy@example.com", "northampton", "MERCHANT", "ACC0001");
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
                seedApplication(connection, "cool@example.com", "NON_COMMERCIAL", "PU0001", null, null, null,
                        "APPROVED", "2026-02-25T10:00:00", "2026-02-26T09:00:00", "Imported from IPOS-PU sample data");
                seedApplication(connection, "cool1@example.com", "NON_COMMERCIAL", "PU0002", null, null, null,
                        "PENDING", "2026-02-25T10:05:00", null, "Imported from IPOS-PU sample data");
                seedApplication(connection, "pondpharma@example.com", "COMMERCIAL", "PU0003", "Pond Pharmacy",
                        "Chislehurst, 25 High Street, BR7 5BN", "UK10003429CompH",
                        "PENDING", "2026-02-25T10:10:00", null, "Commercial member application imported from IPOS-PU sample data");
            }
            backfillApplicationMetadata(connection);
        }
    }

    /**
     * Evaluates the current account status of a merchant based on overdue invoices.
     * <p>
     * The merchant's invoices are inspected to determine the maximum number of overdue days, and the
     * account status is updated accordingly. Any resulting warnings are returned with the evaluation.
     *
     * @param merchantId the merchant identifier to evaluate
     * @return a map containing the merchant ID, computed account status, warnings, and maximum overdue days
     * @throws SQLException if a database access error occurs
     */
    private Map<String, Object> evaluateMerchantAccount(Connection connection, String merchantId) throws SQLException {
        Map<String, Object> merchant = getMerchantById(connection, merchantId);
        LocalDate today = LocalDate.now();
        long maxOverdueDays = 0;

        // creates sql statement to merchants with unpaid invoices
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT due_date FROM invoices WHERE merchant_id = ? AND status != 'PAID'
                """)) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // checks if merchant is overdue
                    LocalDate dueDate = LocalDate.parse(rs.getString(1));
                    if (dueDate.isBefore(today)) {
                        maxOverdueDays = Math.max(maxOverdueDays, ChronoUnit.DAYS.between(dueDate, today));
                    }
                }
            }
        }

        // updates status based on how long the payment is overdue
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

    private MerchantDebtStatus merchantDebtStatus(Connection connection, String merchantId) throws SQLException {
        double outstandingBalance = 0;
        boolean hasOverdueInvoices = false;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT due_date, total_amount, paid_amount, status
                FROM invoices
                WHERE merchant_id = ?
                """)) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                LocalDate today = LocalDate.now();
                while (rs.next()) {
                    double totalAmount = rs.getDouble("total_amount");
                    double paidAmount = rs.getDouble("paid_amount");
                    double remaining = Math.max(0, totalAmount - paidAmount);
                    outstandingBalance += remaining;
                    if (remaining > 0 && !"PAID".equalsIgnoreCase(rs.getString("status"))) {
                        LocalDate dueDate = LocalDate.parse(rs.getString("due_date"));
                        if (dueDate.isBefore(today)) {
                            hasOverdueInvoices = true;
                        }
                    }
                }
            }
        }
        return new MerchantDebtStatus(outstandingBalance, hasOverdueInvoices);
    }

    /**
     * Generates or retrieves the invoice for a given order using an existing database connection.
     * <p>
     * If an invoice already exists for the order, it is returned as-is; otherwise, a new invoice is created
     * from the order's merchant and total amount, then returned from the database.
     *
     * @param connection the active database connection to use
     * @param orderId the order identifier for which to generate an invoice
     * @return a map containing the invoice details
     * @throws SQLException if a database access error occurs
     */
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
        String orderDateStr = Objects.toString(order.get("order_date"), now());
        LocalDate orderDate;
        try {
            orderDate = LocalDate.parse(orderDateStr.substring(0, 10));
        } catch (Exception e) {
            orderDate = LocalDate.now();
        }

        // Due date is end of the calendar month of the order date
        LocalDate dueDate = orderDate.withDayOfMonth(orderDate.lengthOfMonth());

        long invoiceId;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO invoices (order_id, merchant_id, issue_date, due_date, total_amount, paid_amount, status) " +
                        "VALUES (?, ?, ?, ?, ?, 0, 'ISSUED')",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, orderId);
            insert.setString(2, Objects.toString(order.get("merchant_id")));
            insert.setString(3, orderDate.toString());
            insert.setString(4, dueDate.toString());
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

    /**
     * Applies a payment amount across a merchant's outstanding invoices in due-date order.
     * <p>
     * Each invoice is partially or fully paid until the payment amount is exhausted, and invoice status
     * is updated to reflect the remaining balance.
     *
     * @param connection the active database connection to use
     * @param merchantId the merchant identifier whose invoices should receive the payment
     * @param amount the payment amount to apply
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Recalculates and updates a merchant's outstanding balance.
     * <p>
     * The balance is derived from all unpaid invoices for the merchant and written back to the merchants
     * table together with an updated timestamp.
     *
     * @param connection the active database connection to use
     * @param merchantId the merchant identifier whose balance should be updated
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Calculates the discount amount for a merchant based on the configured discount plan.
     * <p>
     * If the merchant uses a fixed discount plan, the returned value includes both any pending discount
     * credit and the percentage-based discount on the provided subtotal. Otherwise, only pending discount
     * credit is applied.
     *
     * @param merchant the merchant data containing discount configuration
     * @param subtotal the order subtotal to use when calculating the discount
     * @return the calculated discount amount
     */
    /**
     * Calculates the discount amount for a merchant based on the configured discount plan.
     *
     * @param merchant the merchant data containing discount configuration
     * @param subtotal the order subtotal to use when calculating the discount
     * @return the calculated discount amount
     */
    private double calculateDiscount(Map<String, Object> merchant, double subtotal) {
        double pendingCredit = ((Number) merchant.get("pending_discount_credit")).doubleValue();
        String discountType = Objects.toString(merchant.get("discount_type"), null);

        if ("FIXED".equalsIgnoreCase(discountType)) {
            double fixedRate = ((Number) merchant.get("fixed_discount_rate")).doubleValue();
            // Fixed discount: apply percentage discount PLUS any pending credit
            return pendingCredit + (subtotal * (fixedRate / 100.0));
        } else if ("FLEXIBLE".equalsIgnoreCase(discountType)) {
            // Flexible: only pending credit at order time (monthly discount calculated separately)
            return pendingCredit;
        }

        // No discount plan - just return pending credit if any
        return pendingCredit;
    }

    /**
     * Calculates the flexible discount for a merchant based on their monthly order total.
     * Called at the end of each calendar month to process pending discounts.
     *
     * @param connection the active database connection
     * @param merchantId the merchant to process
     * @return the discount amount calculated
     * @throws SQLException if database error occurs
     */
    private double calculateFlexibleDiscount(Connection connection, String merchantId) throws SQLException {
        // Get merchant's flexible rates
        Map<String, Object> merchant = getMerchantById(connection, merchantId);
        double tier1 = ((Number) merchant.get("flexible_rate_tier1")).doubleValue();
        double tier2 = ((Number) merchant.get("flexible_rate_tier2")).doubleValue();
        double tier3 = ((Number) merchant.get("flexible_rate_tier3")).doubleValue();

        // Calculate total monthly orders (last 30 days from today)
        double monthlyTotal = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE merchant_id = ? AND order_date >= date('now', '-30 days')")) {
            ps.setString(1, merchantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    monthlyTotal = rs.getDouble(1);
                }
            }
        }

        // Apply tiered discount
        double discountRate;
        if (monthlyTotal < 1000) {
            discountRate = tier1;
        } else if (monthlyTotal < 2000) {
            discountRate = tier2;
        } else {
            discountRate = tier3;
        }

        return monthlyTotal * (discountRate / 100.0);
    }

    /**
     * Processes flexible discounts for all merchants at month end.
     * Called by a scheduled job or manually by admin.
     */
    Map<String, Object> processMonthlyFlexibleDiscounts() throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            List<Map<String, Object>> results = new ArrayList<>();

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT merchant_id FROM merchants WHERE discount_type = 'FLEXIBLE'")) {

                while (rs.next()) {
                    String merchantId = rs.getString(1);
                    double discountAmount = calculateFlexibleDiscount(connection, merchantId);

                    if (discountAmount > 0) {
                        // Add to pending discount credit
                        try (PreparedStatement update = connection.prepareStatement(
                                "UPDATE merchants SET pending_discount_credit = pending_discount_credit + ?, updated_at = ? WHERE merchant_id = ?")) {
                            update.setDouble(1, discountAmount);
                            update.setString(2, now());
                            update.setString(3, merchantId);
                            update.executeUpdate();
                        }

                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("merchantId", merchantId);
                        result.put("discountAmount", discountAmount);
                        result.put("status", "PROCESSED");
                        results.add(result);

                        // Log the discount for audit
                        logEmail(connection, getMerchantById(connection, merchantId).get("email").toString(),
                                "Monthly Discount Applied",
                                "Your flexible discount of £" + String.format("%.2f", discountAmount) +
                                        " has been applied as credit to your next order.");
                    }
                }
            }

            connection.commit();
            return Map.of("message", "Monthly discounts processed", "results", results);
        }
    }

    /**
     * Inserts a stock movement record for a product.
     * <p>
     * The movement captures the product, movement type, quantity, timestamp, and any optional reference
     * information.
     *
     * @param connection the active database connection to use
     * @param productId the product identifier associated with the movement
     * @param type the movement type to record
     * @param quantity the quantity moved
     * @param referenceType the optional reference type for the movement
     * @param referenceId the optional reference identifier for the movement
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Logs an email that would be sent by the system.
     * <p>
     * The message is stored with a simulated SMTP delivery mode and the current timestamp.
     *
     * @param connection the active database connection to use
     * @param recipient the email recipient address
     * @param subject the email subject line
     * @param body the email body content
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Builds a printable text version of the turnover report.
     * <p>
     * The output includes the report period followed by a line for each product showing the product ID,
     * name, quantity sold, and revenue.
     *
     * @param range the date range covered by the report
     * @param rows the report rows to include in the printable output
     * @return a formatted printable turnover report
     */
    private String printableTurnover(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Turnover Report\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | ").append(row.get("name")).append(" | qty=").append(row.get("quantity_sold")).append(" | revenue=").append(row.get("revenue")).append("\n");
        }
        return builder.toString();
    }

    /**
     * Builds a printable text version of the stock turnover report.
     * <p>
     * The output includes the report period followed by a line for each product showing the product ID,
     * sold quantity, and received quantity.
     *
     * @param range the date range covered by the report
     * @param rows the report rows to include in the printable output
     * @return a formatted printable stock turnover report
     */
    private String printableStockTurnover(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Stock Turnover Report\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | sold=").append(row.get("sold_quantity")).append(" | received=").append(row.get("received_quantity")).append("\n");
        }
        return builder.toString();
    }

    /**
     * Builds a printable text version of the low stock report.
     * <p>
     * The output lists each low stock product with its product ID, name, current stock, minimum stock,
     * and recommended order quantity.
     *
     * @param rows the report rows to include in the printable output
     * @return a formatted printable low stock report
     */
    private String printableLowStock(List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Low Stock Report\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | ").append(row.get("name")).append(" | current=").append(row.get("stock_level")).append(" | min=").append(row.get("minimum_stock_level")).append(" | order=").append(row.get("recommended_min_order")).append("\n");
        }
        return builder.toString();
    }

    /**
     * Builds a printable text version of the merchant orders report.
     * <p>
     * The output includes the merchant and report period followed by one line per order with its order date,
     * total amount, dispatch date, and payment status.
     *
     * @param merchantId the merchant identifier for the report header
     * @param range the date range covered by the report
     * @param rows the report rows to include in the printable output
     * @return a formatted printable merchant orders report
     */
    private String printableMerchantOrders(String merchantId, Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Merchant Orders Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Order ").append(row.get("order_id")).append(" | ordered=").append(row.get("order_date")).append(" | total=").append(row.get("total_amount")).append(" | dispatched=").append(row.get("dispatch_date")).append(" | payment=").append(row.get("payment_status")).append("\n");
        }
        return builder.toString();
    }

    /**
     * Builds a printable text version of the merchant activity report.
     * <p>
     * The output includes the merchant and date range, then lists each order followed by its line items.
     *
     * @param merchantId the merchant identifier for the report header
     * @param range the date range covered by the report
     * @param rows the report rows to include in the printable output
     * @return a formatted printable merchant activity report
     */
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

    /**
     * Builds a printable text version of the merchant invoices report.
     * <p>
     * The output includes the merchant and date range, then lists each invoice with its order, total,
     * paid amount, and status.
     *
     * @param merchantId the merchant identifier for the report header
     * @param range the date range covered by the report
     * @param rows the report rows to include in the printable output
     * @return a formatted printable merchant invoices report
     */
    private String printableMerchantInvoices(String merchantId, Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Merchant Invoices Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Invoice ").append(row.get("invoice_id")).append(" | order=").append(row.get("order_id")).append(" | total=").append(row.get("total_amount")).append(" | paid=").append(row.get("paid_amount")).append(" | status=").append(row.get("status")).append("\n");
        }
        return builder.toString();
    }

    /**
     * Builds a printable text version of the company invoices report.
     * <p>
     * The output includes the report period and lists each invoice with its merchant, total, paid amount,
     * and status.
     *
     * @param range the date range covered by the report
     * @param rows the report rows to include in the printable output
     * @return a formatted printable company invoices report
     */
    private String printableCompanyInvoices(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Company Invoices Report\nPeriod: ").append(range.start).append(" to ").append(range.end).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Invoice ").append(row.get("invoice_id")).append(" | merchant=").append(row.get("merchant_id")).append(" | total=").append(row.get("total_amount")).append(" | paid=").append(row.get("paid_amount")).append(" | status=").append(row.get("status")).append("\n");
        }
        return builder.toString();
    }

    /**
     * Creates a standard report payload.
     * <p>
     * The returned map includes the report title, generation timestamp, underlying data, and printable
     * text representation.
     *
     * @param title the report title
     * @param data the structured report data
     * @param printableText the printable text representation of the report
     * @return a map containing the report metadata and content
     */
    private Map<String, Object> report(String title, Object data, String printableText) {
        return Map.of("title", title, "generatedAt", now(), "data", data, "printableText", printableText);
    }

    /**
     * Retrieves the products that are currently below their minimum stock level.
     * <p>
     * Each row includes the recommended minimum order quantity needed to bring stock back above the
     * threshold.
     *
     * @param connection the active database connection to use
     * @return a list of low stock product records
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Retrieves a merchant record by merchant ID.
     * <p>
     * If no matching merchant exists, an exception is thrown.
     *
     * @param connection the active database connection to use
     * @param merchantId the merchant identifier to look up
     * @return a map containing the merchant details
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Retrieves a product record by product ID.
     * <p>
     * If no matching product exists, an exception is thrown.
     *
     * @param connection the active database connection to use
     * @param productId the product identifier to look up
     * @return a map containing the product details
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Retrieves an order record by order ID.
     * <p>
     * If no matching order exists, an exception is thrown.
     *
     * @param connection the active database connection to use
     * @param orderId the order identifier to look up
     * @return a map containing the order details
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Retrieves the line items for an order.
     * <p>
     * The returned items are ordered by their insertion order in the database.
     *
     * @param connection the active database connection to use
     * @param orderId the order identifier whose items should be retrieved
     * @return a list of order item records
     * @throws SQLException if a database access error occurs
     */
    private List<Map<String, Object>> getOrderItems(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM order_items WHERE order_id = ? ORDER BY order_item_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        }
    }

    /**
     * Builds the compact item list used for SA-to-CA delivery synchronization.
     *
     * @param connection the active database connection to use
     * @param orderId the delivered order identifier
     * @return a list of product ID, name, and quantity maps
     * @throws SQLException if a database access error occurs
     */
    private List<Map<String, Object>> deliverySyncItems(Connection connection, long orderId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : getOrderItems(connection, orderId)) {
            Map<String, Object> product = getProductById(connection, Objects.toString(item.get("product_id")));
            Map<String, Object> syncItem = new LinkedHashMap<>();
            syncItem.put("productId", item.get("product_id"));
            syncItem.put("name", product.get("name"));
            syncItem.put("packageType", product.get("package_type"));
            syncItem.put("units", product.get("unit"));
            syncItem.put("unitsInAPack", product.get("units_in_pack"));
            syncItem.put("bulkCost", product.get("unit_price"));
            syncItem.put("quantity", item.get("quantity"));
            syncItem.put("stockLimit", product.get("minimum_stock_level"));
            items.add(syncItem);
        }
        return items;
    }

    /**
     * Builds a printable text representation of an invoice.
     * <p>
     * The output includes the invoice identifier, each order item with quantity, unit price, and line
     * total, followed by the total amount due.
     *
     * @param connection the active database connection to use
     * @param invoice the invoice data used to build the printable text
     * @return a formatted printable invoice
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Inserts a merchant record used during database seeding.
     *
     * @param connection the active database connection to use
     * @param merchantId the merchant identifier
     * @param name the account holder name
     * @param email the stored merchant email address
     * @param address the merchant address
     * @param phone the merchant phone number
     * @param creditLimit the merchant credit limit
     * @param discountType the configured discount type
     * @param fixedRate the fixed discount rate percentage
     * @param tier1 the flexible plan first tier rate
     * @param tier2 the flexible plan second tier rate
     * @param tier3 the flexible plan third tier rate
     * @param createdAt the creation timestamp
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Inserts a historical seeded order together with its items, invoice, and stock movements.
     *
     * @param connection the active database connection to use
     * @param merchantId the merchant who placed the order
     * @param orderDateTime the order timestamp
     * @param dispatchDateTime the dispatch timestamp
     * @param dispatchedBy the staff user who dispatched the order
     * @param courier the courier used for delivery
     * @param trackingNumber the recorded tracking or reference number
     * @param deliveredDateTime the delivered timestamp
     * @param items the line items to insert
     * @throws SQLException if a database access error occurs
     */
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
            insertStockMovementAt(connection, item.productId(), "SALE", item.quantity(), orderDateTime, "ORDER", Long.toString(orderId));
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

        updateMerchantBalance(connection, merchantId);
    }

    /**
     * Inserts a historical payment and applies it to outstanding invoices.
     *
     * @param connection the active database connection to use
     * @param merchantId the merchant making the payment
     * @param amount the payment amount
     * @param method the payment method
     * @param reference the payment reference
     * @param paymentDate the payment date
     * @param notes the payment notes
     * @throws SQLException if a database access error occurs
     */
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
        applyPaymentToInvoices(connection, merchantId, amount);
        updateMerchantBalance(connection, merchantId);
        evaluateMerchantAccount(connection, merchantId);
    }

    /**
     * Inserts a sample application row used for cross-subsystem demo preparation.
     *
     * @param connection the active database connection to use
     * @param email the application email address
     * @param status the stored application status
     * @param createdAt the creation timestamp
     * @param decisionAt the optional decision timestamp
     * @param notes any notes to attach to the application
     * @throws SQLException if a database access error occurs
     */
    private void seedApplication(Connection connection, String email, String memberType, String accountNo,
                                 String companyName, String companyAddress, String companyRegistration,
                                 String status, String createdAt, String decisionAt, String notes) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO non_commercial_applications (
                    email, member_type, account_no, company_name, company_address, company_registration,
                    status, created_at, decided_at, notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, email);
            ps.setString(2, memberType);
            ps.setString(3, accountNo);
            ps.setString(4, companyName);
            ps.setString(5, companyAddress);
            ps.setString(6, companyRegistration);
            ps.setString(7, status);
            ps.setString(8, createdAt);
            setNullable(ps, 9, decisionAt);
            ps.setString(10, notes);
            ps.executeUpdate();
        }
    }

    /**
     * Backfills product metadata for databases created before the richer Appendix 9.1 fields existed.
     *
     * @param connection the active database connection to use
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Backfills application metadata for sample application rows created before the richer fields existed.
     *
     * @param connection the active database connection to use
     * @throws SQLException if a database access error occurs
     */
    private void backfillApplicationMetadata(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE non_commercial_applications
                SET member_type = COALESCE(member_type, CASE
                        WHEN application_id IN (1, 2) THEN 'NON_COMMERCIAL'
                        WHEN application_id = 3 THEN 'COMMERCIAL'
                        ELSE member_type
                    END),
                    account_no = COALESCE(account_no, CASE
                        WHEN application_id = 1 THEN 'PU0001'
                        WHEN application_id = 2 THEN 'PU0002'
                        WHEN application_id = 3 THEN 'PU0003'
                        ELSE account_no
                    END),
                    company_name = COALESCE(company_name, CASE
                        WHEN application_id = 3 THEN 'Pond Pharmacy'
                        ELSE company_name
                    END),
                    company_address = COALESCE(company_address, CASE
                        WHEN application_id = 3 THEN 'Chislehurst, 25 High Street, BR7 5BN'
                        ELSE company_address
                    END),
                    company_registration = COALESCE(company_registration, CASE
                        WHEN application_id = 3 THEN 'UK10003429CompH'
                        ELSE company_registration
                    END),
                    notes = COALESCE(notes, 'Imported from IPOS-PU sample data')
                """)) {
            ps.executeUpdate();
        }
    }

    /**
     * Inserts a single product record used during database seeding.
     * <p>
     * The product is stored with its identifier, name, pricing, stock levels, and timestamps.
     *
     * @param connection the active database connection to use
     * @param id the product identifier
     * @param name the product name
     * @param price the unit price
     * @param stock the initial stock level
     * @param minimumStock the minimum stock threshold
     * @throws SQLException if a database access error occurs
     */
    private void seedProduct(Connection connection, String id, String name, String packageType, String unit, int unitsInPack,
                             double price, int stock, int minimumStock) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO products (
                    product_id, name, package_type, unit, units_in_pack,
                    unit_price, stock_level, minimum_stock_level, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            String now = now();
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, packageType);
            ps.setString(4, unit);
            ps.setInt(5, unitsInPack);
            ps.setDouble(6, price);
            ps.setInt(7, stock);
            ps.setInt(8, minimumStock);
            ps.setString(9, now);
            ps.setString(10, now);
            ps.executeUpdate();
        }
    }

    /**
     * Inserts a single user record used during database seeding.
     * <p>
     * The user is stored with its credentials, role, optional merchant association, active flag, and
     * creation timestamp.
     *
     * @param connection the active database connection to use
     * @param username the username to insert
     * @param password the password to insert
     * @param role the user role to insert
     * @param merchantId the optional merchant identifier associated with the user
     * @throws SQLException if a database access error occurs
     */
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

    /**
     * Inserts a stock movement record at a specific historical timestamp.
     *
     * @param connection the active database connection to use
     * @param productId the product identifier associated with the movement
     * @param type the movement type to record
     * @param quantity the quantity moved
     * @param happenedAt the historical timestamp to store
     * @param referenceType the optional reference type for the movement
     * @param referenceId the optional reference identifier for the movement
     * @throws SQLException if a database access error occurs
     */
    private void insertStockMovementAt(Connection connection, String productId, String type, int quantity, String happenedAt,
                                       String referenceType, String referenceId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO stock_movements (product_id, movement_type, quantity, happened_at, reference_type, reference_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, productId);
            ps.setString(2, type);
            ps.setInt(3, quantity);
            ps.setString(4, happenedAt);
            ps.setString(5, referenceType);
            ps.setString(6, referenceId);
            ps.executeUpdate();
        }
    }

    /**
     * Counts the number of rows in a database table.
     * <p>
     * The table name is used directly in the SQL query, so it should only be supplied from trusted code.
     *
     * @param connection the active database connection to use
     * @param table the table name to count rows from
     * @return the number of rows in the table
     * @throws SQLException if a database access error occurs
     */
    private int count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Ensures that a table contains a required column, adding it if missing.
     *
     * @param statement the statement to use for schema inspection and migration
     * @param table the table name to inspect
     * @param column the required column name
     * @param definition the SQL column definition to add when missing
     * @throws SQLException if schema inspection or alteration fails
     */
    private void ensureColumn(Statement statement, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    /**
     * Opens a SQLite database connection and enables foreign key enforcement.
     * <p>
     * The returned connection is configured for this application’s schema rules before being handed back
     * to the caller.
     *
     * @return an open database connection
     * @throws SQLException if the connection cannot be established or configured
     */
    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /**
     * Resolves the authenticated user from the session token stored in the request headers.
     * <p>
     * The token must be present in the {@code X-Session-Token} header, must match an active session, and
     * the associated user account must be active.
     *
     * @param connection the active database connection to use
     * @param headers the HTTP headers containing the session token
     * @return the resolved authentication context
     * @throws SQLException if a database access error occurs
     */
    private AuthContext resolveAuth(Connection connection, Headers headers) throws SQLException {
        String sessionToken = headers.getFirst("X-Session-Token");
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ApiException(401, "Missing X-Session-Token header");
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.username, s.role, s.merchant_id, u.active, u.email
                FROM sessions s
                JOIN users u ON u.username = s.username
                WHERE s.session_token = ?
                """)) {
            ps.setString(1, sessionToken);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(401, "Session is invalid or expired");
                }
                if (rs.getInt("active") != 1) {
                    throw new ApiException(403, "Account is inactive");
                }
                return new AuthContext(rs.getString("username"), rs.getString("email"), rs.getString("role"), rs.getString("merchant_id"));
            }
        }
    }

    /**
     * Determines whether an order can transition from its current status to a new status.
     * <p>
     * The allowed flow is PENDING → PROCESSING → DISPATCHED → DELIVERED. A status may also remain
     * unchanged.
     *
     * @param currentStatus the order's current status
     * @param newStatus the desired new status
     * @return {@code true} if the transition is allowed; otherwise {@code false}
     */
    private static boolean isValidOrderTransition(String currentStatus, String newStatus) {
        if (Objects.equals(currentStatus, newStatus)) {
            return true;
        }
        
        // This switch logic was changed and requires clearing up
        return switch (currentStatus) {
            case "PENDING" -> "PROCESSING".equals(newStatus) || "DISPATCHED".equals(newStatus);
            case "PROCESSING" -> "DISPATCHED".equals(newStatus);
            case "DISPATCHED" -> "DELIVERED".equals(newStatus);
            default -> false;
        };
    }

    /**
     * Extracts a required query parameter from a map.
     * <p>
     * The value must be present and non-blank; otherwise an exception is thrown.
     *
     * @param query the query parameter map
     * @param key the parameter name to look up
     * @return the non-blank parameter value
     */
    private static String requireQuery(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "Missing query parameter: " + key);
        }
        return value;
    }

    /**
     * Parses a required date range from query parameters.
     * <p>
     * Both {@code start} and {@code end} must be present and valid ISO-8601 dates.
     *
     * @param query the query parameter map
     * @return the parsed date range
     * @throws ApiException if either date parameter is missing
     */
    private static Range requiredRange(Map<String, String> query) {
        return new Range(LocalDate.parse(requireQuery(query, "start")), LocalDate.parse(requireQuery(query, "end")));
    }

    /**
     * Returns the current local date-time as an ISO-8601 string.
     *
     * @return the current timestamp in ISO-8601 format
     */
    private static String now() {
        return LocalDateTime.now().toString();
    }

    /**
     * Sets a prepared statement parameter, allowing {@code null} values.
     *
     * @param ps the prepared statement to update
     * @param index the 1-based parameter index
     * @param value the value to set, or {@code null}
     * @throws SQLException if the parameter cannot be set
     */
    private static void setNullable(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setObject(index, value);
        }
    }

    /**
     * Reads all remaining rows from a result set and converts them into a list of maps.
     *
     * @param rs the result set to read
     * @return a list containing one map per row
     * @throws SQLException if the result set cannot be read
     */
    private static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            rows.add(row(rs));
        }
        return rows;
    }

    /**
     * Reads all remaining rows from a result set and converts them into a list of maps.
     *
     * @param rs the result set to read
     * @return a map containing one map per row
     * @throws SQLException if the result set cannot be read
     */
    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        int count = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= count; i++) {
            row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), normalizeSqlValue(rs.getObject(i)));
        }
        return row;
    }

    /**
     * Normalizes common SQL date and timestamp values into ISO-8601 strings.
     *
     * @param value the value to normalize
     * @return the normalized value, or the original value if no conversion is needed
     */
    private static Object normalizeSqlValue(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        return value;
    }

    /**
     * Lightweight authenticated-user context resolved from a session token.
     *
     * @param username the authenticated username
     * @param role the authenticated role
     * @param merchantId the merchant linked to the session, when applicable
     */
    record AuthContext(String username, String email, String role, String merchantId) {}

    /**
     * Inclusive date range used for report queries.
     *
     * @param start the range start date
     * @param end the range end date
     */
    private record Range(LocalDate start, LocalDate end) {}

    private record MerchantDebtStatus(double outstandingBalance, boolean hasOverdueInvoices) {}

    /**
     * Represents one line item used while seeding historical orders.
     *
     * @param productId the product identifier
     * @param quantity the ordered quantity
     * @param unitPrice the unit price used in the scenario
     */
    private record OrderSeedLine(String productId, int quantity, double unitPrice) {}

    private static OrderSeedLine line(String productId, int quantity, double unitPrice) {
        return new OrderSeedLine(productId, quantity, unitPrice);
    }

    Map<String, Object> testCaConnection() {
        return integrationClient.testConnection("CA");
    }

    Map<String, Object> testPuConnection() {
        return integrationClient.testConnection("PU");
    }
}
