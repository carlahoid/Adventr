package app.adventr.adventure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import app.adventr.group.Author;
import app.adventr.group.AuthorLabels;
import app.adventr.group.GroupAccessService;
import app.adventr.group.GroupScopedService;

/**
 * 👍/👎 reactions. Toggle semantics per clicked type:
 * <pre>
 * current | clicked 👍 | clicked 👎
 * none    | 👍         | 👎
 * 👍      | none       | 👎
 * 👎      | 👍         | none
 * </pre>
 */
@Service
@GroupScopedService
public class ReactionService {

	private final ReactionStore reactions;

	private final AdventureRepository adventures;

	private final AdventureQueries queries;

	private final GroupAccessService access;

	private final AuthorLabels authorLabels;

	private final TransactionTemplate transactions;

	public ReactionService(ReactionStore reactions, AdventureRepository adventures, AdventureQueries queries,
			GroupAccessService access, AuthorLabels authorLabels, TransactionTemplate transactions) {
		this.reactions = reactions;
		this.adventures = adventures;
		this.queries = queries;
		this.access = access;
		this.authorLabels = authorLabels;
		this.transactions = transactions;
	}

	/**
	 * Applies a click and returns the updated bar. A concurrent click by the same user can make
	 * the insert hit the primary key; the failed transaction is then retried once, and the retry
	 * sees the other click's row and toggles from there.
	 */
	public ReactionBar toggle(long userId, long groupId, long adventureId, ReactionType clicked) {
		this.access.requireMember(userId, groupId);
		try {
			this.transactions.executeWithoutResult((status) -> apply(userId, groupId, adventureId, clicked));
		}
		catch (DuplicateKeyException ex) {
			this.transactions.executeWithoutResult((status) -> apply(userId, groupId, adventureId, clicked));
		}
		return bar(userId, groupId, adventureId);
	}

	/**
	 * Counts, the user's own reaction, and who reacted, for one adventure.
	 */
	@Transactional(readOnly = true)
	public ReactionBar bar(long userId, long groupId, long adventureId) {
		this.access.requireMember(userId, groupId);
		requireAdventure(groupId, adventureId);
		List<AdventureQueries.ReactionRow> rows = this.queries.reactions(adventureId);
		Map<Long, Author> authors = this.authorLabels.authorsIn(groupId,
				rows.stream().map(AdventureQueries.ReactionRow::userId).toList());
		Map<ReactionType, List<Author>> byType = rows.stream()
			.collect(Collectors.groupingBy(AdventureQueries.ReactionRow::type,
					Collectors.mapping((row) -> authors.get(row.userId()), Collectors.toList())));
		ReactionType mine = rows.stream()
			.filter((row) -> row.userId() == userId)
			.map(AdventureQueries.ReactionRow::type)
			.findFirst()
			.orElse(null);
		return new ReactionBar(groupId, adventureId, mine, byType.getOrDefault(ReactionType.UP, List.of()),
				byType.getOrDefault(ReactionType.DOWN, List.of()));
	}

	private void apply(long userId, long groupId, long adventureId, ReactionType clicked) {
		requireAdventure(groupId, adventureId);
		Optional<ReactionType> current = this.reactions.find(adventureId, userId);
		if (current.isEmpty()) {
			this.reactions.insert(adventureId, userId, clicked);
		}
		else if (current.get() == clicked) {
			this.reactions.delete(adventureId, userId);
		}
		else {
			this.reactions.update(adventureId, userId, clicked);
		}
	}

	private void requireAdventure(long groupId, long adventureId) {
		if (!this.adventures.existsByIdAndGroupId(adventureId, groupId)) {
			throw new AdventureNotFoundException("No adventure " + adventureId + " in group " + groupId);
		}
	}

}
