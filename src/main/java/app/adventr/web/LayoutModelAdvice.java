package app.adventr.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import app.adventr.user.CurrentUser;
import app.adventr.user.UserService;

/**
 * Model attributes needed by the shared layout (header) on every page.
 */
@ControllerAdvice
public class LayoutModelAdvice {

	private final UserService userService;

	public LayoutModelAdvice(UserService userService) {
		this.userService = userService;
	}

	/**
	 * The logged-in user for the header, or {@code null} on public pages.
	 */
	@ModelAttribute("currentUser")
	CurrentUser currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
			return this.userService.current(oidcUser);
		}
		return null;
	}

}
