package app.adventr.adventure;

/**
 * 👍 or 👎. A user has at most one reaction per adventure.
 */
public enum ReactionType {

	UP("👍", "Thumbs up"), DOWN("👎", "Thumbs down");

	private final String emoji;

	private final String label;

	ReactionType(String emoji, String label) {
		this.emoji = emoji;
		this.label = label;
	}

	public String emoji() {
		return this.emoji;
	}

	public String label() {
		return this.label;
	}

}
