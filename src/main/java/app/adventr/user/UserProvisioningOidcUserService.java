package app.adventr.user;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Hooks into the OIDC login: after Keycloak has authenticated the user, make sure the local
 * {@link User} row exists and is up to date.
 */
@Component
public class UserProvisioningOidcUserService extends OidcUserService {

	private final UserService userService;

	public UserProvisioningOidcUserService(UserService userService) {
		this.userService = userService;
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		OidcUser oidcUser = super.loadUser(userRequest);
		this.userService.provision(oidcUser);
		return oidcUser;
	}

}
