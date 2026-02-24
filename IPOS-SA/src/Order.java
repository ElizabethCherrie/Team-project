import java.util.Date;

public class Order {

	private String orderID;
	private String merchantID;
	private Date orderDate;
	private String status;
	private OrderItem[] items;

	public String getOrderID() {
		return this.orderID;
	}

	/**
	 * 
	 * @param orderID
	 */
	public void setOrderID(String orderID) {
		this.orderID = orderID;
	}

	public String getMerchantID() {
		return this.merchantID;
	}

	/**
	 * 
	 * @param merchantID
	 */
	public void setMerchantID(String merchantID) {
		this.merchantID = merchantID;
	}

	public Date getOrderDate() {
		return this.orderDate;
	}

	/**
	 * 
	 * @param orderDate
	 */
	public void setOrderDate(Date orderDate) {
		this.orderDate = orderDate;
	}

	public String getStatus() {
		return this.status;
	}

	/**
	 * 
	 * @param status
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	public OrderItem[] getItems() {
		return this.items;
	}

	/**
	 * 
	 * @param items
	 */
	public void setItems(OrderItem[] items) {
		this.items = items;
	}

}