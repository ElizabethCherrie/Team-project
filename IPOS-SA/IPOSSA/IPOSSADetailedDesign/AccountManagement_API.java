package IPOSSA.IPOSSADetailedDesign;

public class AccountManagement_API implements IAccountManagement {

	/**
	 * 
	 * @param merchantID
	 */
	public Merchant getMerchant(String merchantID) {
		// TODO - implement AccountManagement_API.getMerchant
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchant
	 */
	public boolean CreateMerchant(Merchant merchant) {
		// TODO - implement AccountManagement_API.CreateMerchant
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchantID
	 * @param newLimit
	 */
	public boolean UpdateCreditLimit(String merchantID, double newLimit) {
		// TODO - implement AccountManagement_API.UpdateCreditLimit
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchantID
	 */
	public double getMerchantBalance(String merchantID) {
		// TODO - implement AccountManagement_API.getMerchantBalance
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchantID
	 * @param payment
	 */
	public boolean ApplyPayment(String merchantID, Payment payment) {
		// TODO - implement AccountManagement_API.ApplyPayment
		throw new UnsupportedOperationException();
	}

	public AccountManagement_API() {
		// TODO - implement AccountManagement_API.AccountManagement_API
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param admin
	 */
	public boolean createAdminAccount(Admin admin) {
		// TODO - implement AccountManagement_API.createAdminAccount
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param manager
	 */
	public boolean createManagerAccount(Manager manager) {
		// TODO - implement AccountManagement_API.createManagerAccount
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param merchantID
	 * @param plan
	 */
	public boolean updateDiscountPlan(String merchantID, DiscountPlan plan) {
		// TODO - implement AccountManagement_API.updateDiscountPlan
		throw new UnsupportedOperationException();
	}

	/**
	 * 
	 * @param accountID
	 * @param status
	 */
	public boolean changeAccountStatus(String accountID, String status) {
		// TODO - implement AccountManagement_API.changeAccountStatus
		throw new UnsupportedOperationException();
	}

}