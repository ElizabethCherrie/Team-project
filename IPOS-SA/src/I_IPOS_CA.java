public interface I_IPOS_CA {

	/**
	 * 
	 * @param productID
	 * @param stockAmount
	 */
	int mutateStock(int productID, int stockAmount);

	/**
	 * 
	 * @param productID
	 */
	int StockQuantity(int productID);

}