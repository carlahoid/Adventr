package app.adventr.adventure;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Row-level reads and writes of the {@code reactions} table. The primary key
 * {@code (adventure_id, user_id)} makes a second insert for the same user fail instead of
 * creating a duplicate.
 */
@Component
class ReactionStore {

	private final JdbcClient jdbc;

	ReactionStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<ReactionType> find(long adventureId, long userId) {
		return this.jdbc.sql("select type from reactions where adventure_id = :adventureId and user_id = :userId")
			.param("adventureId", adventureId)
			.param("userId", userId)
			.query(String.class)
			.optional()
			.map(ReactionType::valueOf);
	}

	/**
	 * @throws org.springframework.dao.DuplicateKeyException if the user already reacted
	 */
	void insert(long adventureId, long userId, ReactionType type) {
		this.jdbc.sql("insert into reactions (adventure_id, user_id, type) values (:adventureId, :userId, :type)")
			.param("adventureId", adventureId)
			.param("userId", userId)
			.param("type", type.name())
			.update();
	}

	/**
	 * Switches the reaction; it then counts as given now, for the order of names.
	 */
	void update(long adventureId, long userId, ReactionType type) {
		this.jdbc
			.sql("update reactions set type = :type, created_at = now() where adventure_id = :adventureId and user_id = :userId")
			.param("adventureId", adventureId)
			.param("userId", userId)
			.param("type", type.name())
			.update();
	}

	void delete(long adventureId, long userId) {
		this.jdbc.sql("delete from reactions where adventure_id = :adventureId and user_id = :userId")
			.param("adventureId", adventureId)
			.param("userId", userId)
			.update();
	}

}
