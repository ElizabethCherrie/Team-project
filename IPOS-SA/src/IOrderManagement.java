public interface IOrderManagement {

	/**
	 * 
	 * @param order
	 */
	String createOrder(Order order);

	/**
	 * 
	 * @param orderID
	 */
	Order getOrder(String orderID);

	/**
	 * 
	 * @param orderID
	 * @param status
	 */
	boolean updateOrderStatus(String orderID, String status);

	/**
	 * 
	 * @param merchantID
	 */
	Order[] listOrdersForMerchant(String merchantID);

	/**
	 * 
	 * @param orderID
	 */
	double calculateOrderTotal(String orderID);

	/**
	 * 
	 * @param orderID
	 */
	boolean cancelOrder(String orderID);

}