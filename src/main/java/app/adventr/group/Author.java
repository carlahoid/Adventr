package app.adventr.group;

/**
 * The author of content (an adventure, comment, or reaction) as shown to group members.
 * {@code formerMember} is true when the user has left, was removed, or deleted their account.
 * {@code avatarVersion} is {@code null} without an avatar.
 */
public record Author(long userId, String displayName, boolean formerMember, String avatarVersion) {

	/**
	 * Whether the viewer may see the avatar image: only while the author is still a member of
	 * this group, i.e. shares it with the viewer. Otherwise the initials are shown.
	 */
	public boolean showsAvatar() {
		return this.avatarVersion != null && !this.formerMember;
	}

	/**
	 * "Alex", or "Alex (former member)".
	 */
	public String label() {
		return this.formerMember ? this.displayName + " (former member)" : this.displayName;
	}

}
