package app.adventr.account;

import app.adventr.user.Theme;

/**
 * The logged-in user's own account settings. {@code accentColor} is the stored pick, or
 * {@code null} for the default; {@code avatarVersion} is {@code null} without an avatar.
 */
public record AccountView(long userId, String displayName, boolean displayNameCustom, String bio, String email,
		String avatarVersion, Theme theme, String accentColor) {

	public boolean hasAvatar() {
		return this.avatarVersion != null;
	}

	/**
	 * The color the form shows as picked: the stored one, or the default.
	 */
	public String effectiveAccentColor() {
		return (this.accentColor != null) ? this.accentColor : AccentColors.DEFAULT;
	}

}
