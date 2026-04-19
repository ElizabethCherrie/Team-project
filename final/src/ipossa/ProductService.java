package ipossa;

import static ipossa.DatabaseSupport.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * Product catalogue management: CRUD, stock levels, stock movements, and low-stock reporting.
 */
final class ProductService {

    private final Path dbPath;

    ProductService(Path dbPath) {
        this.dbPath = dbPath;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }


    /**
     * Retrieves all products from the database ordered by product ID.
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
     */
    Map<String, Object> getProduct(String productId) throws SQLException {
        try (Connection connection = connect()) {
            // this exists to overload prod
            return getProductById(connection, productId);
        }
    }

    /**
     * Creates a new product record in the database.
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


    Map<String, Object> getProductById(Connection connection, String productId) throws SQLException {
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

    List<Map<String, Object>> lowStockRows(Connection connection) throws SQLException {
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

    void insertStockMovement(Connection connection, String productId, String type, int quantity, String referenceType, String referenceId) throws SQLException {
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

    void insertStockMovementAt(Connection connection, String productId, String type, int quantity, String happenedAt,
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
}
