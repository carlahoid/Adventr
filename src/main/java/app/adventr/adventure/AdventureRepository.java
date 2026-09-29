package app.adventr.adventure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdventureRepository extends JpaRepository<Adventure, Long> {

	/**
	 * The only way to load an adventure: scoped to the group the guard was checked for.
	 */
	Optional<Adventure> findByIdAndGroupId(long id, long groupId);

	boolean existsByIdAndGroupId(long id, long groupId);

}
