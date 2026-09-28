package app.adventr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import app.adventr.user.UserProvisioningOidcUserService;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserProvisioningOidcUserService oidcUserService,
			ClientRegistrationRepository clientRegistrations) throws Exception {
		http
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/", "/error", "/css/**", "/webjars/**", "/favicon.ico").permitAll()
				// Health is for Docker health checks; Caddy does not route /actuator to the outside.
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.anyRequest().authenticated())
			// With a single client registration, unauthenticated requests go straight to Keycloak,
			// and the originally requested URL is restored after login (saved request).
			.oauth2Login((login) -> login.userInfoEndpoint((userInfo) -> userInfo.oidcUserService(oidcUserService)))
			.logout((logout) -> logout.logoutSuccessHandler(keycloakLogout(clientRegistrations)));
		// CSRF protection stays enabled (the default); htmx sends the token via hx-headers on <body>.
		return http.build();
	}

	/**
	 * RP-initiated logout: ends the Keycloak SSO session as well, then returns to the landing page.
	 */
	private LogoutSuccessHandler keycloakLogout(ClientRegistrationRepository clientRegistrations) {
		OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(
				clientRegistrations);
		handler.setPostLogoutRedirectUri("{baseUrl}/");
		return handler;
	}

}
