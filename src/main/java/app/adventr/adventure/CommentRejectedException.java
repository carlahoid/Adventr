package app.adventr.adventure;

/**
 * The comment text is blank or too long. The message is shown next to the comment box.
 */
public class CommentRejectedException extends RuntimeException {

	public CommentRejectedException(String message) {
		super(message);
	}

}
