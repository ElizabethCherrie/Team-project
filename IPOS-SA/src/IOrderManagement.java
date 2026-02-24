import java.util.Date;

public interface IOrderManagement {

	/**
	 * @param order
	 */
	String createOrder(Order order);

	/**
	 * @param orderID
	 */
	Order getOrder(String orderID);

	/**
	 * @param orderID
	 * @param status
	 */
	boolean updateOrderStatus(String orderID, String status);

	/**
	 * @param merchantID
	 */
	Order[] orderHistory(String merchantID);

	/**
	 * @param orderID
	 */
	double calculateOrderTotal(String orderID);

	/**
	 * @param orderID
	 */
	boolean cancelOrder(String orderID);

	/**
	 * @param orderID
	 */
	Invoice raiseInvoice(String orderID);

	boolean updateDispatchInfo(String orderID, String courier,
							   Date dispatchDate, String trackingNo);

	/**
	 * @param merchantID
	 * @param payment
	 */
	boolean recordPayment(String merchantID, Payment payment);

	/**
	 *
	 * @param orderID
	 * @param courier
	 * @param dispatchDate
	 * @param trackingNo
	 */
}