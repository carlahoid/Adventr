package app.adventr.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

	Optional<Membership> findByGroupIdAndUserIdAndLeftAtIsNull(long groupId, long userId);

	Optional<Membership> findByGroupIdAndUserId(long groupId, long userId);

	long countByGroupIdAndLeftAtIsNull(long groupId);

	List<Membership> findByUserIdAndRoleAndLeftAtIsNull(long userId, Role role);

	/**
	 * Active members of a group with their current display names, owner first, then by join date.
	 */
	@Query("""
			select new app.adventr.group.MemberRow(u.id, u.displayName, m.role, m.joinedAt)
			from Membership m join User u on u.id = m.userId
			where m.groupId = :groupId and m.leftAt is null
			order by case when m.role = app.adventr.group.Role.OWNER then 0 else 1 end, m.joinedAt, m.id
			""")
	List<MemberRow> findActiveMembers(@Param("groupId") long groupId);

	/**
	 * The longest-standing active member other than the given user, for owner succession.
	 */
	Optional<Membership> findFirstByGroupIdAndUserIdNotAndLeftAtIsNullOrderByJoinedAtAscIdAsc(long groupId,
			long userId);

	/**
	 * Adds the user as a Member, or reactivates a former membership with a new join date. An
	 * already active membership is left unchanged. A single upsert, so that a double click or
	 * two tabs cannot create duplicates.
	 */
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			insert into memberships (group_id, user_id, role, joined_at)
			values (:groupId, :userId, 'MEMBER', now())
			on conflict (group_id, user_id)
			do update set role = 'MEMBER', joined_at = now(), left_at = null
			where memberships.left_at is not null
			""", nativeQuery = true)
	int addOrReactivateMember(@Param("groupId") long groupId, @Param("userId") long userId);

	/**
	 * Which of the given users are active members of the group, for the "former member" label.
	 */
	@Query("select m.userId from Membership m where m.groupId = :groupId and m.leftAt is null and m.userId in :userIds")
	List<Long> findActiveUserIds(@Param("groupId") long groupId, @Param("userIds") Iterable<Long> userIds);

}
