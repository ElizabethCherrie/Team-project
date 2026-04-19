package ipossa;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight outbound integration client for cross-subsystem communication.
 *
 * <p>This client prepares the callback points needed for Demo Day without making
 * the local IPOS-SA flow depend on unfinished endpoints from the other teams.</p>
 */
final class IntegrationClient {
    private final HttpClient httpClient;
    private final String caStockSyncUrl;
    private final String puPaymentUrl;
    private final String puMailUrl;
    private final int caMarkupRate;

    /**
     * Creates the client from runtime configuration.
     *
     * @param caStockSyncUrl the CA stock synchronization endpoint, or blank to disable
     * @param puPaymentUrl the PU payment endpoint, or blank to disable
     * @param puMailUrl the PU mail endpoint, or blank to disable
     * @param caMarkupRate the markup multiplier expected by IPOS-CA
     */
    IntegrationClient(String caStockSyncUrl, String puPaymentUrl, String puMailUrl, int caMarkupRate) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.caStockSyncUrl = normalize(caStockSyncUrl);
        this.puPaymentUrl = normalize(puPaymentUrl);
        this.puMailUrl = normalize(puMailUrl);
        this.caMarkupRate = caMarkupRate <= 0 ? 2 : caMarkupRate;
    }

    /**
     * Creates the client using environment-variable defaults for the other subsystems.
     *
     * @return the configured integration client
     */
    static IntegrationClient fromEnvironment() {
        return new IntegrationClient(
                envOrDefault("IPOS_CA_STOCK_SYNC_URL", "http://localhost:8088/stock/ipos"),
                envOrDefault("IPOS_PU_PAYMENT_URL", "http://localhost:8090/pay"),
                envOrDefault("IPOS_PU_MAIL_URL", "http://localhost:8090/mail"),
                parseMarkupRate(envOrDefault("IPOS_CA_MARKUP_RATE", "2"))
        );
    }

    /**
     * Sends a delivered-order stock synchronization payload to IPOS-CA when configured.
     *
     * @param order the delivered order data
     * @param items the delivered order items enriched with product names
     * @return a result map describing whether the callback was sent successfully
     */
    Map<String, Object> notifyCaDelivery(Map<String, Object> order, List<Map<String, Object>> items) {
        if (caStockSyncUrl == null) {
            return Map.of(
                    "target", "IPOS-CA",
                    "status", "SKIPPED",
                    "reason", "No CA stock sync URL configured"
            );
        }

        List<Map<String, Object>> results = new ArrayList<>();
        boolean allSent = true;
        for (Map<String, Object> item : items) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", item.get("name"));
            payload.put("packageType", normalizePackageType(item.get("packageType")));
            payload.put("units", normalizeUnitType(item.get("units")));
            payload.put("unitsInAPack", item.get("unitsInAPack"));
            payload.put("bulkCost", item.get("bulkCost"));
            payload.put("markupRate", caMarkupRate);
            payload.put("quantity", item.get("quantity"));
            payload.put("stockLimit", item.get("stockLimit"));

            Map<String, Object> result = postJson("IPOS-CA", caStockSyncUrl, payload);
            result = new LinkedHashMap<>(result);
            result.put("productId", item.get("productId"));
            results.add(result);
            if (!"SENT".equals(result.get("status"))) {
                allSent = false;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("target", "IPOS-CA");
        summary.put("status", allSent ? "SENT" : "FAILED");
        summary.put("url", caStockSyncUrl);
        summary.put("merchantId", order.get("merchant_id"));
        summary.put("sourceOrderId", order.get("order_id"));
        summary.put("results", results);
        return summary;
    }

    /**
     * Sends a single stock item payload to Team B's IPOS-CA subsystem using the agreed contract.
     *
     * @param payload the stock payload shaped to Team B's expected JSON body
     * @return a result map describing whether the callback was sent successfully
     */
    Map<String, Object> sendCaStockItem(Map<String, Object> payload) {
        if (caStockSyncUrl == null) {
            return Map.of(
                    "target", "IPOS-CA",
                    "status", "SKIPPED",
                    "reason", "No CA stock sync URL configured"
            );
        }
        Map<String, Object> normalizedPayload = new LinkedHashMap<>();
        normalizedPayload.put("name", payload.get("name"));
        normalizedPayload.put("packageType", normalizePackageType(payload.get("packageType")));
        normalizedPayload.put("units", normalizeUnitType(payload.get("units")));
        normalizedPayload.put("unitsInAPack", payload.get("unitsInAPack"));
        normalizedPayload.put("bulkCost", payload.get("bulkCost"));
        normalizedPayload.put("markupRate", payload.getOrDefault("markupRate", caMarkupRate));
        normalizedPayload.put("quantity", payload.get("quantity"));
        normalizedPayload.put("stockLimit", payload.get("stockLimit"));
        return postJson("IPOS-CA", caStockSyncUrl, normalizedPayload);
    }

    /**
     * Sends a mail request to IPOS-PU when configured.
     *
     * @param sender the sender label/email to present
     * @param receivers the recipient list
     * @param subject the mail subject
     * @param body the mail body
     * @return a result map describing whether the callback was sent successfully
     */
    Map<String, Object> sendPuMail(String sender, List<String> receivers, String subject, String body) {
        if (puMailUrl == null) {
            return Map.of(
                    "target", "IPOS-PU",
                    "status", "SKIPPED",
                    "reason", "No PU mail URL configured"
            );
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("receivers", receivers);
        payload.put("subject", subject);
        payload.put("body", body);
        return postJson("IPOS-PU", puMailUrl, payload);
    }

    /**
     * Sends a payment request to IPOS-PU when configured.
     *
     * @param payload the payment request body already shaped to Team C's contract
     * @return a result map describing whether the callback was sent successfully
     */
    Map<String, Object> sendPuPayment(Map<String, Object> payload) {
        if (puPaymentUrl == null) {
            return Map.of(
                    "target", "IPOS-PU",
                    "status", "SKIPPED",
                    "reason", "No PU payment URL configured"
            );
        }
        return postJson("IPOS-PU", puPaymentUrl, payload);
    }

    /**
     * Returns a compact summary of the configured external endpoints.
     *
     * @return a map describing the current outbound integration configuration
     */
    Map<String, Object> describeConfiguration() {
        return Map.of(
                "caStockSyncUrl", Objects.toString(caStockSyncUrl, ""),
                "caMarkupRate", caMarkupRate,
                "puPaymentUrl", Objects.toString(puPaymentUrl, ""),
                "puMailUrl", Objects.toString(puMailUrl, "")
        );
    }

    private Map<String, Object> postJson(String target, String url, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.stringify(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return Map.of(
                    "target", target,
                    "status", response.statusCode() >= 200 && response.statusCode() < 300 ? "SENT" : "FAILED",
                    "url", url,
                    "httpStatus", response.statusCode(),
                    "responseBody", response.body()
            );
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Map.of(
                    "target", target,
                    "status", "FAILED",
                    "url", url,
                    "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            );
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static int parseMarkupRate(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 2;
        }
    }

    private static String normalizePackageType(Object value) {
        String normalized = Objects.toString(value, "OTHER").trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "BOX", "BOTTLE" -> normalized;
            default -> "BOX";
        };
    }

    private static String normalizeUnitType(Object value) {
        String normalized = Objects.toString(value, "OTHER").trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "CAPS", "ML", "OTHER" -> normalized;
            default -> "OTHER";
        };
    }
    Map<String, Object> testConnection(String target) {
        if ("CA".equals(target) && caStockSyncUrl != null) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(caStockSyncUrl + "/health"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return Map.of("target", "IPOS-CA", "status", response.statusCode() == 200 ? "ONLINE" : "UNKNOWN",
                        "url", caStockSyncUrl, "httpStatus", response.statusCode());
            } catch (Exception e) {
                return Map.of("target", "IPOS-CA", "status", "OFFLINE", "url", caStockSyncUrl, "error", e.getMessage());
            }
        }
        if ("PU".equals(target) && puPaymentUrl != null) {
            try {
                String baseUrl = puPaymentUrl.replace("/pay", "");
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return Map.of("target", "IPOS-PU", "status", response.statusCode() == 200 ? "ONLINE" : "UNKNOWN",
                        "url", baseUrl, "httpStatus", response.statusCode());
            } catch (Exception e) {
                return Map.of("target", "IPOS-PU", "status", "OFFLINE", "url", puPaymentUrl, "error", e.getMessage());
            }
        }
        return Map.of("target", target, "status", "NOT_CONFIGURED");
    }
}
