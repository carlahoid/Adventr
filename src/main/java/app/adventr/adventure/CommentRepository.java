package app.adventr.adventure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	/**
	 * The thread, oldest first.
	 */
	List<Comment> findByAdventureIdOrderByCreatedAtAscIdAsc(long adventureId);

	/**
	 * Scoped to the adventure, whose group the caller has already checked.
	 */
	Optional<Comment> findByIdAndAdventureId(long id, long adventureId);

}
