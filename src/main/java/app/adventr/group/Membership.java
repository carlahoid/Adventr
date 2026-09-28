package app.adventr.group;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A user's membership in a group. Leaving sets {@code leftAt} instead of deleting the row, so
 * that the former member's content keeps its author. Active means {@code leftAt == null}.
 */
@Entity
@Table(name = "memberships")
public class Membership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Role role;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	protected Membership() {
	}

	Membership(long groupId, long userId, Role role, Instant joinedAt) {
		this.groupId = groupId;
		this.userId = userId;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	public Long getId() {
		return this.id;
	}

	public long getGroupId() {
		return this.groupId;
	}

	public long getUserId() {
		return this.userId;
	}

	public Role getRole() {
		return this.role;
	}

	public boolean isOwner() {
		return this.role == Role.OWNER;
	}

	public Instant getJoinedAt() {
		return this.joinedAt;
	}

	public Instant getLeftAt() {
		return this.leftAt;
	}

	void changeRole(Role role) {
		this.role = role;
	}

	void leave(Instant now) {
		this.leftAt = now;
		this.role = Role.MEMBER;
	}

}
