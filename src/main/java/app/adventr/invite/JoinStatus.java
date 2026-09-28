package app.adventr.invite;

/**
 * What opening an invite link means for the current user.
 */
public enum JoinStatus {

	/** Valid link, and the user is not a member yet: they may join. */
	CAN_JOIN,

	/** Valid link, and the user left the group earlier: joining reactivates the membership. */
	CAN_REJOIN,

	/** Valid link for a group the user is already an active member of. */
	ALREADY_MEMBER,

	/** The link's expiry has passed. */
	EXPIRED,

	/** No such link: mistyped, or replaced by a newer one (or the group was deleted). */
	INVALID;

	public boolean canJoin() {
		return this == CAN_JOIN || this == CAN_REJOIN;
	}

}
