package app.adventr.adventure;

import java.util.List;

/**
 * An adventure's comments, oldest first. {@code editingId} is the comment shown as an edit form
 * on a page loaded without JavaScript ({@code ?editComment=…}), or {@code null}.
 */
public record CommentThread(long groupId, long adventureId, List<CommentView> comments, Long editingId) {

	public boolean isEditing(CommentView comment) {
		return this.editingId != null && this.editingId == comment.id() && comment.canEdit();
	}

}
