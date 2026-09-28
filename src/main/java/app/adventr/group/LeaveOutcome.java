package app.adventr.group;

/**
 * What happened when a member left a group.
 */
public enum LeaveOutcome {

	/** The membership became inactive; the group and its content remain. */
	LEFT,

	/** The sole Owner left, so the group and all of its content were deleted. */
	GROUP_DELETED

}
