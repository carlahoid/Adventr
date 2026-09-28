package app.adventr.group;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Groups and their memberships: create, view, rename, leave, remove, transfer, delete.
 */
@Service
@GroupScopedService
public class GroupService {

	/** Longest group name, in characters. */
	public static final int MAX_NAME_LENGTH = 80;

	private final GroupRepository groups;

	private final MembershipRepository memberships;

	private final GroupAccessService access;

	private final GroupDeletion deletion;

	private final Clock clock;

	public GroupService(GroupRepository groups, MembershipRepository memberships, GroupAccessService access,
			GroupDeletion deletion, Clock clock) {
		this.groups = groups;
		this.memberships = memberships;
		this.access = access;
		this.deletion = deletion;
		this.clock = clock;
	}

	/**
	 * Creates a group with the user as its Owner and returns the new group's id.
	 */
	@GuardExempt("Creates a new group; the creator becomes its Owner")
	@Transactional
	public long create(long userId, String name) {
		Group group = this.groups.save(new Group(validName(name)));
		this.memberships.save(new Membership(group.getId(), userId, Role.OWNER, this.clock.instant()));
		return group.getId();
	}

	/**
	 * The user's active groups, for "My groups" and the header switcher.
	 */
	@GuardExempt("Lists only the caller's own active memberships")
	@Transactional(readOnly = true)
	public List<GroupSummary> myGroups(long userId) {
		return this.groups.findSummariesForMember(userId);
	}

	@Transactional(readOnly = true)
	public GroupView view(long userId, long groupId) {
		Membership membership = this.access.requireMember(userId, groupId);
		return toView(membership);
	}

	/**
	 * The active members, Owner first, then by join date.
	 */
	@Transactional(readOnly = true)
	public List<MemberView> members(long userId, long groupId) {
		this.access.requireMember(userId, groupId);
		return this.memberships.findActiveMembers(groupId)
			.stream()
			.map((row) -> new MemberView(row.userId(), row.displayName(), row.role(),
					row.joinedAt().atZone(this.clock.getZone()), row.userId() == userId))
			.toList();
	}

	@Transactional
	public void rename(long userId, long groupId, String name) {
		this.access.requireOwner(userId, groupId);
		group(groupId).rename(validName(name));
	}

	/**
	 * Ends the user's membership. The Owner may leave only as the last active member, which
	 * deletes the group.
	 * @throws GroupRuleException if the Owner leaves while other members remain
	 */
	@Transactional
	public LeaveOutcome leave(long userId, long groupId) {
		Membership membership = this.access.requireMember(userId, groupId);
		if (membership.isOwner()) {
			if (this.memberships.countByGroupIdAndLeftAtIsNull(groupId) > 1) {
				throw new GroupRuleException(
						"You're the owner. Transfer ownership to another member before leaving the group.");
			}
			this.deletion.delete(groupId);
			return LeaveOutcome.GROUP_DELETED;
		}
		membership.leave(this.clock.instant());
		return LeaveOutcome.LEFT;
	}

	/**
	 * The Owner removes another member. Their content stays, attributed to a former member.
	 */
	@Transactional
	public void removeMember(long userId, long groupId, long memberUserId) {
		this.access.requireOwner(userId, groupId);
		if (memberUserId == userId) {
			throw new GroupRuleException("You can't remove yourself. Use \"Leave group\" instead.");
		}
		activeMember(groupId, memberUserId).leave(this.clock.instant());
	}

	/**
	 * Makes another active member the Owner; the previous Owner becomes a Member.
	 */
	@Transactional
	public void transferOwnership(long userId, long groupId, long newOwnerUserId) {
		Membership owner = this.access.requireOwner(userId, groupId);
		if (newOwnerUserId == userId) {
			throw new GroupRuleException("You're already the owner.");
		}
		Membership newOwner = activeMember(groupId, newOwnerUserId);
		// Demote first and flush: the database allows only one active owner per group.
		owner.changeRole(Role.MEMBER);
		this.memberships.saveAndFlush(owner);
		newOwner.changeRole(Role.OWNER);
	}

	/**
	 * Deletes the group and all of its content, after the Owner typed the group name.
	 * @throws GroupRuleException if the confirmation does not match the name
	 */
	@Transactional
	public void delete(long userId, long groupId, String confirmationName) {
		this.access.requireOwner(userId, groupId);
		String name = group(groupId).getName();
		if (confirmationName == null || !name.equals(confirmationName.trim())) {
			throw new GroupRuleException("The name you typed doesn't match the group name.");
		}
		this.deletion.delete(groupId);
	}

	private GroupView toView(Membership membership) {
		long groupId = membership.getGroupId();
		return new GroupView(groupId, group(groupId).getName(), membership.getRole(),
				this.memberships.countByGroupIdAndLeftAtIsNull(groupId));
	}

	private Group group(long groupId) {
		return this.groups.findById(groupId).orElseThrow(() -> new GroupAccessDeniedException(groupId));
	}

	private Membership activeMember(long groupId, long userId) {
		return this.memberships.findByGroupIdAndUserIdAndLeftAtIsNull(groupId, userId)
			.orElseThrow(() -> new GroupRuleException("That person is no longer a member of this group."));
	}

	private static String validName(String name) {
		String trimmed = (name != null) ? name.trim() : "";
		if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
			throw new GroupRuleException("The group name must be 1 to " + MAX_NAME_LENGTH + " characters long.");
		}
		return trimmed;
	}

}
