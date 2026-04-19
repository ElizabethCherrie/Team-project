package ipossa;

import static ipossa.DatabaseSupport.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Payment recording and invoice settlement.
 */
final class PaymentService {

    private final Path dbPath;
    private final MerchantService merchantService;

    PaymentService(Path dbPath, MerchantService merchantService) {
        this.dbPath = dbPath;
        this.merchantService = merchantService;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }

    
    /**
     * Retrieves payments from the database, optionally filtered by merchant ID.
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
                merchantService.updateMerchantBalance(connection, merchantId);
                Map<String, Object> evaluation = merchantService.evaluateMerchantAccount(connection, merchantId);
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

    void applyPaymentToInvoices(Connection connection, String merchantId, double amount) throws SQLException {
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
}
