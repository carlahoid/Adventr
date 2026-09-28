package app.adventr.group;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The user is not an active member of the group (or it does not exist). Mapped to 404, so that
 * outsiders cannot tell whether a group exists.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class GroupAccessDeniedException extends RuntimeException {

	public GroupAccessDeniedException(long groupId) {
		super("No access to group " + groupId);
	}

}
