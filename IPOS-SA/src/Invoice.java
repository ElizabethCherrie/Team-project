public class Invoice {

	private String invoiceID;
	private String orderID;
	private String merchantID;
	private Date issueDate;
	private Date dueDate;
	private double totalAmount;
	private String status;

	public String getInvoiceID() {
		return this.invoiceID;
	}

	/**
	 * 
	 * @param invoiceID
	 */
	public void setInvoiceID(String invoiceID) {
		this.invoiceID = invoiceID;
	}

	public String getOrderID() {
		return this.orderID;
	}

	/**
	 * 
	 * @param orderID
	 */
	public void setOrderID(String orderID) {
		this.orderID = orderID;
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

	public Date getIssueDate() {
		return this.issueDate;
	}

	/**
	 * 
	 * @param issueDate
	 */
	public void setIssueDate(Date issueDate) {
		this.issueDate = issueDate;
	}

	public Date getDueDate() {
		return this.dueDate;
	}

	/**
	 * 
	 * @param dueDate
	 */
	public void setDueDate(Date dueDate) {
		this.dueDate = dueDate;
	}

	public double getTotalAmount() {
		return this.totalAmount;
	}

	/**
	 * 
	 * @param totalAmount
	 */
	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
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