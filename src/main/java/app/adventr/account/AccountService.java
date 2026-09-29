package app.adventr.account;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import app.adventr.group.GroupAccessDeniedException;
import app.adventr.group.GroupAccessService;
import app.adventr.image.ImageRejectedException;
import app.adventr.user.Theme;

import app.adventr.user.User;
import app.adventr.user.UserRepository;
import app.adventr.user.UserService;

/**
 * The "My account" settings. Every method acts on the user id of the logged-in user, which the
 * controller takes from the session only: there is no way to address another user's account.
 * Not group-scoped, so the membership guard does not apply.
 */
@Service
public class AccountService {

	public static final int MAX_DISPLAY_NAME = 60;

	public static final int MAX_BIO = 160;

	/** Line breaks, tabs, and other control characters. */
	private static final Pattern CONTROL = Pattern.compile("\\p{Cc}+");

	private final UserRepository users;

	private final AvatarStorage avatars;

	private final GroupAccessService access;

	public AccountService(UserRepository users, AvatarStorage avatars, GroupAccessService access) {
		this.users = users;
		this.avatars = avatars;
		this.access = access;
	}

	@Transactional(readOnly = true)
	public AccountView view(long userId) {
		User user = user(userId);
		return new AccountView(user.getId(), user.getDisplayName(), user.isDisplayNameCustom(), user.getBio(),
				user.getEmail(), user.getAvatarVersion(), user.getTheme(), user.getAccentColor());
	}

	/**
	 * Saves the display name (as a custom name, which later logins keep) and the bio.
	 * @throws AccountValidationException with a message per invalid field; nothing is saved then
	 */
	@Transactional
	public void updateProfile(long userId, String displayName, String bio) {
		Map<String, String> errors = new LinkedHashMap<>();
		String name = (displayName != null) ? displayName.strip() : "";
		if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_DISPLAY_NAME) {
			errors.put("displayName", "Please enter a name of 1 to " + MAX_DISPLAY_NAME + " characters.");
		}
		else if (CONTROL.matcher(name).find()) {
			errors.put("displayName", "The name can't contain line breaks or other control characters.");
		}
		// The bio is shown on one line: line breaks and other control characters become spaces.
		String oneLine = (bio != null) ? CONTROL.matcher(bio).replaceAll(" ").strip() : "";
		if (oneLine.codePointCount(0, oneLine.length()) > MAX_BIO) {
			errors.put("bio", "The bio can be at most " + MAX_BIO + " characters long.");
		}
		if (!errors.isEmpty()) {
			throw new AccountValidationException(errors);
		}
		User user = user(userId);
		// Saving only the bio keeps an unchanged Keycloak name in sync.
		if (!name.equals(user.getDisplayName())) {
			user.setCustomDisplayName(name);
		}
		user.setBio(oneLine.isEmpty() ? null : oneLine);
	}

	/**
	 * Goes back to the name derived from the Keycloak account of the current login; later logins
	 * keep it in sync again.
	 */
	@Transactional
	public void resetDisplayName(long userId, OidcUser oidcUser) {
		user(userId).resetDisplayName(UserService.displayNameOf(oidcUser));
	}

	/**
	 * Saves theme and primary color. The default color is stored as {@code null}, so that it
	 * follows any future change of the default.
	 * @throws AccountValidationException for an unknown theme or anything but {@code #rrggbb}
	 */
	@Transactional
	public void updateAppearance(long userId, String theme, String color) {
		Map<String, String> errors = new LinkedHashMap<>();
		Theme parsedTheme = parseTheme(theme);
		if (parsedTheme == null) {
			errors.put("theme", "Please choose Light, Dark, or System.");
		}
		String hex = AccentColors.normalize(color).orElse(null);
		if (hex == null) {
			errors.put("color", "Please choose a color, e.g. one of the presets.");
		}
		if (!errors.isEmpty()) {
			throw new AccountValidationException(errors);
		}
		user(userId).setAppearance(parsedTheme, AccentColors.DEFAULT.equals(hex) ? null : hex);
	}

	/**
	 * Back to the default primary color; the theme stays.
	 */
	@Transactional
	public void resetColor(long userId) {
		User user = user(userId);
		user.setAppearance(user.getTheme(), null);
	}

	private static Theme parseTheme(String theme) {
		for (Theme candidate : Theme.values()) {
			if (candidate.name().equalsIgnoreCase(theme)) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Sets or replaces the user's avatar. The previous file is deleted after commit.
	 * @throws ImageRejectedException if the upload is not an acceptable image
	 */
	@Transactional
	public void setAvatar(long userId, MultipartFile upload) {
		User user = user(userId);
		String previous = user.getAvatarPath();
		user.setAvatarPath(this.avatars.store(userId, upload));
		this.avatars.deleteAfterCommit(previous);
	}

	@Transactional
	public void removeAvatar(long userId) {
		User user = user(userId);
		String previous = user.getAvatarPath();
		user.setAvatarPath(null);
		this.avatars.deleteAfterCommit(previous);
	}

	/**
	 * Another user's avatar, for the user themself and for people who currently share a group
	 * with them.
	 * @throws GroupAccessDeniedException (404) for anyone else, and when there is no avatar
	 */
	@Transactional(readOnly = true)
	public Resource avatar(long viewerId, long userId) {
		this.access.requireSharedGroup(viewerId, userId);
		Resource file = this.users.findById(userId)
			.map(User::getAvatarPath)
			.map(this.avatars::read)
			.orElse(null);
		if (file == null) {
			throw new GroupAccessDeniedException("No avatar for user " + userId);
		}
		return file;
	}

	private User user(long userId) {
		return this.users.findById(userId).orElseThrow(() -> new IllegalStateException("Unknown user " + userId));
	}

}
