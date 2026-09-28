package app.adventr;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally with throwaway Postgres and Keycloak containers, as an alternative to
 * docker-compose.dev.yml. The app listens on a random free port; see the startup log.
 */
public class TestAdventrApplication {

	public static void main(String[] args) {
		SpringApplication.from(AdventrApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
