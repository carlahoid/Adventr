package app.adventr.invite;

/**
 * The result of looking at, or using, an invite link. {@code groupId} and {@code groupName}
 * are set only for a valid link, so an invalid or expired link reveals nothing about a group.
 */
public record JoinPreview(JoinStatus status, Long groupId, String groupName) {

	static JoinPreview of(JoinStatus status) {
		return new JoinPreview(status, null, null);
	}

}
