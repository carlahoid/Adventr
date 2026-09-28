package app.adventr.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	/**
	 * Public landing page. Logged-in users go straight to their groups.
	 */
	@GetMapping("/")
	String landing(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof OidcUser) {
			return "redirect:/groups";
		}
		return "landing";
	}

}
