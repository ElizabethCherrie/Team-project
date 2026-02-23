public class Report {

	private String title;
	private Date generatedOn;
	private String content;

	public String getTitle() {
		return this.title;
	}

	/**
	 * 
	 * @param title
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	public Date getGeneratedOn() {
		return this.generatedOn;
	}

	/**
	 * 
	 * @param generatedOn
	 */
	public void setGeneratedOn(Date generatedOn) {
		this.generatedOn = generatedOn;
	}

	public String getContent() {
		return this.content;
	}

	/**
	 * 
	 * @param content
	 */
	public void setContent(String content) {
		this.content = content;
	}

}