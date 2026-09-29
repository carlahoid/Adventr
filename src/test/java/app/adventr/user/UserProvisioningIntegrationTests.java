package app.adventr.user;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import app.adventr.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login sync with custom display names: email always follows Keycloak, the name only while the
 * user has not set their own.
 */
@IntegrationTest
class UserProvisioningIntegrationTests {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository users;

	@Test
	void customNameSurvivesLoginWithAChangedKeycloakNameWhileEmailSyncs() {
		String sub = "sub-" + UUID.randomUUID();
		long id = this.userService.provision(oidcUser(sub, "Kim", "Before", "kim@example.com")).id();
		setCustomName(id, "Kim the Climber");

		CurrentUser afterLogin = this.userService.provision(oidcUser(sub, "Kim", "After", "kim.new@example.com"));

		assertThat(afterLogin.displayName()).isEqualTo("Kim the Climber");
		User user = this.users.findById(id).orElseThrow();
		assertThat(user.getDisplayName()).isEqualTo("Kim the Climber");
		assertThat(user.isDisplayNameCustom()).isTrue();
		assertThat(user.getEmail()).isEqualTo("kim.new@example.com");
	}

	@Test
	void nameWithoutCustomizationStillSyncs() {
		String sub = "sub-" + UUID.randomUUID();
		long id = this.userService.provision(oidcUser(sub, "Sam", "Before", "sam@example.com")).id();

		CurrentUser afterLogin = this.userService.provision(oidcUser(sub, "Sam", "After", "sam@example.com"));

		assertThat(afterLogin.displayName()).isEqualTo("Sam After");
		assertThat(this.users.findById(id).orElseThrow().getDisplayName()).isEqualTo("Sam After");
	}

	@Test
	void afterAResetTheNameSyncsAgain() {
		String sub = "sub-" + UUID.randomUUID();
		long id = this.userService.provision(oidcUser(sub, "Lee", "One", "lee@example.com")).id();
		setCustomName(id, "Lee L.");
		User user = this.users.findById(id).orElseThrow();
		user.resetDisplayName("Lee One");
		this.users.save(user);

		assertThat(this.userService.provision(oidcUser(sub, "Lee", "Two", "lee@example.com")).displayName())
			.isEqualTo("Lee Two");
	}

	@Test
	void newUsersGetTheDefaultAppearance() {
		long id = this.userService.provision(oidcUser("sub-" + UUID.randomUUID(), "Ann", "New", null)).id();

		User user = this.users.findById(id).orElseThrow();
		assertThat(user.getTheme()).isEqualTo(Theme.SYSTEM);
		assertThat(user.getAccentColor()).isNull();
		assertThat(user.isDisplayNameCustom()).isFalse();
	}

	private void setCustomName(long id, String name) {
		User user = this.users.findById(id).orElseThrow();
		user.setCustomDisplayName(name);
		this.users.save(user);
	}

	private static OidcUser oidcUser(String sub, String givenName, String familyName, String email) {
		Map<String, Object> claims = new java.util.HashMap<>(Map.of("given_name", givenName, "family_name", familyName));
		if (email != null) {
			claims.put("email", email);
		}
		OidcIdToken token = OidcIdToken.withTokenValue("token")
			.subject(sub)
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.claims((c) -> c.putAll(claims))
			.build();
		return new DefaultOidcUser(List.of(), token);
	}

}
