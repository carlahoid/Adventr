package app.adventr.group;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {

	/**
	 * The user's active groups with their active member counts, for "My groups" and the switcher.
	 */
	@Query("""
			select new app.adventr.group.GroupSummary(g.id, g.name,
				(select count(m2) from Membership m2 where m2.groupId = g.id and m2.leftAt is null))
			from FriendGroup g, Membership m
			where m.groupId = g.id and m.userId = :userId and m.leftAt is null
			order by lower(g.name), g.id
			""")
	List<GroupSummary> findSummariesForMember(@Param("userId") long userId);

	/**
	 * Deletes the group; the database cascades to memberships, invites, and (later) adventures,
	 * reactions, and comments.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from FriendGroup g where g.id = :groupId")
	void deleteGroup(@Param("groupId") long groupId);

}
