package app.adventr.group;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps every group with exactly one owner when an account goes away. Not a group-scoped
 * service: it acts for the system, not for a member, so it does not use the membership guard.
 */
@Service
public class OwnerSuccessionService {

	private final MembershipRepository memberships;

	private final GroupDeletion deletion;

	private final Clock clock;

	public OwnerSuccessionService(MembershipRepository memberships, GroupDeletion deletion, Clock clock) {
		this.memberships = memberships;
		this.deletion = deletion;
		this.clock = clock;
	}

	/**
	 * Called when a user's account is deleted: in each group they own, the longest-standing
	 * other active member (earliest {@code joined_at}) becomes the Owner, or the group is deleted
	 * if nobody else is left. All of the user's memberships then end.
	 */
	@Transactional
	public void accountDeleted(long userId) {
		List<Membership> owned = this.memberships.findByUserIdAndRoleAndLeftAtIsNull(userId, Role.OWNER);
		for (Membership ownership : owned) {
			long groupId = ownership.getGroupId();
			Optional<Membership> successor = this.memberships
				.findFirstByGroupIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAscIdAsc(groupId, userId);
			if (successor.isEmpty()) {
				this.deletion.delete(groupId);
				continue;
			}
			// Demote first and flush: the database allows only one active owner per group.
			ownership.leave(this.clock.instant());
			this.memberships.saveAndFlush(ownership);
			successor.get().changeRole(Role.OWNER);
		}
		this.memberships.findByUserIdAndRoleAndLeftAtIsNull(userId, Role.MEMBER)
			.forEach((membership) -> membership.leave(this.clock.instant()));
	}

}
