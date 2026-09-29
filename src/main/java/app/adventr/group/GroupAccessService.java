package app.adventr.group;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place where group membership is enforced. Every group-scoped service method starts
 * with {@link #requireMember} or {@link #requireOwner}; controllers never check membership
 * themselves (enforced by {@code ArchitectureTests}).
 */
@Service
public class GroupAccessService {

	private final MembershipRepository memberships;

	public GroupAccessService(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	/**
	 * Returns the user's active membership in the group.
	 * @throws GroupAccessDeniedException (404) if the user is not, or no longer, a member, or
	 * the group does not exist; the two cases are indistinguishable on purpose
	 */
	@Transactional(readOnly = true)
	public Membership requireMember(long userId, long groupId) {
		return this.memberships.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)
			.orElseThrow(() -> new GroupAccessDeniedException(groupId));
	}

	/**
	 * Returns the user's active membership if they are the group's Owner.
	 * @throws GroupAccessDeniedException (404) if the user is not a member
	 * @throws ForbiddenActionException (403) if the user is a member but not the Owner
	 */
	@Transactional(readOnly = true)
	public Membership requireOwner(long userId, long groupId) {
		Membership membership = requireMember(userId, groupId);
		if (!membership.isOwner()) {
			throw new ForbiddenActionException("Only the group owner can do this");
		}
		return membership;
	}

	/**
	 * Allows a user to see another user's profile data that is not tied to one group (their
	 * avatar): the viewer is that user, or both are currently active members of some group.
	 * @throws GroupAccessDeniedException (404) otherwise, so that user ids cannot be probed
	 */
	@Transactional(readOnly = true)
	public void requireSharedGroup(long viewerId, long userId) {
		if (viewerId != userId && !this.memberships.shareActiveGroup(viewerId, userId)) {
			throw new GroupAccessDeniedException("User " + viewerId + " shares no group with user " + userId);
		}
	}

}
