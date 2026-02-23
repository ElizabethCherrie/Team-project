package IPOSSA.IPOSSADetailedDesign;

public class OrderManagment_API implements IOrderManagement {

	/**
	 * 
	 * @param order
	 */
	public String createOrder(Order order) {
		// TODO - implement OrderManagment_API.createOrder
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param orderID
	 */
	public Order getOrder(String orderID) {
		// TODO - implement OrderManagment_API.getOrder
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param orderID
	 * @param status
	 */
	public boolean updateOrderStatus(String orderID, String status) {
		// TODO - implement OrderManagment_API.updateOrderStatus
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchantID
	 */
	public Order[] orderHistory(String merchantID) {
		// TODO - implement OrderManagment_API.orderHistory
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param orderID
	 */
	public double calculateOrderTotal(String orderID) {
		// TODO - implement OrderManagment_API.calculateOrderTotal
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param orderID
	 */
	public boolean cancelOrder(String orderID) {
		// TODO - implement OrderManagment_API.cancelOrder
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param orderID
	 */
	public Invoice raiseInvoice(String orderID) {
		// TODO - implement OrderManagment_API.raiseInvoice
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param orderID
	 * @param courier
	 * @param dispatchDate
	 * @param trackingNo
	 */
	public boolean updateDispatchInfo(String orderID, String courier, Date dispatchDate, String trackingNo) {
		// TODO - implement OrderManagment_API.updateDispatchInfo
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchantID
	 * @param payment
	 */
	public boolean recordPayment(String merchantID, Payment payment) {
		// TODO - implement OrderManagment_API.recordPayment
		throw new UnsupportedOperationException();
	}

	public OrderManagment_API() {
		// TODO - implement OrderManagment_API.OrderManagment_API
		throw new UnsupportedOperationException();
	}

}