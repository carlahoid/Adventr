package app.adventr.group;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A member attempted something their role or authorship does not allow, e.g. a Member
 * removing someone. Mapped to 403: the member may know the group exists.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenActionException extends RuntimeException {

	public ForbiddenActionException(String message) {
		super(message);
	}

}
