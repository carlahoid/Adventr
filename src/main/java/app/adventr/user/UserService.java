package app.adventr.user;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

	private final UserRepository users;

	public UserService(UserRepository users) {
		this.users = users;
	}

	/**
	 * Creates the local user for this Keycloak account, or refreshes its display name and
	 * email from the token. Called on every login.
	 */
	@Transactional
	public CurrentUser provision(OidcUser oidcUser) {
		String displayName = displayNameOf(oidcUser);
		long id = this.users.upsert(oidcUser.getSubject(), displayName, oidcUser.getEmail());
		return new CurrentUser(id, displayName);
	}

	/**
	 * Resolves the local user for an authenticated principal. Falls back to provisioning when
	 * the row is missing (e.g. a session that predates the database).
	 */
	@Transactional
	public CurrentUser current(OidcUser oidcUser) {
		return this.users.findByKeycloakSub(oidcUser.getSubject())
			.map((user) -> new CurrentUser(user.getId(), user.getDisplayName()))
			.orElseGet(() -> provision(oidcUser));
	}

	/**
	 * "Given Family" when Keycloak provides either name part, otherwise the username.
	 */
	static String displayNameOf(OidcUser oidcUser) {
		String fullName = Stream.of(oidcUser.getGivenName(), oidcUser.getFamilyName())
			.filter(StringUtils::hasText)
			.map(String::trim)
			.collect(Collectors.joining(" "));
		if (StringUtils.hasText(fullName)) {
			return fullName;
		}
		if (StringUtils.hasText(oidcUser.getPreferredUsername())) {
			return oidcUser.getPreferredUsername();
		}
		if (StringUtils.hasText(oidcUser.getEmail())) {
			return oidcUser.getEmail();
		}
		return oidcUser.getSubject();
	}

}
