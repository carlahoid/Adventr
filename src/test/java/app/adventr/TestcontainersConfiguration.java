package app.adventr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres plus a Keycloak that imports the production realm file ({@code keycloak/realm-adventr.json}).
 * The app runs on a fixed free port, because the realm restricts redirect URIs to {@code APP_BASE_URL}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	public static final int APP_PORT = freePort();

	public static final String APP_BASE_URL = "http://localhost:" + APP_PORT;

	static final String CLIENT_SECRET = "test-client-secret";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	@Bean
	KeycloakContainer keycloakContainer() {
		return new KeycloakContainer("quay.io/keycloak/keycloak:26.7.4").withContextPath("/auth")
			.withRealmImportFile("keycloak/realm-adventr.json")
			.withEnv("APP_BASE_URL", APP_BASE_URL)
			.withEnv("KEYCLOAK_CLIENT_SECRET", CLIENT_SECRET);
	}

	@Bean
	WebServerFactoryCustomizer<ConfigurableWebServerFactory> fixedAppPort() {
		return (factory) -> factory.setPort(APP_PORT);
	}

	@Bean
	DynamicPropertyRegistrar keycloakProperties(KeycloakContainer keycloak) {
		return (registry) -> {
			registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
					() -> keycloak.getAuthServerUrl() + "/realms/adventr");
			registry.add("spring.security.oauth2.client.registration.keycloak.client-secret", () -> CLIENT_SECRET);
		};
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
