// Reporting_API.java
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class Reporting_API implements IReporting {

	private AccountManagement_API accountManagement;
	private OrderManagment_API orderManagement;
	private ProductCatalogue_API productCatalogue;

	public Reporting_API(AccountManagement_API accountManagement,
						 OrderManagment_API orderManagement,
						 ProductCatalogue_API productCatalogue) {
		this.accountManagement = accountManagement;
		this.orderManagement = orderManagement;
		this.productCatalogue = productCatalogue;
	}

	@Override
	public Report generateMerchantReport(String merchantID) {
		if (merchantID == null) {
			throw new IllegalArgumentException("Merchant ID cannot be null");
		}

		Merchant merchant = accountManagement.getMerchant(merchantID);
		if (merchant == null) {
			throw new IllegalArgumentException("Merchant not found: " + merchantID);
		}

		StringBuilder content = new StringBuilder();
		content.append("MERCHANT REPORT\n");
		content.append("================\n\n");
		content.append("Merchant ID: ").append(merchant.getMerchantID()).append("\n");
		content.append("Name: ").append(merchant.getName()).append("\n");
		content.append("Address: ").append(merchant.getAddress()).append("\n");
		content.append("Credit Limit: £").append(String.format("%.2f", merchant.getCreditLimit())).append("\n");
		content.append("Current Balance: £").append(String.format("%.2f", merchant.getBalance())).append("\n");
		content.append("Status: ").append(merchant.getStatus()).append("\n\n");

		// Add order history
		content.append("ORDER HISTORY:\n");
		content.append("--------------\n");
		Order[] orders = orderManagement.orderHistory(merchantID);
		if (orders.length == 0) {
			content.append("No orders found.\n");
		} else {
			double totalOrderValue = 0;
			for (Order order : orders) {
				content.append("Order ID: ").append(order.getOrderID()).append("\n");
				content.append("  Date: ").append(order.getOrderDate()).append("\n");
				content.append("  Status: ").append(order.getStatus()).append("\n");
				double orderTotal = orderManagement.calculateOrderTotal(order.getOrderID());
				content.append("  Total: £").append(String.format("%.2f", orderTotal)).append("\n");
				totalOrderValue += orderTotal;
			}
			content.append("\nTotal Value of All Orders: £").append(String.format("%.2f", totalOrderValue)).append("\n");
		}

		// Add payment history
		content.append("\nPAYMENT HISTORY:\n");
		content.append("----------------\n");
		List<Payment> payments = orderManagement.getPaymentsForMerchant(merchantID);
		if (payments.isEmpty()) {
			content.append("No payments recorded.\n");
		} else {
			double totalPayments = 0;
			for (Payment payment : payments) {
				content.append("Payment ID: ").append(payment.getPaymentID()).append("\n");
				content.append("  Date: ").append(payment.getDate()).append("\n");
				content.append("  Amount: £").append(String.format("%.2f", payment.getAmount())).append("\n");
				totalPayments += payment.getAmount();
			}
			content.append("\nTotal Payments: £").append(String.format("%.2f", totalPayments)).append("\n");
		}

		Report report = new Report();
		report.setTitle("Merchant Report - " + merchant.getName());
		report.setGeneratedOn(new Date());
		report.setContent(content.toString());

		return report;
	}

	@Override
	public Report generateStockReport() {
		StringBuilder content = new StringBuilder();
		content.append("STOCK REPORT\n");
		content.append("============\n\n");

		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		content.append("Generated: ").append(dateFormat.format(new Date())).append("\n\n");

		Product[] products = productCatalogue.listProducts();

		// Sort by product ID
		Arrays.sort(products, (p1, p2) -> p1.getProductID().compareTo(p2.getProductID()));

		content.append(String.format("%-10s %-30s %-10s %-15s %-15s %-15s\n",
				"Item ID", "Description", "Price", "Stock Level", "Min Stock", "Status"));
		content.append(String.format("%-10s %-30s %-10s %-15s %-15s %-15s\n",
				"-------", "-----------", "-----", "-----------", "---------", "------"));

		int totalItems = 0;
		int lowStockCount = 0;
		double totalStockValue = 0;

		for (Product product : products) {
			String status = product.getStockLevel() < product.getMinimumStockLevel() ?
					"LOW STOCK" : "OK";
			if ("LOW STOCK".equals(status)) lowStockCount++;

			content.append(String.format("%-10s %-30s £%-9.2f %-15d %-15d %-15s\n",
					product.getProductID(),
					truncate(product.getName(), 28),
					product.getPrice(),
					product.getStockLevel(),
					product.getMinimumStockLevel(),
					status));

			totalItems++;
			totalStockValue += product.getPrice() * product.getStockLevel();
		}

		content.append("\n");
		content.append("SUMMARY:\n");
		content.append("--------\n");
		content.append("Total Products: ").append(totalItems).append("\n");
		content.append("Low Stock Items: ").append(lowStockCount).append("\n");
		content.append("Total Stock Value: £").append(String.format("%.2f", totalStockValue)).append("\n");

		// Add recommended orders for low stock items
		if (lowStockCount > 0) {
			content.append("\nRECOMMENDED ORDERS (Low Stock Items):\n");
			content.append("-------------------------------------\n");
			content.append(String.format("%-10s %-30s %-15s %-20s\n",
					"Item ID", "Description", "Current Stock", "Recommended Order"));

			for (Product product : products) {
				if (product.getStockLevel() < product.getMinimumStockLevel()) {
					int recommendedOrder = (int)(product.getMinimumStockLevel() * 1.1) - product.getStockLevel();
					content.append(String.format("%-10s %-30s %-15d %-20d\n",
							product.getProductID(),
							truncate(product.getName(), 28),
							product.getStockLevel(),
							Math.max(0, recommendedOrder)));
				}
			}
		}

		Report report = new Report();
		report.setTitle("Stock Report");
		report.setGeneratedOn(new Date());
		report.setContent(content.toString());

		return report;
	}

	@Override
	public Report generateOrderSummary(String merchantID) {
		if (merchantID == null) {
			throw new IllegalArgumentException("Merchant ID cannot be null");
		}

		Merchant merchant = accountManagement.getMerchant(merchantID);
		if (merchant == null) {
			throw new IllegalArgumentException("Merchant not found: " + merchantID);
		}

		StringBuilder content = new StringBuilder();
		content.append("ORDER SUMMARY\n");
		content.append("=============\n\n");

		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		content.append("Merchant: ").append(merchant.getName()).append("\n");
		content.append("Account: ").append(merchant.getMerchantID()).append("\n");
		content.append("Generated: ").append(dateFormat.format(new Date())).append("\n\n");

		content.append("CONTACT DETAILS:\n");
		content.append("----------------\n");
		content.append(merchant.getAddress()).append("\n\n");

		content.append("ORDER HISTORY SUMMARY:\n");
		content.append("----------------------\n");

		Order[] orders = orderManagement.orderHistory(merchantID);

		// Filter orders for current month
		Calendar cal = Calendar.getInstance();
		int currentMonth = cal.get(Calendar.MONTH);
		int currentYear = cal.get(Calendar.YEAR);

		List<Order> currentMonthOrders = Arrays.stream(orders)
				.filter(o -> {
					cal.setTime(o.getOrderDate());
					return cal.get(Calendar.MONTH) == currentMonth &&
							cal.get(Calendar.YEAR) == currentYear;
				})
				.collect(Collectors.toList());

		content.append(String.format("%-10s %-12s %-12s %-12s %-15s\n",
				"Order ID", "Date", "Amount", "Status", "Payment Status"));

		double totalAmount = 0;
		int pendingCount = 0;

		for (Order order : currentMonthOrders) {
			double amount = orderManagement.calculateOrderTotal(order.getOrderID());
			String paymentStatus = getPaymentStatus(order, merchant);

			content.append(String.format("%-10s %-12s £%-11.2f %-12s %-15s\n",
					order.getOrderID(),
					dateFormat.format(order.getOrderDate()),
					amount,
					order.getStatus(),
					paymentStatus));

			totalAmount += amount;
			if ("PENDING".equals(paymentStatus)) pendingCount++;
		}

		content.append("\n");
		content.append(String.format("%-10s %-12s £%-11.2f\n",
				"TOTAL:", "", totalAmount));
		content.append("Pending Payments: ").append(pendingCount).append("\n");

		Report report = new Report();
		report.setTitle("Order Summary - " + merchant.getName());
		report.setGeneratedOn(new Date());
		report.setContent(content.toString());

		return report;
	}

	private String getPaymentStatus(Order order, Merchant merchant) {
		// Simplified payment status logic
		double orderTotal = orderManagement.calculateOrderTotal(order.getOrderID());
		if ("DISPATCHED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())) {
			// Check if paid based on merchant balance
			if (merchant.getBalance() <= 0) {
				return "PAID";
			} else {
				return "PENDING";
			}
		}
		return "NOT DUE";
	}

	@Override
	public Report generateSalesPerformanceReport(String merchantID) {
		if (merchantID == null) {
			throw new IllegalArgumentException("Merchant ID cannot be null");
		}

		Merchant merchant = accountManagement.getMerchant(merchantID);
		if (merchant == null) {
			throw new IllegalArgumentException("Merchant not found: " + merchantID);
		}

		StringBuilder content = new StringBuilder();
		content.append("SALES PERFORMANCE REPORT\n");
		content.append("========================\n\n");

		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		content.append("Merchant: ").append(merchant.getName()).append("\n");
		content.append("Generated: ").append(dateFormat.format(new Date())).append("\n\n");

		Order[] orders = orderManagement.orderHistory(merchantID);

		// Group orders by month
		Map<String, List<Order>> ordersByMonth = new HashMap<>();
		SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy");

		for (Order order : orders) {
			String month = monthFormat.format(order.getOrderDate());
			ordersByMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(order);
		}

		content.append(String.format("%-15s %-15s %-15s %-15s\n",
				"Month", "Orders", "Total Sales", "Avg Order Value"));

		List<String> months = new ArrayList<>(ordersByMonth.keySet());
		Collections.sort(months);

		double totalSales = 0;
		int totalOrders = 0;

		for (String month : months) {
			List<Order> monthOrders = ordersByMonth.get(month);
			double monthTotal = 0;
			for (Order order : monthOrders) {
				monthTotal += orderManagement.calculateOrderTotal(order.getOrderID());
			}
			double avgOrder = monthOrders.isEmpty() ? 0 : monthTotal / monthOrders.size();

			content.append(String.format("%-15s %-15d £%-14.2f £%-14.2f\n",
					month, monthOrders.size(), monthTotal, avgOrder));

			totalSales += monthTotal;
			totalOrders += monthOrders.size();
		}

		content.append("\n");
		content.append("TOTALS:\n");
		content.append("-------\n");
		content.append("Total Orders: ").append(totalOrders).append("\n");
		content.append("Total Sales: £").append(String.format("%.2f", totalSales)).append("\n");
		content.append("Average Order Value: £").append(
				totalOrders == 0 ? "0.00" : String.format("%.2f", totalSales / totalOrders)).append("\n");

		Report report = new Report();
		report.setTitle("Sales Performance - " + merchant.getName());
		report.setGeneratedOn(new Date());
		report.setContent(content.toString());

		return report;
	}

	// Additional reports required for IPOS-SA
	public Report generateOverduePaymentsReport() {
		StringBuilder content = new StringBuilder();
		content.append("OVERDUE PAYMENTS REPORT\n");
		content.append("=======================\n\n");

		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		content.append("Generated: ").append(dateFormat.format(new Date())).append("\n\n");

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, -30); // 30 days overdue threshold

		List<Merchant> allMerchants = accountManagement.getAllMerchants();
		List<Merchant> overdueMerchants = new ArrayList<>();

		for (Merchant merchant : allMerchants) {
			if (merchant.getBalance() > 0) {
				// Check if any invoices are overdue
				List<Invoice> invoices = orderManagement.getInvoicesForMerchant(
						merchant.getMerchantID());
				for (Invoice invoice : invoices) {
					if (invoice.getDueDate().before(new Date()) &&
							!"PAID".equals(invoice.getStatus())) {
						overdueMerchants.add(merchant);
						break;
					}
				}
			}
		}

		if (overdueMerchants.isEmpty()) {
			content.append("No overdue payments found.\n");
		} else {
			content.append(String.format("%-15s %-30s %-15s %-15s\n",
					"Merchant ID", "Name", "Balance", "Status"));

			for (Merchant merchant : overdueMerchants) {
				content.append(String.format("%-15s %-30s £%-14.2f %-15s\n",
						merchant.getMerchantID(),
						truncate(merchant.getName(), 28),
						merchant.getBalance(),
						merchant.getStatus()));
			}
		}

		Report report = new Report();
		report.setTitle("Overdue Payments Report");
		report.setGeneratedOn(new Date());
		report.setContent(content.toString());

		return report;
	}

	public Report generateTurnoverReport(DateRange period) {
		StringBuilder content = new StringBuilder();
		content.append("TURNOVER REPORT\n");
		content.append("===============\n\n");

		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
		content.append("Period: ").append(dateFormat.format(period.getStart()))
				.append(" to ").append(dateFormat.format(period.getEnd())).append("\n\n");

		// Get all orders within period
		List<Order> ordersInPeriod = new ArrayList<>();
		Product[] allProducts = productCatalogue.listProducts();

		// Calculate sales
		Map<String, Integer> quantitySold = new HashMap<>();
		Map<String, Double> revenueByProduct = new HashMap<>();
		double totalRevenue = 0;
		int totalItemsSold = 0;

		for (Merchant merchant : accountManagement.getAllMerchants()) {
			Order[] orders = orderManagement.orderHistory(merchant.getMerchantID());
			for (Order order : orders) {
				if (order.getOrderDate().after(period.getStart()) &&
						order.getOrderDate().before(period.getEnd()) &&
						!"CANCELLED".equals(order.getStatus())) {

					ordersInPeriod.add(order);

					// Calculate items sold
					for (OrderItem item : order.getItems()) {
						String productID = item.getProductID();
						int qty = item.getQuantity();
						double itemRevenue = qty * item.getUnitPrice();

						quantitySold.put(productID,
								quantitySold.getOrDefault(productID, 0) + qty);
						revenueByProduct.put(productID,
								revenueByProduct.getOrDefault(productID, 0.0) + itemRevenue);

						totalItemsSold += qty;
						totalRevenue += itemRevenue;
					}
				}
			}
		}

		content.append(String.format("%-10s %-30s %-15s %-15s\n",
				"Item ID", "Description", "Quantity Sold", "Revenue"));

		for (Product product : allProducts) {
			int qty = quantitySold.getOrDefault(product.getProductID(), 0);
			double revenue = revenueByProduct.getOrDefault(product.getProductID(), 0.0);

			if (qty > 0) {
				content.append(String.format("%-10s %-30s %-15d £%-14.2f\n",
						product.getProductID(),
						truncate(product.getName(), 28),
						qty,
						revenue));
			}
		}

		content.append("\n");
		content.append("SUMMARY:\n");
		content.append("--------\n");
		content.append("Total Orders: ").append(ordersInPeriod.size()).append("\n");
		content.append("Total Items Sold: ").append(totalItemsSold).append("\n");
		content.append("Total Revenue: £").append(String.format("%.2f", totalRevenue)).append("\n");

		Report report = new Report();
		report.setTitle("Turnover Report");
		report.setGeneratedOn(new Date());
		report.setContent(content.toString());

		return report;
	}

	private String truncate(String str, int maxLength) {
		if (str == null) return "";
		if (str.length() <= maxLength) return str;
		return str.substring(0, maxLength - 3) + "...";
	}
}