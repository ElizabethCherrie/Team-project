public class Manager {

	private String managerID;
	private String name;
	private String email;
	private String region;

	public String getManagerID() {
		return this.managerID;
	}

	/**
	 * 
	 * @param managerID
	 */
	public void setManagerID(String managerID) {
		this.managerID = managerID;
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

	public String getEmail() {
		return this.email;
	}

	/**
	 * 
	 * @param email
	 */
	public void setEmail(String email) {
		this.email = email;
	}

	public String getRegion() {
		return this.region;
	}

	/**
	 * 
	 * @param region
	 */
	public void setRegion(String region) {
		this.region = region;
	}

}