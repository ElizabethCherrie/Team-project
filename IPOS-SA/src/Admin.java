public class Admin {

	private String adminID;
	private String name;
	private String email;
	private String role;

	public String getAdminID() {
		return this.adminID;
	}

	/**
	 * 
	 * @param adminID
	 */
	public void setAdminID(String adminID) {
		this.adminID = adminID;
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

	public String getRole() {
		return this.role;
	}

	/**
	 * 
	 * @param role
	 */
	public void setRole(String role) {
		this.role = role;
	}

}