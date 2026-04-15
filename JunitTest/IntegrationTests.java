package ipossa;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationClientTest {

    private HttpServer server;
    private String baseUrl;

    private volatile String lastPath;
    private volatile String lastMethod;
    private volatile String lastBody;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        server.createContext("/ca/stock", this::handleSuccess);
        server.createContext("/pu/payment", this::handleSuccess);
        server.createContext("/error", this::handleError);

        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // -------------------------------
    // TEST 1: CA valid (SUCCESS)
    // -------------------------------
    @Test
    void sendCaStockItem_validPayload_succeeds() {
        IntegrationClient client = new IntegrationClient(
                baseUrl + "/ca/stock", "", "", 3
        );

        Map<String, Object> payload = Map.of(
                "name", "Paracetamol",
                "packageType", "crate",
                "units", "tablet",
                "unitsInAPack", 24,
                "bulkCost", 18.50,
                "quantity", 10,
                "stockLimit", 5
        );

        Map<String, Object> result = client.sendCaStockItem(payload);

        assertEquals("SENT", result.get("status"));
        assertEquals(200, result.get("httpStatus"));
    }

    // -------------------------------
    // TEST 2: CA no URL (SKIPPED)
    // -------------------------------
    @Test
    void sendCaStockItem_noUrl_returnsSkipped() {
        IntegrationClient client = new IntegrationClient(
                "", "", "", 2
        );

        Map<String, Object> result = client.sendCaStockItem(Map.of());

        assertEquals("SKIPPED", result.get("status"));
    }

    // -------------------------------
    // TEST 3: CA server error (FAILED)
    // -------------------------------
    @Test
    void sendCaStockItem_serverError_returnsFailed() {
        IntegrationClient client = new IntegrationClient(
                baseUrl + "/error", "", "", 2
        );

        Map<String, Object> result = client.sendCaStockItem(Map.of(
                "name", "Test"
        ));

        assertEquals("FAILED", result.get("status"));
    }

    // -------------------------------
    // TEST 4: PU valid (SUCCESS)
    // -------------------------------
    @Test
    void sendPuPayment_validPayload_succeeds() {
        IntegrationClient client = new IntegrationClient(
                "", baseUrl + "/pu/payment", "", 2
        );

        Map<String, Object> payload = Map.of(
                "merchantId", "ACC0001",
                "amount", 100.0
        );

        Map<String, Object> result = client.sendPuPayment(payload);

        assertEquals("SENT", result.get("status"));
        assertEquals(200, result.get("httpStatus"));
    }

    // -------------------------------
    // TEST 5: PU no URL (SKIPPED)
    // -------------------------------
    @Test
    void sendPuPayment_noUrl_returnsSkipped() {
        IntegrationClient client = new IntegrationClient(
                "", "", "", 2
        );

        Map<String, Object> result = client.sendPuPayment(Map.of());

        assertEquals("SKIPPED", result.get("status"));
    }

    // -------------------------------
    // MOCK SERVER HANDLERS
    // -------------------------------
    private void handleSuccess(HttpExchange exchange) throws IOException {
        lastPath = exchange.getRequestURI().getPath();
        lastMethod = exchange.getRequestMethod();
        lastBody = readBody(exchange.getRequestBody());

        byte[] response = "{\"message\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void handleError(HttpExchange exchange) throws IOException {
        byte[] response = "{\"error\":\"fail\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, response.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private String readBody(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
