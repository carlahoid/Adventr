package app.adventr.adventure;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * No such adventure (or comment, or image) in this group, including ids that belong to another
 * group. Mapped to 404, like a group the user has no access to.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AdventureNotFoundException extends RuntimeException {

	public AdventureNotFoundException(String message) {
		super(message);
	}

}
