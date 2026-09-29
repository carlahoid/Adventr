package app.adventr.adventure;

import java.time.ZonedDateTime;

import app.adventr.group.Author;

/**
 * One comment as a member sees it. Only the author may edit; the author and the group Owner
 * may delete.
 */
public record CommentView(long id, long groupId, long adventureId, Author author, String text,
		ZonedDateTime createdAt, ZonedDateTime editedAt, boolean canEdit, boolean canDelete) {

	public boolean edited() {
		return this.editedAt != null;
	}

}
