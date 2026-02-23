public interface IAccountManagement {

	/**
	 * 
	 * @param merchantID
	 */
	Merchant getMerchant(String merchantID);

	/**
	 * 
	 * @param merchant
	 */
	boolean CreateMerchant(Merchant merchant);

	/**
	 * 
	 * @param merchantID
	 * @param newLimit
	 */
	boolean UpdateCreditLimit(String merchantID, double newLimit);

	/**
	 * 
	 * @param merchantID
	 */
	double getMerchantBalance(String merchantID);

	/**
	 * 
	 * @param merchantID
	 * @param payment
	 */
	boolean ApplyPayment(String merchantID, Payment payment);

}