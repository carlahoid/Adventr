package app.adventr.group;

import java.time.ZonedDateTime;

/**
 * An active member on the settings page. {@code you} marks the viewing user; {@code bio} and
 * {@code avatarVersion} are {@code null} when the member has none.
 */
public record MemberView(long userId, String displayName, String bio, String avatarVersion, Role role,
		ZonedDateTime joinedAt, boolean you) {

	public boolean isOwner() {
		return this.role == Role.OWNER;
	}

}
