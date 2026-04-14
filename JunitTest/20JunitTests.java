package ipossa;
import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

// so these are test for IPOS-SA Database class, the service layer that interacts with external api's.
// The test cover ACC, CAT, and ORD test cases
// I used a  temporary SQLite file as a new connection is opened on each call

public class DatabaseTest {

    private Database db;
    private Path tempDb;
    
    // this is the temporary database
    @BeforeEach
    void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        tempDb = Files.createTempFile("ipos-sa-test-", ".db");
        IntegrationClient client = IntegrationClient.fromEnvironment();
        db = new Database(tempDb, client);
        db.bootstrap();
    }

    //to clean up after each test
    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempDb);
    }

 

    //this helper logs in and returns a valid session header
    private Headers sessionFor(String username, String password) throws Exception {
        Map<String, Object> result = db.login(username, password);
        String token = (String) result.get("sessionToken");
        Headers headers = new Headers();
        headers.add("X-Session-Token", token);
        return headers;
    }

 

    // this section is the Account Management 
 
    // So this creates a merchant with all the required details and checks that the response confirms creation and returns the correct merchant ID
    @Test
    void ACC01_createMerchant_fullDetailsFixedDiscount_succeeds() throws Exception {
        Map<String, Object> body = Map.of(
                "merchantId", "MCH-TEST-01",
                "name", "Test Pharmacy",
                "email", "test@pharmacy.com",
                "address", "1 Test Street, London",
                "creditLimit", 5000.0,
                "discountType", "FIXED",
                "fixedDiscountRate", 5.0,
                "username", "testmerchant01",
                "password", "testpass01"
        );
        Map<String, Object> result = db.createMerchant(body);

        assertEquals("Merchant created", result.get("message"),
                "ACC-01: Merchant creation should be confirmed");
        assertEquals("MCH-TEST-01", result.get("merchantId"),
                "ACC-01: Returned merchant ID should match input");
    }

  
    
    // Second, attempts to create a merchant with a missing section  
    @Test
    void ACC02_createMerchant_missingCreditLimit_throwsException() {
        Map<String, Object> body = Map.of(
                "merchantId", "MCH-TEST-02",
                "name", "Test Pharmacy 2",
                "email", "test2@pharmacy.com",
                "address", "2 Test Street, London",
                "username", "testmerchant02",
                "password", "testpass02"
                // creditLimit deliberately missing
        );

        assertThrows(Exception.class,
                () -> db.createMerchant(body),
                "ACC-02: Missing mandatory field should prevent account creation");
    }

   
    
    //So it creates a merchant with the credit limit of 0 
    @Test
    void ACC04_createMerchant_creditLimitZero_succeeds() throws Exception {
        Map<String, Object> body = Map.of(
                "merchantId", "MCH-TEST-04",
                "name", "Zero Limit Pharmacy",
                "email", "zero@pharmacy.com",
                "address", "4 Test Street, London",
                "creditLimit", 0.0,
                "discountType", "FIXED",
                "fixedDiscountRate", 0.0,
                "username", "testmerchant04",
                "password", "testpass04"
        );
        Map<String, Object> result = db.createMerchant(body);

        assertEquals("Merchant created", result.get("message"),
                "ACC-04: Credit limit of 0 should be accepted as a valid boundary value");
    }

  
    
    // Updates a merchants credit limit to a new valid limit bigger than 0 
    @Test
    void ACC05_updateMerchant_validCreditLimit_succeeds() throws Exception {
        Map<String, Object> result = db.updateMerchant("ACC0001",
                Map.of("creditLimit", 15000.0));

        assertEquals("Merchant updated", result.get("message"),
                "ACC-05: Valid credit limit update should be confirmed");
    }

    
    //updates the merchant discount plan to teh FLEXIBLE type with teh correct thresholds
    @Test
    void ACC07_updateDiscountPlan_flexibleWithValidThresholds_succeeds() throws Exception {
        Map<String, Object> result = db.updateDiscountPlan("ACC0001", Map.of(
                "discountType", "FLEXIBLE",
                "flexibleRateTier1", 1.0,
                "flexibleRateTier2", 2.0,
                "flexibleRateTier3", 3.0
        ));

        assertEquals("Discount plan updated", result.get("message"),
                "ACC-07: Valid FLEXIBLE discount plan update should be confirmed");
        assertEquals("FLEXIBLE", result.get("discountType"),
                "ACC-07: Discount type should be updated to FLEXIBLE");
    }

    
    
    //Updates the discount plan with a value that is invalid and makes sure the system rejetcs it
    @Test
    void ACC08_updateDiscountPlan_invalidType_throwsException() {
        ApiException ex = assertThrows(ApiException.class,
                () -> db.updateDiscountPlan("ACC0001", Map.of(
                        "discountType", "INVALID_TYPE"
                )));
        assertEquals(400, ex.statusCode,
                "ACC-08: Invalid discount type should be rejected with 400");
    }

 
    
    //restores a merchant account that isnt active with approval 
    @Test
    void ACC09_restoreMerchant_withDirectorApproval_succeeds() throws Exception {
        Map<String, Object> result = db.restoreMerchant("ACC0001", Map.of(
                "directorApproved", true,
                "newStatus", "NORMAL"
        ));

        assertEquals("Merchant restored", result.get("message"),
                "ACC-09: Merchant restore with director approval should succeed");
        assertEquals("NORMAL", result.get("newStatus"),
                "ACC-09: Merchant status should be set to NORMAL");
    }

    
    // This section is  Catalogue Management 
    
    
   
    //Adds a new product to the catalogue with valid details and checks that it can be retrieved successfully
    @Test
    void CAT01_createProduct_validDetails_productAppearsInCatalogue() throws Exception {
        Map<String, Object> body = Map.of(
                "productId", "CAT-TEST-01",
                "name", "New Test Drug",
                "unitPrice", 12.50,
                "stockLevel", 200,
                "minimumStockLevel", 50
        );
        db.createProduct(body);

        Map<String, Object> fetched = db.getProduct("CAT-TEST-01");
        assertEquals("New Test Drug", fetched.get("name"),
                "CAT-01: Created product should appear in the catalogue");
    }

   
    // tries to get the product with a unknown id
    @Test
    void CAT_getProduct_unknownId_throws404() {
        ApiException ex = assertThrows(ApiException.class,
                () -> db.getProduct("UNKNOWN-ID-999"),
                "CAT: Unknown product ID should return 404");
        assertEquals(404, ex.statusCode);
    }

  
    
    //Deletes a existing product and makes sure its deleted
    @Test
    void CAT_deleteProduct_existingProduct_removedFromCatalogue() throws Exception {
        db.createProduct(Map.of(
                "productId", "DEL-TEST-01",
                "name", "Product To Delete",
                "unitPrice", 1.00,
                "stockLevel", 10,
                "minimumStockLevel", 5
        ));
        db.deleteProduct("DEL-TEST-01");

        ApiException ex = assertThrows(ApiException.class,
                () -> db.getProduct("DEL-TEST-01"));
        assertEquals(404, ex.statusCode,
                "CAT: Deleted product should no longer be found in catalogue");
    }

   
    
    // Adds stock to an existing product and checks that the stock level increases by the correct amount
    @Test
    void CAT_addStock_validQuantity_stockLevelIncreases() throws Exception {
        int before = ((Number) db.getProduct("10000002").get("stock_level")).intValue();

        db.addStock("10000002", Map.of("quantity", 500));

        int after = ((Number) db.getProduct("10000002").get("stock_level")).intValue();
        assertEquals(before + 500, after,
                "CAT: Stock level should increase by exactly the restocked quantity");
    }

 
    
    //so searches for a product by the name keyword
    @Test
    void CAT_searchProducts_validKeyword_returnsMatchingProducts() throws Exception {
        List<Map<String, Object>> results = db.searchProducts("Paracetamol");

        assertNotNull(results,
                "CAT: Search results should not be null");
        assertFalse(results.isEmpty(),
                "CAT: Search for Paracetamol should return at least one result");
        assertEquals("Paracetamol", results.get(0).get("name"),
                "CAT: First result should be Paracetamol");
    }

  
    
    //Adds a stock with a quantity of 0 and checks that the system rejects it with a 400 error
    @Test
    void CAT_addStock_zeroQuantity_throws400() {
        ApiException ex = assertThrows(ApiException.class,
                () -> db.addStock("10000001", Map.of("quantity", 0)));
        assertEquals(400, ex.statusCode,
                "CAT: Zero quantity restock should be rejected with 400");
    }

 
    // this is the Order Management section 
    
    
   
    //Creates a order valid details and checks its created successfully
    @Test
    void ORD01_createOrder_validMerchantAndStock_orderCreatedStockReduced() throws Exception {
        Headers headers = sessionFor("city", "northampton");
        int stockBefore = ((Number) db.getProduct("10000001").get("stock_level")).intValue();

        Map<String, Object> result = db.createOrder(headers, Map.of(
                "merchantId", "ACC0001",
                "items", List.of(Map.of("productId", "10000001", "quantity", 12))
        ));

        assertNotNull(result.get("orderId"),
                "ORD-01: Order ID should be returned on success");
        assertNotNull(result.get("invoice"),
                "ORD-01: Invoice should be generated automatically");
        int stockAfter = ((Number) db.getProduct("10000001").get("stock_level")).intValue();
        assertEquals(stockBefore - 12, stockAfter,
                "ORD-01: Stock should be reduced by the ordered quantity");
    }

   
    
    // so checks when a order quantity is higher than the stock availability.
    @Test
    void ORD02_createOrder_quantityExceedsStock_orderRejected() throws Exception {
        Headers headers = sessionFor("city", "northampton");
        int stock = ((Number) db.getProduct("30000001").get("stock_level")).intValue();

        ApiException ex = assertThrows(ApiException.class,
                () -> db.createOrder(headers, Map.of(
                        "merchantId", "ACC0001",
                        "items", List.of(Map.of("productId", "30000001",
                                "quantity", stock + 1000))
                )));
        assertEquals(400, ex.statusCode,
                "ORD-02: Order exceeding stock should be rejected with 400");
    }

  
    
    // Dose it contain a unknown id
    @Test
    void ORD03_createOrder_unknownProductId_orderRejected() throws Exception {
        Headers headers = sessionFor("city", "northampton");

        ApiException ex = assertThrows(ApiException.class,
                () -> db.createOrder(headers, Map.of(
                        "merchantId", "ACC0001",
                        "items", List.of(Map.of("productId", "UNKNOWN-107",
                                "quantity", 5))
                )));
        assertEquals(404, ex.statusCode,
                "ORD-03: Unknown product ID should cause order to be rejected with 404");
    }

  
    //Dose the system reject a empty order
    @Test
    void ORD04_createOrder_emptyItemsList_orderRejected() throws Exception {
        Headers headers = sessionFor("city", "northampton");

        ApiException ex = assertThrows(ApiException.class,
                () -> db.createOrder(headers, Map.of(
                        "merchantId", "ACC0001",
                        "items", List.of()
                )));
        assertEquals(400, ex.statusCode,
                "ORD-04: Empty order should be rejected with 400");
    }

    // Dose the system reject a order with a quantity of 0
    @Test
    void ORD05_createOrder_quantityZero_validationError() throws Exception {
        Headers headers = sessionFor("city", "northampton");

        ApiException ex = assertThrows(ApiException.class,
                () -> db.createOrder(headers, Map.of(
                        "merchantId", "ACC0001",
                        "items", List.of(Map.of("productId", "10000001",
                                "quantity", 0))
                )));
        assertEquals(400, ex.statusCode,
                "ORD-05: Quantity of 0 should be rejected with a 400 validation error");
    }

    
    
    // Updates the order to DISPATCHED with a valid courier and tracking info 
    @Test
    void ORD08_updateOrderStatus_dispatched_withValidCourierInfo_succeeds() throws Exception {
        // First create an order to get an order ID
        Headers headers = sessionFor("city", "northampton");
        Map<String, Object> orderResult = db.createOrder(headers, Map.of(
                "merchantId", "ACC0001",
                "items", List.of(Map.of("productId", "10000001", "quantity", 5))
        ));
        long orderId = ((Number) orderResult.get("orderId")).longValue();

        // Move to PROCESSING first (required transition)
        db.updateOrderStatus(orderId, Map.of("status", "PROCESSING"));

        // Now dispatch with valid courier info
        Map<String, Object> result = db.updateOrderStatus(orderId, Map.of(
                "status", "DISPATCHED",
                "courier", "DHL",
                "trackingNumber", "REF-992211",
                "expectedDelivery", "2026-04-20"
        ));

        assertEquals("Order status updated", result.get("message"),
                "ORD-08: Valid dispatch update should be confirmed");
        assertEquals("DISPATCHED", result.get("status"),
                "ORD-08: Order status should be updated to DISPATCHED");
    }

   
    
    // This updates the order to DISPATCHED without providing the courier info and checks that the system rejects it with a 400 error
    @Test
    void ORD09_updateOrderStatus_dispatched_missingCourierInfo_throws400() throws Exception {
        // Create and move order to PROCESSING
        Headers headers = sessionFor("city", "northampton");
        Map<String, Object> orderResult = db.createOrder(headers, Map.of(
                "merchantId", "ACC0001",
                "items", List.of(Map.of("productId", "10000001", "quantity", 5))
        ));
        long orderId = ((Number) orderResult.get("orderId")).longValue();
        db.updateOrderStatus(orderId, Map.of("status", "PROCESSING"));

        // Try to dispatch without courier info
        ApiException ex = assertThrows(ApiException.class,
                () -> db.updateOrderStatus(orderId, Map.of(
                        "status", "DISPATCHED"
                        // courier, trackingNumber, expectedDelivery deliberately missing
                )));
        assertEquals(400, ex.statusCode,
                "ORD-09: Missing courier info should reject dispatch with 400");
    }
}
