package app.adventr.adventure;

/**
 * The adventure form as submitted: raw text, so that an unparsable date or amount comes back as
 * a field error instead of a binding failure. {@link AdventureFields#validate} turns it into
 * checked values.
 */
public record AdventureForm(String title, String description, String location, String dateFrom, String dateTo,
		String timeHint, String costAmount, String costNote, String link) {

	/**
	 * Just a title, as the quick-add form sends it.
	 */
	public static AdventureForm titleOnly(String title) {
		return new AdventureForm(title, null, null, null, null, null, null, null, null);
	}

	/**
	 * The form pre-filled with an adventure's current values, for the edit page.
	 */
	static AdventureForm of(Adventure adventure) {
		return new AdventureForm(adventure.getTitle(), adventure.getDescription(), adventure.getLocation(),
				toText(adventure.getDateFrom()), toText(adventure.getDateTo()), adventure.getTimeHint(),
				(adventure.getCostAmount() != null) ? adventure.getCostAmount().toPlainString() : null,
				adventure.getCostNote(), adventure.getLink());
	}

	private static String toText(Object value) {
		return (value != null) ? value.toString() : null;
	}

}
