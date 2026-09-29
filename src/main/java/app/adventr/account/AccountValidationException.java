package app.adventr.account;

import java.util.Map;

/**
 * Submitted account settings break a rule. Controllers show the messages next to the fields.
 */
public class AccountValidationException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public AccountValidationException(Map<String, String> fieldErrors) {
		super("Invalid account settings: " + fieldErrors.keySet());
		this.fieldErrors = Map.copyOf(fieldErrors);
	}

	public Map<String, String> fieldErrors() {
		return this.fieldErrors;
	}

}
