package app.adventr;

import java.util.UUID;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import app.adventr.user.UserRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc logins as distinct users. Each {@link TestUser} has a unique {@code sub}, so tests
 * sharing the database do not see each other's groups.
 */
public class TestUsers {

	private final MockMvc mvc;

	private final ClientRegistrationRepository clientRegistrations;

	private final UserRepository users;

	public TestUsers(MockMvc mvc, ClientRegistrationRepository clientRegistrations, UserRepository users) {
		this.mvc = mvc;
		this.clientRegistrations = clientRegistrations;
		this.users = users;
	}

	/**
	 * A new user, provisioned locally by a first request.
	 */
	public TestUser create(String name) throws Exception {
		String sub = name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID();
		RequestPostProcessor login = oidcLogin().clientRegistration(this.clientRegistrations.findByRegistrationId("keycloak"))
			.idToken((token) -> token.subject(sub).claim("preferred_username", name));
		this.mvc.perform(get("/groups").with(login)).andExpect(status().isOk());
		long id = this.users.findByKeycloakSub(sub).orElseThrow().getId();
		return new TestUser(id, name, login);
	}

	/**
	 * Creates a group through the UI and returns its id.
	 */
	public long createGroup(TestUser owner, String name) throws Exception {
		MvcResult result = this.mvc.perform(post("/groups").param("name", name).with(owner.login()).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String location = result.getResponse().getRedirectedUrl();
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	public record TestUser(long id, String name, RequestPostProcessor login) {
	}

}
