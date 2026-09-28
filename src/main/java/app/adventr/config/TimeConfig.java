package app.adventr.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A shared {@link Clock}: services take "now" from it, and its zone is used to show dates.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
