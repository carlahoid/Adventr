package app.adventr.adventure;

import java.util.List;

/**
 * The group's adventures in their three sections, each already in display order.
 */
public record AdventureList(List<AdventureItem> planned, List<AdventureItem> ideas, List<AdventureItem> memories) {

	public boolean isEmpty() {
		return this.planned.isEmpty() && this.ideas.isEmpty() && this.memories.isEmpty();
	}

}
