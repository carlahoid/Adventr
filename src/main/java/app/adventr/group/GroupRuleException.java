package app.adventr.group;

/**
 * A permitted user asked for something the group rules refuse, e.g. the Owner leaving while
 * others remain. Controllers show the message to the user instead of an error page.
 */
public class GroupRuleException extends RuntimeException {

	public GroupRuleException(String message) {
		super(message);
	}

}
