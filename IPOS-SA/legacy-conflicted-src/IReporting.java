<<<<<<< HEAD
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

=======
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

>>>>>>> f9b28ea0475ec3893bbd47162daa158d6cb6949d
}