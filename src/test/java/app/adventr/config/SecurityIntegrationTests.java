package app.adventr.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

import app.adventr.IntegrationTest;
import app.adventr.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SecurityIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClientRegistrationRepository clientRegistrations;

	@Autowired
	private UserRepository users;

	@Test
	void landingPageIsPublic() throws Exception {
		this.mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Log in")));
	}

	@Test
	void loggedInUserIsRedirectedFromLandingToMyGroups() throws Exception {
		this.mvc.perform(get("/").with(oidcLogin().clientRegistration(keycloak())))
			.andExpect(redirectedUrl("/groups"));
	}

	@Test
	void unauthenticatedRequestRedirectsToKeycloak() throws Exception {
		this.mvc.perform(get("/groups"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/oauth2/authorization/keycloak")));
		this.mvc.perform(get("/oauth2/authorization/keycloak"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", startsWith(keycloak().getProviderDetails().getAuthorizationUri())))
			.andExpect(header().string("Location", containsString("client_id=adventr-app")));
	}

	@Test
	void healthIsReachableWithoutLogin() throws Exception {
		this.mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void postWithoutCsrfTokenIsRejected() throws Exception {
		long usersBefore = this.users.count();

		this.mvc.perform(post("/groups").param("name", "Mountain Crew").with(oidcLogin().clientRegistration(keycloak())))
			.andExpect(status().isForbidden());

		assertThat(this.users.count()).isEqualTo(usersBefore);
	}

	@Test
	void pagesCarryCsrfHeaderForHtmx() throws Exception {
		this.mvc.perform(get("/groups").with(oidcLogin().clientRegistration(keycloak())))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("hx-headers=\"{&quot;X-CSRF-TOKEN&quot;: &quot;")));
	}

	@Test
	void logoutEndsKeycloakSessionAndReturnsToLanding() throws Exception {
		String endSession = (String) keycloak().getProviderDetails()
			.getConfigurationMetadata()
			.get("end_session_endpoint");

		this.mvc.perform(post("/logout").with(csrf()).with(oidcLogin().clientRegistration(keycloak())))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", startsWith(endSession)))
			.andExpect(header().string("Location", containsString("id_token_hint=")))
			.andExpect(header().string("Location", containsString("post_logout_redirect_uri=http://localhost/")));
	}

	private ClientRegistration keycloak() {
		return this.clientRegistrations.findByRegistrationId("keycloak");
	}

}
