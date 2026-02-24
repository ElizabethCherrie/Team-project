import java.util.Date;

public class Payment {

	private String paymentID;
	private String merchantID;
	private double amount;
	private Date date;



    public String getPaymentID() {
		return this.paymentID;
	}

	/**
	 * 
	 * @param paymentID
	 */
	public void setPaymentID(String paymentID) {
		this.paymentID = paymentID;
	}

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

	public Date getDate() {
		return this.date;
	}

	/**
	 * 
	 * @param date
	 */
	public void setDate(Date date) {
		this.date = date;
	}

	public void setAmount(Double amount) {
		this.amount = amount;
	}

	public double getAmount() {
		return this.amount;
	}
}