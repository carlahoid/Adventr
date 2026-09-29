package app.adventr.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

	/** The user set {@code displayName} themselves; login then keeps it. */
	@Column(name = "display_name_custom", nullable = false)
	private boolean displayNameCustom;

	private String bio;

	@Column(name = "avatar_path")
	private String avatarPath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 6)
	private Theme theme = Theme.SYSTEM;

	/** {@code #rrggbb}, or {@code null} for the default color. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "accent_color", length = 7)
	private String accentColor;

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

	public boolean isDisplayNameCustom() {
		return this.displayNameCustom;
	}

	public String getBio() {
		return this.bio;
	}

	public String getAvatarPath() {
		return this.avatarPath;
	}

	/**
	 * The random part of the avatar file name, or {@code null} without an avatar. It changes with
	 * every upload, so avatar URLs carry it and can be cached for a long time.
	 */
	public String getAvatarVersion() {
		return avatarVersionOf(this.avatarPath);
	}

	/**
	 * The version part of an avatar path such as {@code avatars/7/0b1c….jpg}.
	 */
	public static String avatarVersionOf(String avatarPath) {
		if (avatarPath == null) {
			return null;
		}
		String name = avatarPath.substring(avatarPath.lastIndexOf('/') + 1);
		return name.substring(0, name.length() - ".jpg".length());
	}

	public Theme getTheme() {
		return this.theme;
	}

	public String getAccentColor() {
		return this.accentColor;
	}

	/**
	 * A name the user chose; later logins keep it.
	 */
	public void setCustomDisplayName(String displayName) {
		this.displayName = displayName;
		this.displayNameCustom = true;
	}

	/**
	 * Back to the name derived from Keycloak; later logins refresh it again.
	 */
	public void resetDisplayName(String keycloakName) {
		this.displayName = keycloakName;
		this.displayNameCustom = false;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public void setAvatarPath(String avatarPath) {
		this.avatarPath = avatarPath;
	}

	public void setAppearance(Theme theme, String accentColor) {
		this.theme = theme;
		this.accentColor = accentColor;
	}

}
