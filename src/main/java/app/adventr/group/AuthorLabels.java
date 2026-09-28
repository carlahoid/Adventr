package app.adventr.group;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import app.adventr.user.UserRepository;

/**
 * Resolves user ids to {@link Author}s with the "former member" label, for every author and
 * reactor display. Callers are group-scoped services that have already passed the guard.
 * Two queries per call, however many authors a page shows.
 */
@Component
public class AuthorLabels {

	private final UserRepository users;

	private final MembershipRepository memberships;

	public AuthorLabels(UserRepository users, MembershipRepository memberships) {
		this.users = users;
		this.memberships = memberships;
	}

	@Transactional(readOnly = true)
	public Map<Long, Author> authorsIn(long groupId, Collection<Long> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		Set<Long> ids = new HashSet<>(userIds);
		Set<Long> active = new HashSet<>(this.memberships.findActiveUserIds(groupId, ids));
		return this.users.findAllById(ids)
			.stream()
			.map((user) -> new Author(user.getId(), user.getDisplayName(),
					user.getDeletedAt() != null || !active.contains(user.getId())))
			.collect(Collectors.toMap(Author::userId, Function.identity()));
	}

	@Transactional(readOnly = true)
	public Author authorIn(long groupId, long userId) {
		Author author = authorsIn(groupId, Set.of(userId)).get(userId);
		if (author == null) {
			throw new IllegalStateException("Unknown user " + userId);
		}
		return author;
	}

}
