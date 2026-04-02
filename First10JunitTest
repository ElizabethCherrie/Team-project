import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OrderManagment_APITest {


    /
    private AccountManagement_API accountManagement;
    private ProductCatalogue_API productCatalogue;
    private OrderManagment_API orderApi;


    //this is a helper method to create a mock merchant with specified properties
    @BeforeEach
    void setUp() {
        accountManagement = mock(AccountManagement_API.class);
        productCatalogue = mock(ProductCatalogue_API.class);
        orderApi = new OrderManagment_API(accountManagement, productCatalogue);
    }

    private Merchant buildActiveMerchant(String merchantId, double balance, double creditLimit) {
        Merchant merchant = new Merchant();
        merchant.setMerchantID(merchantId);
        merchant.setStatus("ACTIVE");
        merchant.setBalance(balance);
        merchant.setCreditLimit(creditLimit);
        return merchant;
    }

    private OrderItem buildOrderItem(String productId, int quantity, double unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductID(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        return item;
    }

    private Order buildOrder(String merchantId, OrderItem[] items) {
        Order order = new Order();
        order.setMerchantID(merchantId);
        order.setItems(items);
        return order;
    }

    //so this verifys when a order is created it comes with a order id and ist status is pending where its date is set, the balance is updated and
    @Test
    void createOrder_validOrder_returnsGeneratedOrderId() {
        String merchantId = "M001";
        Merchant merchant = buildActiveMerchant(merchantId, 100.0, 1000.0);

        OrderItem item = buildOrderItem("P001", 2, 50.0);
        Order order = buildOrder(merchantId, new OrderItem[]{item});

        Product product = mock(Product.class);
        when(product.getStockLevel()).thenReturn(20);

        when(accountManagement.getMerchant(merchantId)).thenReturn(merchant);
        when(productCatalogue.getProduct("P001")).thenReturn(product);

        String orderId = orderApi.createOrder(order);

        assertNotNull(orderId);
        assertTrue(orderId.startsWith("ORD"));
        assertEquals(orderId, order.getOrderID());
        assertEquals("PENDING", order.getStatus());
        assertNotNull(order.getOrderDate());
        assertEquals(200.0, merchant.getBalance(), 0.001);

        verify(productCatalogue).updateStock("P001", 18);
    }

    // to test if something incorrect happes like a null order is trying ot be passed
    @Test
    void createOrder_nullOrder_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> orderApi.createOrder(null));
    }

    // this test verifies that when we try to get an order with a valid order id we get the correct order details back, and if we try to get an order with a null order id we get an exception
    @Test
    void getOrder_validOrderId_returnsOrder() {
        String merchantId = "M001";
        Merchant merchant = buildActiveMerchant(merchantId, 0.0, 1000.0);

        OrderItem item = buildOrderItem("P001", 1, 25.0);
        Order order = buildOrder(merchantId, new OrderItem[]{item});

        Product product = mock(Product.class);
        when(product.getStockLevel()).thenReturn(10);

        when(accountManagement.getMerchant(merchantId)).thenReturn(merchant);
        when(productCatalogue.getProduct("P001")).thenReturn(product);

        String orderId = orderApi.createOrder(order);

        Order result = orderApi.getOrder(orderId);

        assertNotNull(result);
        assertEquals(merchantId, result.getMerchantID());
        assertEquals(1, result.getItems().length);
    }

    // this test verifies that when we try to get an order with a null order id we get an exception
    @Test
    void getOrder_nullOrderId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> orderApi.getOrder(null));
    }

    //shows that when we try to update the order status with a valid order id and a valid status it updates the status and returns true, but if we try to update the order status with a null status it returns false
    @Test
    void updateOrderStatus_validOrderAndStatus_returnsTrue() {
        String merchantId = "M001";
        Merchant merchant = buildActiveMerchant(merchantId, 0.0, 1000.0);

        OrderItem item = buildOrderItem("P001", 1, 30.0);
        Order order = buildOrder(merchantId, new OrderItem[]{item});

        Product product = mock(Product.class);
        when(product.getStockLevel()).thenReturn(15);

        when(accountManagement.getMerchant(merchantId)).thenReturn(merchant);
        when(productCatalogue.getProduct("P001")).thenReturn(product);

        String orderId = orderApi.createOrder(order);

        boolean updated = orderApi.updateOrderStatus(orderId, "DISPATCHED");

        assertTrue(updated);
        assertEquals("DISPATCHED", orderApi.getOrder(orderId).getStatus());
    }

    // when we try to update the order status with a null status it should return false and not update the status
    @Test
    void updateOrderStatus_nullStatus_returnsFalse() {
        String merchantId = "M001";
        Merchant merchant = buildActiveMerchant(merchantId, 0.0, 1000.0);

        OrderItem item = buildOrderItem("P001", 1, 30.0);
        Order order = buildOrder(merchantId, new OrderItem[]{item});

        Product product = mock(Product.class);
        when(product.getStockLevel()).thenReturn(15);

        when(accountManagement.getMerchant(merchantId)).thenReturn(merchant);
        when(productCatalogue.getProduct("P001")).thenReturn(product);

        String orderId = orderApi.createOrder(order);

        boolean updated = orderApi.updateOrderStatus(orderId, null);

        assertFalse(updated);
    }

    // this test verifies that when we calculate the order total for a valid order id we get the correct total, and if we try to calculate the order total for an invalid order id we get zero
    @Test
    void calculateOrderTotal_validOrderId_returnsCorrectTotal() {
        String merchantId = "M001";
        Merchant merchant = buildActiveMerchant(merchantId, 0.0, 1000.0);

        OrderItem item1 = buildOrderItem("P001", 2, 10.0); // 20
        OrderItem item2 = buildOrderItem("P002", 3, 5.0);  // 15
        Order order = buildOrder(merchantId, new OrderItem[]{item1, item2});

        Product product1 = mock(Product.class);
        Product product2 = mock(Product.class);

        when(product1.getStockLevel()).thenReturn(20);
        when(product2.getStockLevel()).thenReturn(20);

        when(accountManagement.getMerchant(merchantId)).thenReturn(merchant);
        when(productCatalogue.getProduct("P001")).thenReturn(product1);
        when(productCatalogue.getProduct("P002")).thenReturn(product2);

        String orderId = orderApi.createOrder(order);

        double total = orderApi.calculateOrderTotal(orderId);

        assertEquals(35.0, total, 0.001);
    }

    // shows that when we try to calculate the order total for an invalid order id we get zero
    @Test
    void calculateOrderTotal_invalidOrderId_returnsZero() {
        double total = orderApi.calculateOrderTotal("DOES_NOT_EXIST");
        assertEquals(0.0, total, 0.001);
    }

    // can we cancel a pending order and does it update the stock levels and merchant balance correctly, and if we try to cancel an order that is not pending it should return false
    @Test
    void cancelOrder_pendingOrder_returnsTrueAndSetsCancelled() {
        String merchantId = "M001";
        Merchant merchant = buildActiveMerchant(merchantId, 0.0, 1000.0);

        OrderItem item = buildOrderItem("P001", 2, 40.0); // total 80
        Order order = buildOrder(merchantId, new OrderItem[]{item});

        Product product = mock(Product.class);
        when(product.getStockLevel()).thenReturn(10, 8);
        // first call during createOrder, second during cancel restore

        when(accountManagement.getMerchant(merchantId)).thenReturn(merchant);
        when(productCatalogue.getProduct("P001")).thenReturn(product);

        String orderId = orderApi.createOrder(order);
        assertEquals(80.0, merchant.getBalance(), 0.001);

        boolean cancelled = orderApi.cancelOrder(orderId);

        assertTrue(cancelled);
        assertEquals("CANCELLED", orderApi.getOrder(orderId).getStatus());
        assertEquals(0.0, merchant.getBalance(), 0.001);

        verify(productCatalogue).updateStock("P001", 8);
        verify(productCatalogue).updateStock("P001", 10);
    }

    // shows that if we try to cancel an order that is not pending it should return false and not update the status
    @Test
    void recordPayment_nullPayment_returnsFalse() {
        boolean result = orderApi.recordPayment("M001", null);
        assertFalse(result);
    }
}
