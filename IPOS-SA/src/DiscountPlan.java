public class DiscountPlan {

	private String planID;
	private String description;
	private double discountRate;

	public String getPlanID() {
		return this.planID;
	}

	/**
	 * 
	 * @param planID
	 */
	public void setPlanID(String planID) {
		this.planID = planID;
	}

	public String getDescription() {
		return this.description;
	}

	/**
	 * 
	 * @param description
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	public double getDiscountRate() {
		return this.discountRate;
	}

	/**
	 * 
	 * @param discountRate
	 */
	public void setDiscountRate(double discountRate) {
		this.discountRate = discountRate;
	}

}