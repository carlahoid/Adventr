package app.adventr.user;

import java.util.Map;
import java.util.UUID;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;

import app.adventr.Browser;
import app.adventr.Browser.Page;
import app.adventr.IntegrationTest;

import static app.adventr.TestcontainersConfiguration.APP_BASE_URL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end login flows through the real Keycloak pages of the imported {@code adventr} realm.
 */
@IntegrationTest
class LoginFlowIntegrationTests {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private KeycloakContainer keycloak;

	@Autowired
	private UserRepository users;

	@Test
	void unauthenticatedVisitorIsSentToKeycloakLoginPage() {
		Page login = new Browser().get(APP_BASE_URL + "/groups");

		assertThat(login.url()).startsWith(this.keycloak.getAuthServerUrl() + "/realms/adventr/protocol/openid-connect/auth");
		assertThat(login.body()).contains("login-actions/registration");
		assertThat(login.body()).contains("login-actions/reset-credentials");
		assertThat(login.body()).as("Google IdP is disabled by default").doesNotContain("social-google");
	}

	@Test
	void firstLoginCreatesLocalUserAndReturnsToRequestedPage() {
		Browser browser = new Browser();
		String username = uniqueUsername();

		Page landed = register(browser, APP_BASE_URL + "/groups/42", username, "Eve", "Tester");

		assertThat(landed.url()).isEqualTo(APP_BASE_URL + "/groups/42?continue");
		User user = this.users.findByKeycloakSub(keycloakUser(username).getId()).orElseThrow();
		assertThat(user.getDisplayName()).isEqualTo("Eve Tester");
		assertThat(user.getEmail()).isEqualTo(username + "@example.com");
		assertThat(user.getCreatedAt()).isNotNull();
	}

	@Test
	void subsequentLoginRefreshesProfileWithoutDuplicating() {
		Browser browser = new Browser();
		String username = uniqueUsername();
		Page groups = register(browser, APP_BASE_URL + "/groups", username, "Sam", "Before");
		long userId = this.users.findByKeycloakSub(keycloakUser(username).getId()).orElseThrow().getId();
		logout(browser, groups);

		UserResource keycloakUser = this.keycloak.getKeycloakAdminClient()
			.realm("adventr")
			.users()
			.get(keycloakUser(username).getId());
		UserRepresentation representation = keycloakUser.toRepresentation();
		representation.setLastName("After");
		keycloakUser.update(representation);

		Page login = browser.get(APP_BASE_URL + "/groups");
		Page landed = browser.post(login.formAction("kc-form-login"), Map.of("username", username, "password", PASSWORD));

		assertThat(landed.url()).startsWith(APP_BASE_URL + "/groups");
		assertThat(landed.body()).contains("Sam After");
		User user = this.users.findByKeycloakSub(representation.getId()).orElseThrow();
		assertThat(user.getId()).isEqualTo(userId);
		assertThat(user.getDisplayName()).isEqualTo("Sam After");
		assertThat(this.users.findAll()).filteredOn((u) -> u.getKeycloakSub().equals(representation.getId())).hasSize(1);
	}

	@Test
	void customDisplayNameSurvivesLoginAndShowsInTheHeaderRightAway() {
		Browser browser = new Browser();
		String username = uniqueUsername();
		Page groups = register(browser, APP_BASE_URL + "/groups", username, "Kim", "Before");
		String sub = keycloakUser(username).getId();
		User user = this.users.findByKeycloakSub(sub).orElseThrow();
		user.setCustomDisplayName("Kim the Climber");
		this.users.save(user);
		logout(browser, groups);

		UserResource keycloakUser = this.keycloak.getKeycloakAdminClient().realm("adventr").users().get(sub);
		UserRepresentation representation = keycloakUser.toRepresentation();
		representation.setLastName("After");
		representation.setEmail(username + "@changed.example.com");
		keycloakUser.update(representation);

		Page login = browser.get(APP_BASE_URL + "/groups");
		Page landed = browser.post(login.formAction("kc-form-login"), Map.of("username", username, "password", PASSWORD));

		assertThat(landed.body()).contains("Kim the Climber").doesNotContain("Kim After");
		User reloaded = this.users.findByKeycloakSub(sub).orElseThrow();
		assertThat(reloaded.getDisplayName()).isEqualTo("Kim the Climber");
		assertThat(reloaded.getEmail()).isEqualTo(username + "@changed.example.com");
	}

	@Test
	void logoutEndsAppAndKeycloakSessions() {
		Browser browser = new Browser();
		Page groups = register(browser, APP_BASE_URL + "/groups", uniqueUsername(), "Lou", "Gout");

		Page afterLogout = logout(browser, groups);

		assertThat(afterLogout.visited()).anyMatch((url) -> url.contains("/protocol/openid-connect/logout"));
		assertThat(afterLogout.url()).isEqualTo(APP_BASE_URL + "/");
		Page next = browser.get(APP_BASE_URL + "/groups");
		assertThat(next.url()).as("Keycloak SSO session ended, so the login form is shown again")
			.startsWith(this.keycloak.getAuthServerUrl() + "/realms/adventr/protocol/openid-connect/auth");
		assertThat(next.body()).contains("kc-form-login");
	}

	private Page register(Browser browser, String startUrl, String username, String firstName, String lastName) {
		Page login = browser.get(startUrl);
		Page registration = browser.get(login.linkContaining("login-actions/registration"));
		return browser.post(registration.formAction("kc-register-form"),
				Map.of("username", username, "email", username + "@example.com", "firstName", firstName, "lastName",
						lastName, "password", PASSWORD, "password-confirm", PASSWORD));
	}

	private Page logout(Browser browser, Page appPage) {
		return browser.post(APP_BASE_URL + "/logout", Map.of("_csrf", appPage.inputValue("_csrf")));
	}

	private UserRepresentation keycloakUser(String username) {
		return this.keycloak.getKeycloakAdminClient()
			.realm("adventr")
			.users()
			.searchByUsername(username, true)
			.getFirst();
	}

	private static String uniqueUsername() {
		return "user-" + UUID.randomUUID().toString().substring(0, 8);
	}

}
