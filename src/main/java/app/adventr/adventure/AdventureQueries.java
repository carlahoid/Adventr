package app.adventr.adventure;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read queries that aggregate over adventures, reactions, and comments in SQL. Callers are the
 * group-scoped services, after the guard.
 */
@Component
class AdventureQueries {

	/**
	 * The whole list page in one query: counts, the viewer's own reaction, and reactor ids per
	 * adventure, ordered by section (Planned, Ideas, Memories) and by each section's rule:
	 * <ul>
	 * <li>Planned: {@code date_from} ascending, no date last, then net score, then newest</li>
	 * <li>Ideas: net score descending, then newest</li>
	 * <li>Memories: most recent status change first</li>
	 * </ul>
	 * The section-specific sort keys are {@code null} outside their section, so they only order
	 * rows within it.
	 */
	private static final String LIST = """
			select a.id, a.title, a.status, a.date_from, a.date_to, a.image_path,
				(select count(*) from comments c where c.adventure_id = a.id) as comment_count,
				max(r.type) filter (where r.user_id = :userId) as my_reaction,
				array_agg(r.user_id order by r.created_at, r.user_id) filter (where r.type = 'UP') as up_ids,
				array_agg(r.user_id order by r.created_at, r.user_id) filter (where r.type = 'DOWN') as down_ids
			from adventures a
			left join reactions r on r.adventure_id = a.id
			where a.group_id = :groupId
			group by a.id
			order by
				case a.status when 'PLANNED' then 0 when 'IDEA' then 1 else 2 end,
				case when a.status = 'PLANNED' then a.date_from end asc nulls last,
				case when a.status = 'DONE' then a.status_changed_at end desc,
				count(r.user_id) filter (where r.type = 'UP') - count(r.user_id) filter (where r.type = 'DOWN') desc,
				a.created_at desc,
				a.id desc
			""";

	private final JdbcClient jdbc;

	AdventureQueries(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<ListRow> list(long groupId, long userId) {
		return this.jdbc.sql(LIST)
			.param("groupId", groupId)
			.param("userId", userId)
			.query((rs, rowNum) -> new ListRow(rs.getLong("id"), rs.getString("title"),
					Status.valueOf(rs.getString("status")), rs.getObject("date_from", LocalDate.class),
					rs.getObject("date_to", LocalDate.class), rs.getString("image_path"), rs.getLong("comment_count"),
					reactionType(rs.getString("my_reaction")), ids(rs, "up_ids"), ids(rs, "down_ids")))
			.list();
	}

	/**
	 * One adventure's reactions, oldest first.
	 */
	List<ReactionRow> reactions(long adventureId) {
		return this.jdbc
			.sql("select user_id, type from reactions where adventure_id = :adventureId order by created_at, user_id")
			.param("adventureId", adventureId)
			.query((rs, rowNum) -> new ReactionRow(rs.getLong("user_id"), ReactionType.valueOf(rs.getString("type"))))
			.list();
	}

	private static ReactionType reactionType(String value) {
		return (value != null) ? ReactionType.valueOf(value) : null;
	}

	private static List<Long> ids(ResultSet rs, String column) throws SQLException {
		Array array = rs.getArray(column);
		if (array == null) {
			return List.of();
		}
		try {
			return Arrays.stream((Object[]) array.getArray()).map((id) -> ((Number) id).longValue()).toList();
		}
		finally {
			array.free();
		}
	}

	record ListRow(long id, String title, Status status, LocalDate dateFrom, LocalDate dateTo, String imagePath,
			long commentCount, ReactionType myReaction, List<Long> upIds, List<Long> downIds) {
	}

	record ReactionRow(long userId, ReactionType type) {
	}

}
