package ipossa;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ApiHandler implements HttpHandler {
    private final Database db;
    private final Path staticRoot;

    ApiHandler(Database db, Path staticRoot) {
        this.db = db;
        this.staticRoot = staticRoot.normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("OPTIONS".equalsIgnoreCase(method)) {
                writeJson(exchange, 204, Map.of());
                return;
            }
            if ("/health".equals(path)) {
                writeJson(exchange, 200, Map.of("status", "ok", "service", "IPOS-SA"));
                return;
            }
            if (!path.startsWith("/api/")) {
                serveStatic(exchange, path);
                return;
            }
            List<String> parts = splitPath(path.substring(5));
            if (parts.isEmpty()) {
                writeJson(exchange, 200, Map.of("service", "IPOS-SA", "routes", List.of("auth", "users", "merchants", "products", "orders", "invoices", "payments", "reports", "non-commercial-applications")));
                return;
            }
            route(exchange, method, parts);
        } catch (ApiException ex) {
            writeJson(exchange, ex.statusCode, Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            writeJson(exchange, 500, Map.of("error", ex.getMessage() == null ? "Internal server error" : ex.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange, String method, List<String> parts) throws Exception {
        switch (parts.get(0)) {
            case "auth" -> handleAuth(exchange, method, parts);
            case "users" -> handleUsers(exchange, method, parts);
            case "merchants" -> handleMerchants(exchange, method, parts);
            case "products" -> handleProducts(exchange, method, parts);
            case "orders" -> handleOrders(exchange, method, parts);
            case "invoices" -> handleInvoices(exchange, method, parts);
            case "payments" -> handlePayments(exchange, method, parts);
            case "reports" -> handleReports(exchange, method, parts);
            case "non-commercial-applications" -> handleApplications(exchange, method, parts);
            default -> throw new ApiException(404, "Route not found");
        }
    }

    private void handleAuth(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (!"POST".equals(method) || parts.size() != 2 || !"login".equals(parts.get(1))) {
            throw new ApiException(404, "Route not found");
        }
        Map<String, Object> body = body(exchange);
        writeJson(exchange, 200, db.login(JsonUtil.requireString(body, "username"), JsonUtil.requireString(body, "password")));
    }

    private void handleUsers(HttpExchange exchange, String method, List<String> parts) throws Exception {
        requireRole(exchange, "ADMINISTRATOR");
        if (parts.size() == 1 && "GET".equals(method)) {
            writeJson(exchange, 200, Map.of("users", db.listUsers()));
            return;
        }
        if (parts.size() == 1 && "POST".equals(method)) {
            writeJson(exchange, 201, db.createUser(body(exchange)));
            return;
        }
        if (parts.size() == 2 && "PUT".equals(method)) {
            writeJson(exchange, 200, db.updateUser(parts.get(1), body(exchange)));
            return;
        }
        if (parts.size() == 2 && "DELETE".equals(method)) {
            writeJson(exchange, 200, db.deleteUser(parts.get(1)));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    private void handleMerchants(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF", "MERCHANT");
            writeJson(exchange, 200, Map.of("merchants", db.listMerchants(exchange.getRequestHeaders())));
            return;
        }
        if (parts.size() == 1 && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 201, db.createMerchant(body(exchange)));
            return;
        }
        if (parts.size() >= 2) {
            String merchantId = parts.get(1);
            if (parts.size() == 2 && "GET".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF", "MERCHANT");
                writeJson(exchange, 200, db.getMerchant(exchange.getRequestHeaders(), merchantId));
                return;
            }
            if (parts.size() == 2 && "PUT".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR", "MANAGER");
                writeJson(exchange, 200, db.updateMerchant(merchantId, body(exchange)));
                return;
            }
            if (parts.size() == 2 && "DELETE".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR");
                writeJson(exchange, 200, db.deleteMerchant(merchantId));
                return;
            }
            if (parts.size() == 3 && "balance".equals(parts.get(2)) && "GET".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "MERCHANT");
                writeJson(exchange, 200, db.getMerchantBalance(exchange.getRequestHeaders(), merchantId));
                return;
            }
            if (parts.size() == 3 && "evaluate".equals(parts.get(2)) && "POST".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF");
                writeJson(exchange, 200, db.evaluateMerchantAccount(merchantId));
                return;
            }
            if (parts.size() == 3 && "restore".equals(parts.get(2)) && "POST".equals(method)) {
                requireRole(exchange, "MANAGER");
                writeJson(exchange, 200, db.restoreMerchant(merchantId, body(exchange)));
                return;
            }
            if (parts.size() == 3 && "discount-plan".equals(parts.get(2)) && "PUT".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR", "MANAGER");
                writeJson(exchange, 200, db.updateDiscountPlan(merchantId, body(exchange)));
                return;
            }
            if (parts.size() == 3 && "discount-plan".equals(parts.get(2)) && "DELETE".equals(method)) {
                requireRole(exchange, "ADMINISTRATOR", "MANAGER");
                writeJson(exchange, 200, db.deleteDiscountPlan(merchantId));
                return;
            }
        }
        throw new ApiException(404, "Route not found");
    }

    private void handleProducts(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            writeJson(exchange, 200, Map.of("products", db.listProducts()));
            return;
        }
        if (parts.size() == 1 && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 201, db.createProduct(body(exchange)));
            return;
        }
        if (parts.size() == 2 && "search".equals(parts.get(1)) && "GET".equals(method)) {
            writeJson(exchange, 200, Map.of("products", db.searchProducts(query(exchange).getOrDefault("q", ""))));
            return;
        }
        if (parts.size() == 2 && "low-stock".equals(parts.get(1)) && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER");
            writeJson(exchange, 200, db.lowStockReport());
            return;
        }
        if (parts.size() == 2 && "GET".equals(method)) {
            writeJson(exchange, 200, db.getProduct(parts.get(1)));
            return;
        }
        if (parts.size() == 2 && "PUT".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 200, db.updateProduct(parts.get(1), body(exchange)));
            return;
        }
        if (parts.size() == 2 && "DELETE".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 200, db.deleteProduct(parts.get(1)));
            return;
        }
        if (parts.size() == 3 && "stock".equals(parts.get(2)) && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 200, db.addStock(parts.get(1), body(exchange)));
            return;
        }
        if (parts.size() == 3 && "minimum-stock".equals(parts.get(2)) && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 200, db.updateMinimumStock(parts.get(1), body(exchange)));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    private void handleOrders(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF", "MERCHANT");
            writeJson(exchange, 200, Map.of("orders", db.listOrders(exchange.getRequestHeaders(), query(exchange))));
            return;
        }
        if (parts.size() == 1 && "POST".equals(method)) {
            requireRole(exchange, "MERCHANT");
            writeJson(exchange, 201, db.createOrder(exchange.getRequestHeaders(), body(exchange)));
            return;
        }
        if (parts.size() == 2 && "pending".equals(parts.get(1)) && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "OPERATIONS_STAFF");
            writeJson(exchange, 200, Map.of("orders", db.listPendingOrders()));
            return;
        }
        if (parts.size() == 2 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF", "MERCHANT");
            writeJson(exchange, 200, db.getOrder(exchange.getRequestHeaders(), Long.parseLong(parts.get(1))));
            return;
        }
        if (parts.size() == 3 && "status".equals(parts.get(2)) && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "OPERATIONS_STAFF");
            writeJson(exchange, 200, db.updateOrderStatus(Long.parseLong(parts.get(1)), body(exchange)));
            return;
        }
        if (parts.size() == 3 && "invoice".equals(parts.get(2)) && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "ACCOUNTING_STAFF", "OPERATIONS_STAFF");
            writeJson(exchange, 200, db.generateInvoice(Long.parseLong(parts.get(1))));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    private void handleInvoices(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "MERCHANT");
            writeJson(exchange, 200, Map.of("invoices", db.listInvoices(exchange.getRequestHeaders(), query(exchange))));
            return;
        }
        if (parts.size() == 2 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "MERCHANT");
            writeJson(exchange, 200, db.getInvoice(exchange.getRequestHeaders(), Long.parseLong(parts.get(1))));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    private void handlePayments(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF");
            writeJson(exchange, 200, Map.of("payments", db.listPayments(query(exchange))));
            return;
        }
        if (parts.size() == 1 && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "ACCOUNTING_STAFF");
            writeJson(exchange, 201, db.recordPayment(body(exchange)));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    private void handleReports(HttpExchange exchange, String method, List<String> parts) throws Exception {
        requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF");
        if (!"GET".equals(method) || parts.size() != 2) {
            throw new ApiException(404, "Route not found");
        }
        Map<String, String> query = query(exchange);
        Map<String, Object> result = switch (parts.get(1)) {
            case "turnover" -> db.turnoverReport(query);
            case "stock-turnover" -> db.stockTurnoverReport(query);
            case "low-stock" -> db.lowStockReport();
            case "merchant-orders" -> db.merchantOrdersReport(query);
            case "merchant-activity" -> db.merchantActivityReport(query);
            case "merchant-invoices" -> db.merchantInvoicesReport(query);
            case "company-invoices" -> db.companyInvoicesReport(query);
            default -> throw new ApiException(404, "Route not found");
        };
        writeJson(exchange, 200, result);
    }

    private void handleApplications(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "POST".equals(method)) {
            writeJson(exchange, 201, db.createNonCommercialApplication(body(exchange)));
            return;
        }
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER");
            writeJson(exchange, 200, Map.of("applications", db.listApplications()));
            return;
        }
        if (parts.size() == 3 && "decision".equals(parts.get(2)) && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER");
            writeJson(exchange, 200, db.decideApplication(Long.parseLong(parts.get(1)), body(exchange)));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    private Map<String, Object> body(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return JsonUtil.asObject(JsonUtil.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new LinkedHashMap<>();
        URI uri = exchange.getRequestURI();
        if (uri.getRawQuery() == null) {
            return params;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] split = pair.split("=", 2);
            params.put(URLDecoder.decode(split[0], StandardCharsets.UTF_8), split.length > 1 ? URLDecoder.decode(split[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }

    private static void requireRole(HttpExchange exchange, String... allowedRoles) {
        String role = exchange.getRequestHeaders().getFirst("X-Role");
        String normalized = role == null ? null : role.trim().toUpperCase(Locale.ROOT);
        for (String allowed : allowedRoles) {
            if (allowed.equals(normalized)) {
                return;
            }
        }
        throw new ApiException(403, "Operation requires one of roles: " + String.join(", ", allowedRoles));
    }

    private static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] bytes = JsonUtil.stringify(payload).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Role, X-Merchant-Id");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String requested = path.equals("/") ? "/login.html" : path;
        Path resolved = staticRoot.resolve("." + requested).normalize();
        if (!resolved.startsWith(staticRoot) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
            throw new ApiException(404, "Route not found");
        }
        byte[] bytes = Files.readAllBytes(resolved);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType(resolved));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
