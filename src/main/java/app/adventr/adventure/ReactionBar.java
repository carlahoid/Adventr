package app.adventr.adventure;

import java.util.List;
import java.util.stream.Collectors;

import app.adventr.group.Author;

/**
 * An adventure's reactions as one member sees them: separate counts, their own reaction (or
 * {@code null}), and who reacted, oldest reaction first.
 */
public record ReactionBar(long groupId, long adventureId, ReactionType mine, List<Author> up, List<Author> down) {

	public int ups() {
		return this.up.size();
	}

	public int downs() {
		return this.down.size();
	}

	public boolean mineUp() {
		return this.mine == ReactionType.UP;
	}

	public boolean mineDown() {
		return this.mine == ReactionType.DOWN;
	}

	/**
	 * "Anna, Ben (former member)" for the tooltip, or "No one yet".
	 */
	public String upNames() {
		return names(this.up);
	}

	public String downNames() {
		return names(this.down);
	}

	private static String names(List<Author> authors) {
		return authors.isEmpty() ? "No one yet"
				: authors.stream().map(Author::label).collect(Collectors.joining(", "));
	}

}
