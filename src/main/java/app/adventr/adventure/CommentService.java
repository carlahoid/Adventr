package app.adventr.adventure;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.adventr.group.Author;
import app.adventr.group.AuthorLabels;
import app.adventr.group.ForbiddenActionException;
import app.adventr.group.GroupAccessService;
import app.adventr.group.GroupScopedService;
import app.adventr.group.Membership;

/**
 * The flat comment thread of an adventure. Any member posts; only the author edits (not even
 * the Owner); the author or the Owner deletes.
 */
@Service
@GroupScopedService
public class CommentService {

	public static final int MAX_LENGTH = 2000;

	private final CommentRepository comments;

	private final AdventureRepository adventures;

	private final GroupAccessService access;

	private final AuthorLabels authorLabels;

	private final Clock clock;

	public CommentService(CommentRepository comments, AdventureRepository adventures, GroupAccessService access,
			AuthorLabels authorLabels, Clock clock) {
		this.comments = comments;
		this.adventures = adventures;
		this.access = access;
		this.authorLabels = authorLabels;
		this.clock = clock;
	}

	/**
	 * The thread, oldest first. {@code editingId} marks a comment to show as an edit form.
	 */
	@Transactional(readOnly = true)
	public CommentThread thread(long userId, long groupId, long adventureId, Long editingId) {
		Membership membership = this.access.requireMember(userId, groupId);
		requireAdventure(groupId, adventureId);
		List<Comment> thread = this.comments.findByAdventureIdOrderByCreatedAtAscIdAsc(adventureId);
		Map<Long, Author> authors = this.authorLabels.authorsIn(groupId,
				thread.stream().map(Comment::getAuthorId).toList());
		List<CommentView> views = thread.stream()
			.map((comment) -> toView(comment, groupId, membership, authors.get(comment.getAuthorId())))
			.toList();
		return new CommentThread(groupId, adventureId, views, editingId);
	}

	/**
	 * One comment, for re-rendering it in place.
	 */
	@Transactional(readOnly = true)
	public CommentView comment(long userId, long groupId, long adventureId, long commentId) {
		Membership membership = this.access.requireMember(userId, groupId);
		Comment comment = comment(groupId, adventureId, commentId);
		return toView(comment, groupId, membership, this.authorLabels.authorIn(groupId, comment.getAuthorId()));
	}

	/**
	 * The user's own comment, for the edit form.
	 * @throws ForbiddenActionException (403) if the user is not the author
	 */
	@Transactional(readOnly = true)
	public CommentView ownComment(long userId, long groupId, long adventureId, long commentId) {
		CommentView comment = comment(userId, groupId, adventureId, commentId);
		if (!comment.canEdit()) {
			throw new ForbiddenActionException("Only the author can edit this comment");
		}
		return comment;
	}

	/**
	 * Posts a comment at the end of the thread and returns its id.
	 * @throws CommentRejectedException if the text is blank or too long
	 */
	@Transactional
	public long post(long userId, long groupId, long adventureId, String text) {
		this.access.requireMember(userId, groupId);
		requireAdventure(groupId, adventureId);
		return this.comments.save(new Comment(adventureId, userId, validText(text), this.clock.instant())).getId();
	}

	/**
	 * Changes the text of the user's own comment and marks it as edited.
	 * @throws ForbiddenActionException (403) if the user is not the author
	 * @throws CommentRejectedException if the text is blank or too long
	 */
	@Transactional
	public void edit(long userId, long groupId, long adventureId, long commentId, String text) {
		this.access.requireMember(userId, groupId);
		Comment comment = comment(groupId, adventureId, commentId);
		if (comment.getAuthorId() != userId) {
			throw new ForbiddenActionException("Only the author can edit this comment");
		}
		comment.edit(validText(text), this.clock.instant());
	}

	/**
	 * @throws ForbiddenActionException (403) unless the user is the author or the group Owner
	 */
	@Transactional
	public void delete(long userId, long groupId, long adventureId, long commentId) {
		Membership membership = this.access.requireMember(userId, groupId);
		Comment comment = comment(groupId, adventureId, commentId);
		if (comment.getAuthorId() != userId && !membership.isOwner()) {
			throw new ForbiddenActionException("Only the author or the group owner can delete this comment");
		}
		this.comments.delete(comment);
	}

	private CommentView toView(Comment comment, long groupId, Membership membership, Author author) {
		boolean own = comment.getAuthorId() == membership.getUserId();
		return new CommentView(comment.getId(), groupId, comment.getAdventureId(), author, comment.getText(),
				comment.getCreatedAt().atZone(this.clock.getZone()),
				(comment.getEditedAt() != null) ? comment.getEditedAt().atZone(this.clock.getZone()) : null, own,
				own || membership.isOwner());
	}

	private Comment comment(long groupId, long adventureId, long commentId) {
		requireAdventure(groupId, adventureId);
		return this.comments.findByIdAndAdventureId(commentId, adventureId)
			.orElseThrow(() -> new AdventureNotFoundException("No comment " + commentId + " on adventure " + adventureId));
	}

	private void requireAdventure(long groupId, long adventureId) {
		if (!this.adventures.existsByIdAndGroupId(adventureId, groupId)) {
			throw new AdventureNotFoundException("No adventure " + adventureId + " in group " + groupId);
		}
	}

	/**
	 * Trimmed, with Windows line breaks normalized; inner line breaks are kept.
	 */
	private static String validText(String text) {
		String trimmed = (text != null) ? text.replace("\r\n", "\n").strip() : "";
		if (trimmed.isEmpty()) {
			throw new CommentRejectedException("Please write something before posting.");
		}
		if (trimmed.length() > MAX_LENGTH) {
			throw new CommentRejectedException("Comments can be at most " + MAX_LENGTH + " characters long.");
		}
		return trimmed;
	}

}
