package app.adventr.adventure;

import java.util.Map;

/**
 * The submitted adventure fields break one or more rules. Controllers show the messages next
 * to the fields instead of an error page.
 */
public class AdventureValidationException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public AdventureValidationException(Map<String, String> fieldErrors) {
		super("Invalid adventure: " + fieldErrors.keySet());
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	/**
	 * Message per form field name, e.g. {@code "title" -> "Please enter a title…"}.
	 */
	public Map<String, String> fieldErrors() {
		return this.fieldErrors;
	}

}
