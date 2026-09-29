package app.adventr.adventure;

import java.time.LocalDate;

/**
 * One adventure in the group's list. {@code imageVersion} is {@code null} without an image.
 */
public record AdventureItem(long id, String title, Status status, LocalDate dateFrom, LocalDate dateTo,
		String imageVersion, long commentCount, ReactionBar reactions) {

	public boolean hasImage() {
		return this.imageVersion != null;
	}

}
