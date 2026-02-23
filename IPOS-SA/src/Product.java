public class Product {

	private String productID;
	private String name;
	private double price;
	private int stockLevel;
	private int minimumStockLevel;

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

	public String getName() {
		return this.name;
	}

	/**
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

	public double getPrice() {
		return this.price;
	}

	/**
	 * 
	 * @param price
	 */
	public void setPrice(double price) {
		this.price = price;
	}

	public int getStockLevel() {
		return this.stockLevel;
	}

	/**
	 * 
	 * @param stockLevel
	 */
	public void setStockLevel(int stockLevel) {
		this.stockLevel = stockLevel;
	}

	public int getMinimumStockLevel() {
		return this.minimumStockLevel;
	}

	/**
	 * 
	 * @param minimumStockLevel
	 */
	public void setMinimumStockLevel(int minimumStockLevel) {
		this.minimumStockLevel = minimumStockLevel;
	}

}