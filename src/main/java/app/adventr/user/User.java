package app.adventr.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Local mirror of a Keycloak account. Identity lives in Keycloak; this row exists so that
 * app data (memberships, adventures, comments) can reference users by a stable local id.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "keycloak_sub", nullable = false, unique = true, updatable = false)
	private String keycloakSub;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	private String email;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected User() {
	}

	public Long getId() {
		return this.id;
	}

	public String getKeycloakSub() {
		return this.keycloakSub;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getEmail() {
		return this.email;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getDeletedAt() {
		return this.deletedAt;
	}

}
