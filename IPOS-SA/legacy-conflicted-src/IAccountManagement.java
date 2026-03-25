<<<<<<< HEAD
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

=======
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

>>>>>>> f9b28ea0475ec3893bbd47162daa158d6cb6949d
}