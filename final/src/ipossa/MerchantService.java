package ipossa;

import static ipossa.DatabaseSupport.*;

import com.sun.net.httpserver.Headers;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Merchant account management: CRUD, discount plans, balance tracking, and account status evaluation.
 */
final class MerchantService {

    private final Path dbPath;

    MerchantService(Path dbPath) {
        this.dbPath = dbPath;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }


    /**
     * Retrieves merchants visible to the current user, optionally filtered by a search query.
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
     */
    Map<String, Object> evaluateMerchantAccount(String merchantId) throws SQLException {
        try (Connection connection = connect()) {
            return evaluateMerchantAccount(connection, merchantId);
        }
    }

    /**
     * Evaluates and updates account statuses for every merchant in the database.
     */
    void runAccountStatusSweep() {
        try (Connection connection = connect();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT merchant_id FROM merchants")) {
            List<String> ids = new ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getString(1));
            }
            for (String id : ids) {
                try {
                    evaluateMerchantAccount(connection, id);
                } catch (Exception e) {
                    System.err.println("Account sweep failed for merchant " + id + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Account status sweep error: " + e.getMessage());
        }
    }

    /**
     * Processes flexible discounts for all merchants at month end.
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


    Map<String, Object> getMerchantById(Connection connection, String merchantId) throws SQLException {
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

    Map<String, Object> evaluateMerchantAccount(Connection connection, String merchantId) throws SQLException {
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

    void updateMerchantBalance(Connection connection, String merchantId) throws SQLException {
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

    MerchantDebtStatus merchantDebtStatus(Connection connection, String merchantId) throws SQLException {
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
     * Calculates the discount amount for a merchant based on the configured discount plan.
     */
    @SuppressWarnings("unused")
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
}
