// OrderManagment_API.java (fixed class name typo)
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Date;

public class OrderManagment_API implements IOrderManagement {

	private Map<String, Order> orders = new ConcurrentHashMap<>();
	private Map<String, Invoice> invoices = new ConcurrentHashMap<>();
	private Map<String, List<Order>> merchantOrders = new ConcurrentHashMap<>();
	private Map<String, List<Payment>> merchantPayments = new ConcurrentHashMap<>();
	private AccountManagement_API accountManagement;
	private ProductCatalogue_API productCatalogue;
	private AtomicInteger orderCounter = new AtomicInteger(1000);
	private AtomicInteger invoiceCounter = new AtomicInteger(5000);

	public OrderManagment_API(AccountManagement_API accountManagement,
							  ProductCatalogue_API productCatalogue) {
		this.accountManagement = accountManagement;
		this.productCatalogue = productCatalogue;
	}

	@Override
	public String createOrder(Order order) {
		if (order == null) {
			throw new IllegalArgumentException("Order cannot be null");
		}

		// Validate merchant
		Merchant merchant = accountManagement.getMerchant(order.getMerchantID());
		if (merchant == null) {
			throw new IllegalArgumentException("Merchant not found: " + order.getMerchantID());
		}

		// Check if merchant account is active
		if (!"ACTIVE".equals(merchant.getStatus())) {
			throw new IllegalStateException("Merchant account is not active. Status: " +
					merchant.getStatus());
		}

		// Check credit limit
		double orderTotal = calculateOrderTotalFromItems(order.getItems());
		if (merchant.getBalance() + orderTotal > merchant.getCreditLimit()) {
			throw new IllegalStateException("Order would exceed credit limit. " +
					"Current balance: " + merchant.getBalance() +
					", Order total: " + orderTotal +
					", Credit limit: " + merchant.getCreditLimit());
		}

		// Generate order ID
		String orderID = "ORD" + orderCounter.incrementAndGet();
		order.setOrderID(orderID);

		if (order.getOrderDate() == null) {
			order.setOrderDate(new Date());
		}

		if (order.getStatus() == null) {
			order.setStatus("PENDING");
		}

		// Store order
		orders.put(orderID, order);

		// Add to merchant's order history
		merchantOrders.computeIfAbsent(order.getMerchantID(),
				k -> new ArrayList<>()).add(order);

		// Update stock levels (reduce by ordered quantities)
		updateStockForOrder(order.getItems());

		// Update merchant balance (increase debt)
		merchant.setBalance(merchant.getBalance() + orderTotal);

		return orderID;
	}

	private double calculateOrderTotalFromItems(OrderItem[] items) {
		if (items == null) return 0.0;

		double total = 0.0;
		for (OrderItem item : items) {
			total += item.getQuantity() * item.getUnitPrice();
		}
		return total;
	}

	private void updateStockForOrder(OrderItem[] items) {
		if (items == null) return;

		for (OrderItem item : items) {
			Product product = productCatalogue.getProduct(item.getProductID());
			if (product != null) {
				int newStock = product.getStockLevel() - item.getQuantity();
				product.setStockLevel(Math.max(0, newStock));
				productCatalogue.updateStock(item.getProductID(), newStock);
			}
		}
	}

	@Override
	public Order getOrder(String orderID) {
		if (orderID == null) {
			throw new IllegalArgumentException("Order ID cannot be null");
		}
		return orders.get(orderID);
	}

	@Override
	public boolean updateOrderStatus(String orderID, String status) {
		if (orderID == null || status == null) {
			return false;
		}

		Order order = orders.get(orderID);
		if (order == null) {
			return false;
		}

		order.setStatus(status);
		return true;
	}

	@Override
	public Order[] orderHistory(String merchantID) {
		if (merchantID == null) {
			return new Order[0];
		}

		List<Order> merchantOrderList = merchantOrders.get(merchantID);
		if (merchantOrderList == null) {
			return new Order[0];
		}

		return merchantOrderList.toArray(new Order[0]);
	}

	@Override
	public double calculateOrderTotal(String orderID) {
		if (orderID == null) {
			return 0.0;
		}

		Order order = orders.get(orderID);
		if (order == null) {
			return 0.0;
		}

		return calculateOrderTotalFromItems(order.getItems());
	}

	@Override
	public boolean cancelOrder(String orderID) {
		if (orderID == null) {
			return false;
		}

		Order order = orders.get(orderID);
		if (order == null) {
			return false;
		}

		// Can only cancel pending orders
		if (!"PENDING".equals(order.getStatus())) {
			return false;
		}

		order.setStatus("CANCELLED");

		// Restore stock
		restoreStockForCancelledOrder(order.getItems());

		// Update merchant balance
		Merchant merchant = accountManagement.getMerchant(order.getMerchantID());
		if (merchant != null) {
			double orderTotal = calculateOrderTotalFromItems(order.getItems());
			merchant.setBalance(merchant.getBalance() - orderTotal);
		}

		return true;
	}

	private void restoreStockForCancelledOrder(OrderItem[] items) {
		if (items == null) return;

		for (OrderItem item : items) {
			Product product = productCatalogue.getProduct(item.getProductID());
			if (product != null) {
				int newStock = product.getStockLevel() + item.getQuantity();
				product.setStockLevel(newStock);
				productCatalogue.updateStock(item.getProductID(), newStock);
			}
		}
	}

	@Override
	public Invoice raiseInvoice(String orderID) {
		if (orderID == null) {
			throw new IllegalArgumentException("Order ID cannot be null");
		}

		Order order = orders.get(orderID);
		if (order == null) {
			throw new IllegalArgumentException("Order not found: " + orderID);
		}

		// Check if invoice already exists
		for (Invoice inv : invoices.values()) {
			if (orderID.equals(inv.getOrderID())) {
				return inv; // Return existing invoice
			}
		}

		// Create new invoice
		Invoice invoice = new Invoice();
		String invoiceID = "INV" + invoiceCounter.incrementAndGet();
		invoice.setInvoiceID(invoiceID);
		invoice.setOrderID(orderID);
		invoice.setMerchantID(order.getMerchantID());
		invoice.setIssueDate(new Date());

		// Calculate due date (30 days from issue)
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 30);
		invoice.setDueDate(cal.getTime());

		invoice.setTotalAmount(calculateOrderTotal(orderID));
		invoice.setStatus("ISSUED");

		invoices.put(invoiceID, invoice);

		return invoice;
	}

	@Override
	public boolean updateDispatchInfo(String orderID, String courier, Date dispatchDate, String trackingNo) {
		return false;
	}


	@Override
	public boolean recordPayment(String merchantID, Payment payment) {
		if (merchantID == null || payment == null) {
			return false;
		}

		// Store payment
		merchantPayments.computeIfAbsent(merchantID, k -> new ArrayList<>()).add(payment);

		// Apply payment to merchant account
		return accountManagement.ApplyPayment(merchantID, payment);
	}

	public List<Invoice> getInvoicesForMerchant(String merchantID) {
		List<Invoice> result = new ArrayList<>();
		for (Invoice invoice : invoices.values()) {
			if (merchantID.equals(invoice.getMerchantID())) {
				result.add(invoice);
			}
		}
		return result;
	}

	public List<Payment> getPaymentsForMerchant(String merchantID) {
		return merchantPayments.getOrDefault(merchantID, new ArrayList<>());
	}

	public Invoice getInvoice(String invoiceID) {
		return invoices.get(invoiceID);
	}
}