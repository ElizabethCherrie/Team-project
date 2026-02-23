public class OrderItem {

	private String productID;
	private int quantity;
	private double unitPrice;

	public String getProductID() {
		return this.productID;
	}

	/**
	 * 
	 * @param productID
	 */
	public void setProductID(String productID) {
		this.productID = productID;
	}

	public int getQuantity() {
		return this.quantity;
	}

	/**
	 * 
	 * @param quantity
	 */
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public double getUnitPrice() {
		return this.unitPrice;
	}

	/**
	 * 
	 * @param unitPrice
	 */
	public void setUnitPrice(double unitPrice) {
		this.unitPrice = unitPrice;
	}

}