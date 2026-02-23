public class Merchant {

	private String merchantID;
	private String name;
	private String address;
	private double creditLimit;
	private double balance;
	private String status;

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

	public String getAddress() {
		return this.address;
	}

	/**
	 * 
	 * @param address
	 */
	public void setAddress(String address) {
		this.address = address;
	}

	public double getCreditLimit() {
		return this.creditLimit;
	}

	/**
	 * 
	 * @param creditLimit
	 */
	public void setCreditLimit(double creditLimit) {
		this.creditLimit = creditLimit;
	}

	public double getBalance() {
		return this.balance;
	}

	/**
	 * 
	 * @param balance
	 */
	public void setBalance(double balance) {
		this.balance = balance;
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

}