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

    /**
     * Creates the client from runtime configuration.
     *
     * @param caStockSyncUrl the CA stock synchronization endpoint, or blank to disable
     * @param puPaymentUrl the PU payment endpoint, or blank to disable
     * @param puMailUrl the PU mail endpoint, or blank to disable
     */
    IntegrationClient(String caStockSyncUrl, String puPaymentUrl, String puMailUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.caStockSyncUrl = normalize(caStockSyncUrl);
        this.puPaymentUrl = normalize(puPaymentUrl);
        this.puMailUrl = normalize(puMailUrl);
    }

    /**
     * Creates the client using environment-variable defaults for the other subsystems.
     *
     * @return the configured integration client
     */
    static IntegrationClient fromEnvironment() {
        return new IntegrationClient(
                System.getenv().getOrDefault("IPOS_CA_STOCK_SYNC_URL", ""),
                System.getenv().getOrDefault("IPOS_PU_PAYMENT_URL", ""),
                System.getenv().getOrDefault("IPOS_PU_MAIL_URL", "")
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("merchantId", order.get("merchant_id"));
        payload.put("sourceOrderId", order.get("order_id"));
        payload.put("reason", "SA_DELIVERY");
        payload.put("deliveredAt", order.get("delivered_date"));
        payload.put("items", items);

        return postJson("IPOS-CA", caStockSyncUrl, payload);
    }

    /**
     * Returns a compact summary of the configured external endpoints.
     *
     * @return a map describing the current outbound integration configuration
     */
    Map<String, Object> describeConfiguration() {
        return Map.of(
                "caStockSyncUrl", Objects.toString(caStockSyncUrl, ""),
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
}
