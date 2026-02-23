public interface IReporting {

	/**
	 * 
	 * @param merchantID
	 */
	Report generateMerchantReport(String merchantID);

	Report generateStockReport();

	/**
	 * 
	 * @param merchantID
	 */
	Report generateOrderSummary(String merchantID);

	/**
	 * 
	 * @param merchantID
	 */
	Report generateSalesPerformanceReport(String merchantID);

}