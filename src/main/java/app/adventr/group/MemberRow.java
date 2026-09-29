package app.adventr.group;

import java.time.Instant;

/**
 * An active member as loaded for the settings page.
 */
public record MemberRow(long userId, String displayName, String bio, String avatarPath, Role role,
		Instant joinedAt) {
}
