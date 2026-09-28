package app.adventr.invite;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.adventr.group.GroupAccessService;
import app.adventr.group.GroupRepository;
import app.adventr.group.GroupScopedService;
import app.adventr.group.GuardExempt;
import app.adventr.group.Membership;
import app.adventr.group.MembershipRepository;

/**
 * Invite links: the Owner generates one per group, every member can see and share it, and
 * anyone logged in who opens it can join until it expires or is regenerated.
 */
@Service
@GroupScopedService
public class InviteService {

	/** How long a new link stays valid. */
	public static final Duration VALIDITY = Duration.ofDays(7);

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	private final InviteRepository invites;

	private final GroupRepository groups;

	private final MembershipRepository memberships;

	private final GroupAccessService access;

	private final Clock clock;

	public InviteService(InviteRepository invites, GroupRepository groups, MembershipRepository memberships,
			GroupAccessService access, Clock clock) {
		this.invites = invites;
		this.groups = groups;
		this.memberships = memberships;
		this.access = access;
		this.clock = clock;
	}

	/**
	 * Creates the group's invite link, or replaces it: the previous token stops working at once.
	 */
	@Transactional
	public InviteView generate(long userId, long groupId) {
		this.access.requireOwner(userId, groupId);
		this.invites.upsert(groupId, newToken(), this.clock.instant().plus(VALIDITY), userId);
		return this.invites.findByGroupId(groupId).map(this::toView).orElseThrow();
	}

	/**
	 * The group's invite link, including an expired one, so that the page can say so.
	 */
	@Transactional(readOnly = true)
	public Optional<InviteView> current(long userId, long groupId) {
		this.access.requireMember(userId, groupId);
		return this.invites.findByGroupId(groupId).map(this::toView);
	}

	/**
	 * What opening the link means for this user, without changing anything.
	 */
	@GuardExempt("The invite token is the authorization; an unknown token reveals no group")
	@Transactional(readOnly = true)
	public JoinPreview preview(long userId, String token) {
		Optional<Invite> invite = this.invites.findByToken(token);
		if (invite.isEmpty()) {
			return JoinPreview.of(JoinStatus.INVALID);
		}
		if (invite.get().isExpired(this.clock.instant())) {
			return JoinPreview.of(JoinStatus.EXPIRED);
		}
		long groupId = invite.get().getGroupId();
		Optional<Membership> membership = this.memberships.findByGroupIdAndUserId(groupId, userId);
		JoinStatus status = membership.map((m) -> (m.getLeftAt() == null) ? JoinStatus.ALREADY_MEMBER : JoinStatus.CAN_REJOIN)
			.orElse(JoinStatus.CAN_JOIN);
		String groupName = this.groups.findById(groupId).orElseThrow().getName();
		return new JoinPreview(status, groupId, groupName);
	}

	/**
	 * Joins the group with a valid link: adds the user as a Member, or reactivates a former
	 * membership with a new join date. Active members stay as they are (no duplicate).
	 * @return the preview as it was before joining; nothing changed unless it
	 * {@linkplain JoinStatus#canJoin() allowed joining}
	 */
	@GuardExempt("The invite token is the authorization; an unknown token reveals no group")
	@Transactional
	public JoinPreview join(long userId, String token) {
		JoinPreview preview = preview(userId, token);
		if (preview.status().canJoin()) {
			this.memberships.addOrReactivateMember(preview.groupId(), userId);
		}
		return preview;
	}

	private InviteView toView(Invite invite) {
		Instant now = this.clock.instant();
		return new InviteView(invite.getToken(), invite.getExpiresAt().atZone(this.clock.getZone()), invite.isExpired(now));
	}

	/**
	 * 256 random bits, URL-safe.
	 */
	private String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
