package app.adventr.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByKeycloakSub(String keycloakSub);

	/**
	 * Inserts the user or refreshes name and email, atomically, so that two concurrent first
	 * requests for the same account cannot create duplicates.
	 */
	@Query(value = """
			insert into users (keycloak_sub, display_name, email)
			values (:sub, :displayName, :email)
			on conflict (keycloak_sub)
			do update set display_name = excluded.display_name, email = excluded.email
			returning id
			""", nativeQuery = true)
	long upsert(@Param("sub") String sub, @Param("displayName") String displayName, @Param("email") String email);

}
