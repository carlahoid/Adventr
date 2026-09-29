package app.adventr.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByKeycloakSub(String keycloakSub);

	/**
	 * Inserts the user or refreshes email and name, atomically, so that two concurrent first
	 * requests for the same account cannot create duplicates. A name the user set themselves
	 * ({@code display_name_custom}) is kept. Returns the id and the effective display name.
	 */
	@Query(value = """
			insert into users (keycloak_sub, display_name, email)
			values (:sub, :displayName, :email)
			on conflict (keycloak_sub)
			do update set
				display_name = case when users.display_name_custom then users.display_name
					else excluded.display_name end,
				email = excluded.email
			returning id, display_name as displayName, avatar_path as avatarPath, theme, accent_color as accentColor
			""", nativeQuery = true)
	Provisioned upsert(@Param("sub") String sub, @Param("displayName") String displayName,
			@Param("email") String email);

	/**
	 * The result of {@link #upsert}.
	 */
	interface Provisioned {

		Long getId();

		String getDisplayName();

		String getAvatarPath();

		String getTheme();

		String getAccentColor();

	}

}
