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
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Order management: CRUD, status transitions, invoice generation and retrieval.
 */
final class OrderService {

    private final Path dbPath;
    private final MerchantService merchantService;
    private final ProductService productService;
    private final IntegrationClient integrationClient;

    OrderService(Path dbPath, MerchantService merchantService, ProductService productService, IntegrationClient integrationClient) {
        this.dbPath = dbPath;
        this.merchantService = merchantService;
        this.productService = productService;
        this.integrationClient = integrationClient;
    }

    private Connection connect() throws SQLException {
        return DatabaseSupport.connect(dbPath);
    }

    /**
     * Creates a new order for the authenticated merchant and generates an invoice for it.
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
                Map<String, Object> merchant = merchantService.getMerchantById(connection, merchantId);

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
                    Map<String, Object> product = productService.getProductById(connection, productId);
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
                    productService.insertStockMovement(connection, Objects.toString(item.get("productId")), "SALE",
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
     */
    Map<String, Object> updateOrderStatus(long orderId, Map<String, Object> body) throws SQLException {
        String newStatus = JsonUtil.requireUpper(body, "status");
        if (!List.of("PENDING", "ACCEPTED", "PROCESSING", "DISPATCHED", "DELIVERED").contains(newStatus)) {
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
            LocalDate expectedDate;
            try {
                expectedDate = LocalDate.parse(expectedDelivery);
            } catch (DateTimeParseException e) {
                throw new ApiException(400, "Invalid expected delivery date format. Use YYYY-MM-DD");
            }
            if (expectedDate.isBefore(LocalDate.now())) {
                throw new ApiException(400, "Expected delivery date must be today or in the future");
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
     */
    Map<String, Object> generateInvoice(long orderId) throws SQLException {
        try (Connection connection = connect()) {
            return generateInvoice(connection, orderId);
        }
    }

    /**
     * Retrieves invoices visible to the current user, optionally filtered by merchant ID and issue date range.
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


    Map<String, Object> generateInvoice(Connection connection, long orderId) throws SQLException {
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

    Map<String, Object> getOrderById(Connection connection, long orderId) throws SQLException {
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

    List<Map<String, Object>> getOrderItems(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM order_items WHERE order_id = ? ORDER BY order_item_id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        }
    }


    private List<Map<String, Object>> deliverySyncItems(Connection connection, long orderId) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> item : getOrderItems(connection, orderId)) {
            Map<String, Object> product = productService.getProductById(connection, Objects.toString(item.get("product_id")));
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

    private static boolean isValidOrderTransition(String currentStatus, String newStatus) {
        if (Objects.equals(currentStatus, newStatus)) {
            return true;
        }
        
        // This switch logic was changed and requires clearing up
        return switch (currentStatus) {
            case "PENDING" -> "ACCEPTED".equals(newStatus) || "PROCESSING".equals(newStatus) || "DISPATCHED".equals(newStatus);
            case "ACCEPTED" -> "PROCESSING".equals(newStatus) || "DISPATCHED".equals(newStatus);
            case "PROCESSING" -> "DISPATCHED".equals(newStatus);
            case "DISPATCHED" -> "DELIVERED".equals(newStatus);
            default -> false;
        };
    }
}
