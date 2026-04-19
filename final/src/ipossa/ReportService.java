package ipossa;

import static ipossa.DatabaseSupport.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Report generation: turnover, stock turnover, low stock, debtor reminders,
 * merchant orders, merchant activity, merchant invoices, company invoices.
 */
final class ReportService {

    private final Path dbPath;

    ReportService(Path dbPath) {
        this.dbPath = dbPath;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }

    /**
     * Generates a turnover report for products sold within the requested date range.
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
            ps.setString(1, range.start().toString());
            ps.setString(2, range.end().plusDays(1).toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Turnover Report", data, printableTurnover(range, data));
            }
        }
    }

    /**
     * Generates a stock turnover report for the requested date range.
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
            ps.setString(1, range.start().toString());
            ps.setString(2, range.end().plusDays(1).toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Stock Turnover Report", data, printableStockTurnover(range, data));
            }
        }
    }

    /**
     * Generates a low stock report for products currently below their minimum stock level.
     */
    Map<String, Object> lowStockReport() throws SQLException {
        try (Connection connection = connect()) {
            List<Map<String, Object>> data = lowStockRows(connection);
            return report("Low Stock Report", data, printableLowStock(data));
        }
    }

    /**
     * Generates a debtor reminders report for merchants with overdue invoices.
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
            ps.setString(2, range.start().toString());
            ps.setString(3, range.end().plusDays(1).toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Merchant Orders Report", data, printableMerchantOrders(merchantId, range, data));
            }
        }
    }

    /**
     * Generates a detailed activity report for a specific merchant over a date range.
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
                ps.setString(2, range.start().toString());
                ps.setString(3, range.end().plusDays(1).toString());
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
            ps.setString(2, range.start().toString());
            ps.setString(3, range.end().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Merchant Invoices Report", data, printableMerchantInvoices(merchantId, range, data));
            }
        }
    }

    /**
     * Generates a company-wide invoices report for the requested date range.
     */
    Map<String, Object> companyInvoicesReport(Map<String, String> query) throws SQLException {
        Range range = requiredRange(query);
        try (Connection connection = connect();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT * FROM invoices
                     WHERE issue_date >= ? AND issue_date <= ?
                     ORDER BY invoice_id
                     """)) {
            ps.setString(1, range.start().toString());
            ps.setString(2, range.end().toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> data = rows(rs);
                return report("Company Invoices Report", data, printableCompanyInvoices(range, data));
            }
        }
    }


    /** Duplicated trivial read-only query to avoid service dependency. */
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

    /** Duplicated trivial read-only query to avoid service dependency. */
    private List<Map<String, Object>> getOrderItems(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM order_items WHERE order_id = ? ORDER BY order_item_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        }
    }

    private Map<String, Object> report(String title, Object data, String printableText) {
        return Map.of("title", title, "generatedAt", now(), "data", data, "printableText", printableText);
    }

    private String printableTurnover(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Turnover Report\nPeriod: ").append(range.start()).append(" to ").append(range.end()).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append(row.get("product_id")).append(" | ").append(row.get("name")).append(" | qty=").append(row.get("quantity_sold")).append(" | revenue=").append(row.get("revenue")).append("\n");
        }
        return builder.toString();
    }

    private String printableStockTurnover(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Stock Turnover Report\nPeriod: ").append(range.start()).append(" to ").append(range.end()).append("\n\n");
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
        StringBuilder builder = new StringBuilder("Merchant Orders Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start()).append(" to ").append(range.end()).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Order ").append(row.get("order_id")).append(" | ordered=").append(row.get("order_date")).append(" | total=").append(row.get("total_amount")).append(" | dispatched=").append(row.get("dispatch_date")).append(" | payment=").append(row.get("payment_status")).append("\n");
        }
        return builder.toString();
    }

    private String printableMerchantActivity(String merchantId, Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Merchant Activity Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start()).append(" to ").append(range.end()).append("\n\n");
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
        StringBuilder builder = new StringBuilder("Merchant Invoices Report\nMerchant: ").append(merchantId).append("\nPeriod: ").append(range.start()).append(" to ").append(range.end()).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Invoice ").append(row.get("invoice_id")).append(" | order=").append(row.get("order_id")).append(" | total=").append(row.get("total_amount")).append(" | paid=").append(row.get("paid_amount")).append(" | status=").append(row.get("status")).append("\n");
        }
        return builder.toString();
    }

    private String printableCompanyInvoices(Range range, List<Map<String, Object>> rows) {
        StringBuilder builder = new StringBuilder("Company Invoices Report\nPeriod: ").append(range.start()).append(" to ").append(range.end()).append("\n\n");
        for (Map<String, Object> row : rows) {
            builder.append("Invoice ").append(row.get("invoice_id")).append(" | merchant=").append(row.get("merchant_id")).append(" | total=").append(row.get("total_amount")).append(" | paid=").append(row.get("paid_amount")).append(" | status=").append(row.get("status")).append("\n");
        }
        return builder.toString();
    }
}
