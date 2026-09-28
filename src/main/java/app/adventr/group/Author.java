package app.adventr.group;

/**
 * The author of content (an adventure, comment, or reaction) as shown to group members.
 * {@code formerMember} is true when the user has left, was removed, or deleted their account.
 */
public record Author(long userId, String displayName, boolean formerMember) {

	/**
	 * "Alex", or "Alex (former member)".
	 */
	public String label() {
		return this.formerMember ? this.displayName + " (former member)" : this.displayName;
	}

}
