package app.adventr.group;

import java.time.ZonedDateTime;

/**
 * An active member on the settings page. {@code you} marks the viewing user.
 */
public record MemberView(long userId, String displayName, Role role, ZonedDateTime joinedAt, boolean you) {

	public boolean isOwner() {
		return this.role == Role.OWNER;
	}

}
