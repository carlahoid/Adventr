package app.adventr.invite;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A group's invite link. There is at most one per group; it is written only through
 * {@link InviteRepository#upsert}, which replaces the token on regeneration.
 */
@Entity
@Table(name = "invites")
public class Invite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false, updatable = false)
	private Long groupId;

	@Column(nullable = false, length = 64)
	private String token;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected Invite() {
	}

	public Long getId() {
		return this.id;
	}

	public long getGroupId() {
		return this.groupId;
	}

	public String getToken() {
		return this.token;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(this.expiresAt);
	}

	public long getCreatedBy() {
		return this.createdBy;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
