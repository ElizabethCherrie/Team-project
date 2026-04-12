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

/**
 * Central HTTP handler for the IPOS-SA prototype.
 *
 * <p>This class routes incoming requests to subsystem operations, serves the
 * static frontend, validates authorization, and serializes JSON responses for
 * the demo website and any integrating subsystems.</p>
 */
final class ApiHandler implements HttpHandler {
    private final Database db;
    private final Path staticRoot;

    /**
     * Creates the API handler with its backing database and static file root.
     *
     * @param db the database service used by the routes
     * @param staticRoot the root folder for static frontend assets
     */
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
                // Reply to CORS preflight requests without processing a route.
                writeJson(exchange, 204, Map.of());
                return;
            }
            if ("/health".equals(path)) {
                // Expose a lightweight liveness check for monitoring.
                writeJson(exchange, 200, Map.of("status", "ok", "service", "IPOS-SA"));
                return;
            }
            if (!"/api".equals(path) && !path.startsWith("/api/")) {
                // Treat non-API paths as frontend asset requests.
                serveStatic(exchange, path);
                return;
            }
            String apiSuffix = "/api".equals(path) ? "" : path.substring(5);
            List<String> parts = splitPath(apiSuffix);
            if (parts.isEmpty()) {
                // Return a simple index when the API root is requested.
                writeJson(exchange, 200, Map.of("service", "IPOS-SA", "routes", List.of("auth", "users", "merchants", "products", "orders", "invoices", "payments", "reports", "non-commercial-applications", "integrations")));
                return;
            }
            route(exchange, method, parts);
        } catch (ApiException ex) {
            // Preserve intended status codes for known API failures.
            writeJson(exchange, ex.statusCode, Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            ex.printStackTrace();
            // Hide unexpected failures behind a generic server response.
            writeJson(exchange, 500, Map.of("error", ex.getMessage() == null ? "Internal server error" : ex.getMessage()));
        } finally {
            exchange.close();
        }
    }

    /**
     * Routes an API request to the handler responsible for the first path segment.
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the non-empty path segments after the /api/ prefix
     * @throws Exception if the selected route handler fails
     */
    private void route(HttpExchange exchange, String method, List<String> parts) throws Exception {
        // Dispatch by the first segment after /api/.
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
            case "integrations" -> handleIntegrations(exchange, method, parts);
            default -> throw new ApiException(404, "Route not found");
        }
    }

    /**
     * Processes authentication requests.
     *
     * <p>Only {@code POST /api/auth/login} is supported. The request body must contain valid
     * credentials, which are passed to the database login flow, and the resulting login payload is
     * returned as JSON.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authentication fails or the request body cannot be parsed
     */
    private void handleAuth(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() != 2) {
            throw new ApiException(404, "Route not found");
        }
        if ("POST".equals(method) && "login".equals(parts.get(1))) {
            // Read credentials once before validating required fields.
            Map<String, Object> body = body(exchange);
            writeJson(exchange, 200, db.login(JsonUtil.requireString(body, "username"), JsonUtil.requireString(body, "password")));
            return;
        }
        if ("GET".equals(method) && "session".equals(parts.get(1))) {
            writeJson(exchange, 200, db.getSession(exchange.getRequestHeaders()));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    /**
     * Processes user management requests.
     *
     * <p>All user operations require administrator access. The handler supports listing users, creating
     * a user, updating a user by username, and deleting a user by username.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
    private void handleUsers(HttpExchange exchange, String method, List<String> parts) throws Exception {
        // All user routes are restricted to administrators.
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

    /**
     * Processes merchant-related requests.
     *
     * <p>This handler supports listing, creating, retrieving, updating, deleting, restoring merchants,
     * managing discount plans, evaluating accounts, and retrieving merchant balances. Access control is
     * enforced per operation based on the caller's role.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
    private void handleMerchants(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF", "MERCHANT");
            writeJson(exchange, 200, Map.of("merchants", db.listMerchants(exchange.getRequestHeaders(), query(exchange))));
            return;
        }
        if (parts.size() == 1 && "POST".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR");
            writeJson(exchange, 201, db.createMerchant(body(exchange)));
            return;
        }
        if (parts.size() >= 2) {
            // Reuse the merchant id for all nested merchant actions.
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

    /**
     * Processes product-related requests.
     *
     * <p>The handler supports listing products, creating products, searching, low-stock reporting,
     * retrieving a product, updating or deleting a product, and adjusting stock or minimum stock
     * thresholds. Sensitive actions require elevated roles.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
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
            // Default to an empty search term if q is not supplied.
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

    /**
     * Processes order-related requests.
     *
     * <p>The handler supports listing orders, creating a new order for the current merchant, retrieving
     * an order, listing pending orders, updating order status, and generating invoices from orders.
     * Access is restricted according to the caller's role.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
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
            // Order ids are numeric path parameters.
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

    /**
     * Processes invoice-related requests.
     *
     * <p>The handler supports listing invoices and retrieving a single invoice by ID. Merchant users are
     * limited to invoices belonging to their own merchant account.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
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

    /**
     * Processes payment-related requests.
     *
     * <p>The handler supports listing payments and recording a new payment. Payment actions are limited
     * to authorized accounting or administrative roles.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
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

    /**
     * Processes report requests.
     *
     * <p>Only authorized staff roles may access reports, and only via {@code GET}. The second path
     * segment determines which report is generated, such as turnover, stock turnover, low stock, debtor
     * reminders, merchant activity, or invoice summaries.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
    private void handleReports(HttpExchange exchange, String method, List<String> parts) throws Exception {
        // Report access is centralized here before selecting a specific report.
        requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF");
        if (!"GET".equals(method) || parts.size() != 2) {
            throw new ApiException(404, "Route not found");
        }
        Map<String, String> query = query(exchange);
        // Delegate report generation based on the second path segment.
        Map<String, Object> result = switch (parts.get(1)) {
            case "turnover" -> db.turnoverReport(query);
            case "stock-turnover" -> db.stockTurnoverReport(query);
            case "low-stock" -> db.lowStockReport();
            case "debtor-reminders" -> db.debtorRemindersReport();
            case "merchant-orders" -> db.merchantOrdersReport(query);
            case "merchant-activity" -> db.merchantActivityReport(query);
            case "merchant-invoices" -> db.merchantInvoicesReport(query);
            case "company-invoices" -> db.companyInvoicesReport(query);
            default -> throw new ApiException(404, "Route not found");
        };
        writeJson(exchange, 200, result);
    }

    /**
     * Processes non-commercial application requests.
     *
     * <p>The handler supports submitting new applications, listing applications for privileged roles,
     * and recording a decision for a specific application. Decisions may result in a generated password
     * and an email log entry.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the database operation fails
     */
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

    /**
     * Processes cross-subsystem integration requests and diagnostics.
     *
     * <p>This handler exposes the configured integration endpoints and lets privileged
     * users relay Team C mail and payment requests through Team A's backend during
     * integration testing and demo rehearsal.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param method the HTTP request method
     * @param parts the path segments after the {@code /api/} prefix
     * @throws Exception if authorization fails, the route is invalid, or the relay cannot be processed
     */
    private void handleIntegrations(HttpExchange exchange, String method, List<String> parts) throws Exception {
        if (parts.size() == 1 && "GET".equals(method)) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF", "OPERATIONS_STAFF");
            writeJson(exchange, 200, Map.of("integrations", db.describeIntegrations()));
            return;
        }
        if (parts.size() == 3 && "POST".equals(method) && "pu".equals(parts.get(1))) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "ACCOUNTING_STAFF");
            if ("mail".equals(parts.get(2))) {
                writeJson(exchange, 200, db.sendPuMail(body(exchange)));
                return;
            }
            if ("pay".equals(parts.get(2))) {
                writeJson(exchange, 200, db.sendPuPayment(body(exchange)));
                return;
            }
        }
        if (parts.size() == 3 && "POST".equals(method) && "ca".equals(parts.get(1)) && "stock".equals(parts.get(2))) {
            requireRole(exchange, "ADMINISTRATOR", "MANAGER", "OPERATIONS_STAFF");
            writeJson(exchange, 200, db.sendCaStock(body(exchange)));
            return;
        }
        throw new ApiException(404, "Route not found");
    }

    /**
     * Reads and parses the request body as a JSON object.
     *
     * <p>The entire request body is consumed as UTF-8 text and converted into a JSON object map.</p>
     *
     * @param exchange the HTTP exchange whose request body should be read
     * @return the parsed JSON object as a map
     * @throws IOException if the body cannot be read or parsed
     */
    private Map<String, Object> body(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            // Read the entire request body before handing it to the JSON parser.
            return JsonUtil.asObject(JsonUtil.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    /**
     * Parses the raw query string into a map of decoded key-value pairs.
     *
     * <p>Blank segments are ignored, keys and values are URL-decoded, and parameters without an explicit
     * value are mapped to an empty string.</p>
     *
     * @param exchange the HTTP exchange whose query string should be parsed
     * @return a map of decoded query parameters
     */
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
            // Allow keys without a value by mapping them to an empty string.
            params.put(URLDecoder.decode(split[0], StandardCharsets.UTF_8), split.length > 1 ? URLDecoder.decode(split[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }

    /**
     * Verifies that the current request is authenticated and has one of the allowed roles.
     *
     * <p>This is a convenience wrapper around database authorization that enforces role-based access
     * control before a handler proceeds.</p>
     *
     * @param exchange the HTTP exchange containing the current request headers
     * @param allowedRoles the roles permitted to perform the operation
     * @throws Exception if authentication fails or the role is not permitted
     */
    private void requireRole(HttpExchange exchange, String... allowedRoles) throws Exception {
        db.authorize(exchange.getRequestHeaders(), allowedRoles);
    }

    /**
     * Splits a URL path into non-blank path segments.
     *
     * <p>Leading, trailing, and repeated slashes are ignored so the returned list contains only useful
     * route segments.</p>
     *
     * @param path the raw path string to split
     * @return a list of non-blank path segments
     */
    private static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                // Ignore empty segments caused by leading or repeated slashes.
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * Serializes a payload to JSON and writes it to the HTTP response.
     *
     * <p>The response is sent with UTF-8 JSON content type, CORS headers, and the provided status code.
     * The response body is written fully and then closed.</p>
     *
     * @param exchange the HTTP exchange to write to
     * @param statusCode the HTTP status code to send
     * @param payload the response payload to serialize as JSON
     * @throws IOException if the response cannot be written
     */
    private static void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] bytes = JsonUtil.stringify(payload).getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        // Apply shared response headers to every JSON endpoint.
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-Session-Token");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Serves a static file from the configured static root.
     *
     * <p>The root path is protected against directory traversal. Requests for {@code /} are mapped to
     * {@code /login.html}. If the requested file does not exist or is a directory, a not found error is
     * raised.</p>
     *
     * @param exchange the HTTP exchange representing the request and response
     * @param path the requested URL path
     * @throws IOException if the file cannot be read or the response cannot be written
     */
    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String requested = path.equals("/") ? "/login.html" : path;
        Path resolved = staticRoot.resolve("." + requested).normalize();
        // Resolve against the static root to block directory traversal.
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

    /**
     * Determines the MIME type for a static file based on its extension.
     *
     * <p>Common web asset types are mapped explicitly, and all other files fall back to a generic binary
     * content type.</p>
     *
     * @param file the file whose content type should be determined
     * @return the appropriate content type string
     */
    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        // Keep content types explicit for the frontend assets this app serves.
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }
}
