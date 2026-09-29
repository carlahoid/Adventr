package app.adventr.adventure;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

import app.adventr.group.Author;

/**
 * One adventure as a member sees it on the detail page. {@code canEdit} is true for the
 * creator, {@code canDelete} for the creator and the group Owner.
 */
public record AdventureDetail(long id, long groupId, String title, String description, String location,
		LocalDate dateFrom, LocalDate dateTo, String timeHint, BigDecimal costAmount, String costNote, String link,
		String imageVersion, Status status, Author creator, ZonedDateTime createdAt, ZonedDateTime updatedAt,
		boolean canEdit, boolean canDelete) {

	public boolean hasImage() {
		return this.imageVersion != null;
	}

	public boolean hasCost() {
		return this.costAmount != null || this.costNote != null;
	}

	public boolean wasUpdated() {
		return this.updatedAt.isAfter(this.createdAt);
	}

}
