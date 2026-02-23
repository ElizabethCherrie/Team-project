public interface IProductCatalogue {

	/**
	 * 
	 * @param productID
	 */
	Product getProduct(String productID);

	Product[] listProducts();

	/**
	 * 
	 * @param product
	 */
	boolean addProduct(Product product);

	/**
	 * 
	 * @param productID
	 * @param quantity
	 */
	boolean updateStock(String productID, int quantity);

	Product[] getLowStockProducts();

	/**
	 * 
	 * @param productID
	 * @param level
	 */
	boolean setMinimumStockLevel(String productID, int level);

	/**
	 * 
	 * @param itemID
	 * @param quantity
	 */
	void addStock(int itemID, int quantity);

}