package app.adventr.group;

/**
 * A group as seen by one of its members.
 */
public record GroupView(long id, String name, Role myRole, long memberCount) {

	public boolean isOwner() {
		return this.myRole == Role.OWNER;
	}

}
