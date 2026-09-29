package app.adventr.user;

/**
 * The user's color theme. {@link #SYSTEM} follows the operating system's light/dark setting.
 */
public enum Theme {

	LIGHT("Light"), DARK("Dark"), SYSTEM("System");

	private final String label;

	Theme(String label) {
		this.label = label;
	}

	public String label() {
		return this.label;
	}

}
