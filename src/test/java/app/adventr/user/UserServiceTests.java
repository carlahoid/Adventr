package app.adventr.user;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceTests {

	@Test
	void displayNamePrefersGivenAndFamilyName() {
		assertThat(UserService.displayNameOf(user(Map.of("given_name", "Anna", "family_name", "Berg",
				"preferred_username", "anna88"))))
			.isEqualTo("Anna Berg");
	}

	@Test
	void displayNameUsesGivenNameAlone() {
		assertThat(UserService.displayNameOf(user(Map.of("given_name", " Anna ", "preferred_username", "anna88"))))
			.isEqualTo("Anna");
	}

	@Test
	void displayNameFallsBackToUsername() {
		assertThat(UserService.displayNameOf(user(Map.of("preferred_username", "anna88", "given_name", " "))))
			.isEqualTo("anna88");
	}

	@Test
	void displayNameFallsBackToEmailThenSubject() {
		assertThat(UserService.displayNameOf(user(Map.of("email", "anna@example.com")))).isEqualTo("anna@example.com");
		assertThat(UserService.displayNameOf(user(Map.of()))).isEqualTo("sub-123");
	}

	private static OidcUser user(Map<String, Object> claims) {
		OidcIdToken token = OidcIdToken.withTokenValue("token")
			.subject("sub-123")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.claims((c) -> c.putAll(claims))
			.build();
		return new DefaultOidcUser(List.of(), token);
	}

}
