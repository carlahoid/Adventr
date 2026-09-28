package app.adventr.invite;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds shareable invite URLs.
 */
public final class InviteLinks {

	private InviteLinks() {
	}

	/**
	 * The absolute {@code /join/{token}} URL for the current request's host. Behind Caddy this is
	 * the public https URL, because the app trusts the forwarded headers.
	 */
	public static String url(String token) {
		return ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/join/{token}")
			.buildAndExpand(token)
			.toUriString();
	}

}
