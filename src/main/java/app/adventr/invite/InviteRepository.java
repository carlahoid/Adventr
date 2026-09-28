package app.adventr.invite;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

	Optional<Invite> findByGroupId(long groupId);

	Optional<Invite> findByToken(String token);

	/**
	 * Creates the group's invite, or replaces its token and expiry, which invalidates the old
	 * link at once. One statement, so that two concurrent regenerations cannot both insert.
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			insert into invites (group_id, token, expires_at, created_by)
			values (:groupId, :token, :expiresAt, :createdBy)
			on conflict (group_id)
			do update set token = excluded.token, expires_at = excluded.expires_at,
				created_by = excluded.created_by, created_at = now()
			""", nativeQuery = true)
	void upsert(@Param("groupId") long groupId, @Param("token") String token, @Param("expiresAt") Instant expiresAt,
			@Param("createdBy") long createdBy);

}
