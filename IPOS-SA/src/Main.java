// Main.java - Updated main class to demonstrate the system
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println("InfoPharma Ordering System (IPOS-SA)");
        System.out.println("======================================\n");

        try {
            // Initialize subsystems
            System.out.println("Initializing IPOS-SA subsystems...");

            AccountManagement_API accountManagement = new AccountManagement_API();
            ProductCatalogue_API productCatalogue = new ProductCatalogue_API();


            OrderManagment_API orderManagement = new OrderManagment_API(
                    accountManagement, productCatalogue);

            Reporting_API reporting = new Reporting_API(
                    accountManagement, orderManagement, productCatalogue);

            System.out.println("✓ Account Management initialized");
            System.out.println("✓ Product Catalogue initialized");
            System.out.println("✓ Order Management initialized");
            System.out.println("✓ Reporting initialized\n");

            // Create a sample merchant
            System.out.println("Creating sample merchant...");
            Merchant merchant = new Merchant();
            merchant.setMerchantID("M001");
            merchant.setName("Cosymed Ltd.");
            merchant.setAddress("3, High Level Drive, Sydenham, SE26 3ET");
            merchant.setCreditLimit(10000.0);
            merchant.setBalance(0.0);
            merchant.setStatus("ACTIVE");

            if (accountManagement.CreateMerchant(merchant)) {
                System.out.println("✓ Merchant created successfully");
                System.out.println("  Name: " + merchant.getName());
                System.out.println("  Credit Limit: £" + merchant.getCreditLimit());
            } else {
                System.out.println("✗ Failed to create merchant");
            }
            System.out.println();

            // Create an order
            System.out.println("Creating sample order...");
            Order order = new Order();
            order.setMerchantID("M001");
            order.setOrderDate(new Date());

            // Create order items
            OrderItem[] items = new OrderItem[3];

            OrderItem item1 = new OrderItem();
            item1.setProductID("10000001"); // Paracetamol
            item1.setQuantity(100);
            item1.setUnitPrice(0.10);
            items[0] = item1;

            OrderItem item2 = new OrderItem();
            item2.setProductID("10000003"); // Analgin
            item2.setQuantity(20);
            item2.setUnitPrice(1.20);
            items[1] = item2;

            OrderItem item3 = new OrderItem();
            item3.setProductID("20000004"); // Iodine tincture
            item3.setQuantity(20);
            item3.setUnitPrice(0.30);
            items[2] = item3;

            order.setItems(items);

            try {
                String orderID = orderManagement.createOrder(order);
                System.out.println("✓ Order created successfully");
                System.out.println("  Order ID: " + orderID);

                double orderTotal = orderManagement.calculateOrderTotal(orderID);
                System.out.println("  Order Total: £" + String.format("%.2f", orderTotal));

                // Store the order ID for later use
                System.out.println("  Note: Order ID is " + orderID + " (use this for invoice)");
            } catch (Exception e) {
                System.out.println("✗ Failed to create order: " + e.getMessage());
            }
            System.out.println();

            // Raise invoice - use a more reliable approach
            System.out.println("Raising invoice...");
            try {
                // Get the most recent order instead of hardcoding "ORD1001"
                Order[] orders = orderManagement.orderHistory("M001");
                if (orders.length > 0) {
                    String latestOrderID = orders[orders.length - 1].getOrderID();
                    Invoice invoice = orderManagement.raiseInvoice(latestOrderID);

                    if (invoice != null) {
                        System.out.println("✓ Invoice created successfully");
                        System.out.println("  Invoice ID: " + invoice.getInvoiceID());
                        System.out.println("  Order ID: " + invoice.getOrderID());
                        System.out.println("  Amount: £" + String.format("%.2f", invoice.getTotalAmount()));
                        System.out.println("  Issue Date: " + invoice.getIssueDate());
                        System.out.println("  Due Date: " + invoice.getDueDate());
                    }
                } else {
                    System.out.println("No orders found to raise invoice for");
                }
            } catch (Exception e) {
                System.out.println("Note: Could not raise invoice: " + e.getMessage());
            }
            System.out.println();

            // Generate reports
            System.out.println("Generating reports...");

            Report stockReport = reporting.generateStockReport();
            System.out.println("✓ Stock Report generated");
            System.out.println("  Title: " + stockReport.getTitle());

            String contentPreview = stockReport.getContent();
            if (contentPreview.length() > 100) {
                contentPreview = contentPreview.substring(0, 100) + "...";
            }
            System.out.println("  Content preview: " + contentPreview);

            // Generate merchant report
            Report merchantReport = reporting.generateMerchantReport("M001");
            System.out.println("✓ Merchant Report generated");
            System.out.println("  Title: " + merchantReport.getTitle());

            System.out.println("\nSystem ready for use!\n");

            // Print summary
            System.out.println("======================================");
            System.out.println("SYSTEM SUMMARY");
            System.out.println("======================================");
            System.out.println("Merchant: Cosymed Ltd. (M001)");

            Merchant updatedMerchant = accountManagement.getMerchant("M001");
            if (updatedMerchant != null) {
                System.out.println("Current Balance: £" + String.format("%.2f", updatedMerchant.getBalance()));
                System.out.println("Credit Limit: £" + String.format("%.2f", updatedMerchant.getCreditLimit()));
                System.out.println("Status: " + updatedMerchant.getStatus());
            }

            Order[] merchantOrders = orderManagement.orderHistory("M001");
            System.out.println("Total Orders: " + merchantOrders.length);

            List<Invoice> merchantInvoices = orderManagement.getInvoicesForMerchant("M001");
            System.out.println("Total Invoices: " + merchantInvoices.size());

            System.out.println("======================================");

        } catch (Exception e) {
            System.err.println("Error initializing system: " + e.getMessage());
            e.printStackTrace();
        }
    }
}