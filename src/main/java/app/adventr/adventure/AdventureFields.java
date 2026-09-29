package app.adventr.adventure;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The editable fields of an adventure after validation: trimmed, blank optional fields as
 * {@code null}, and every rule of the adventures spec checked.
 */
public record AdventureFields(String title, String description, String location, LocalDate dateFrom,
		LocalDate dateTo, String timeHint, BigDecimal costAmount, String costNote, String link) {

	public static final int MAX_TITLE = 120;

	public static final int MAX_DESCRIPTION = 5000;

	public static final int MAX_LOCATION = 200;

	public static final int MAX_TIME_HINT = 100;

	public static final int MAX_COST_NOTE = 100;

	public static final int MAX_LINK = 2000;

	/** {@code numeric(10, 2)}: below 100 million, at most two decimals. */
	private static final BigDecimal MAX_COST = new BigDecimal("100000000");

	/**
	 * Checks all fields at once, so that the form can show every problem together.
	 * @throws AdventureValidationException with one message per invalid field
	 */
	public static AdventureFields validate(AdventureForm form) {
		Map<String, String> errors = new LinkedHashMap<>();
		String title = text(form.title());
		if (title == null || title.length() > MAX_TITLE) {
			errors.put("title", "Please enter a title of 1 to " + MAX_TITLE + " characters.");
		}
		String description = optional(form.description(), MAX_DESCRIPTION, "description", "The description", errors);
		String location = optional(form.location(), MAX_LOCATION, "location", "The location", errors);
		String timeHint = optional(form.timeHint(), MAX_TIME_HINT, "timeHint", "The time", errors);
		String costNote = optional(form.costNote(), MAX_COST_NOTE, "costNote", "The cost note", errors);
		LocalDate dateFrom = date(form.dateFrom(), "dateFrom", errors);
		LocalDate dateTo = date(form.dateTo(), "dateTo", errors);
		if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
			errors.put("dateTo", "The end date can't be before the start date.");
		}
		BigDecimal costAmount = cost(form.costAmount(), errors);
		String link = link(form.link(), errors);
		if (!errors.isEmpty()) {
			throw new AdventureValidationException(errors);
		}
		return new AdventureFields(title, description, location, dateFrom, dateTo, timeHint, costAmount, costNote,
				link);
	}

	/**
	 * Trimmed text with Windows line breaks normalized, or {@code null} when blank.
	 */
	private static String text(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.replace("\r\n", "\n").strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String optional(String value, int max, String field, String label, Map<String, String> errors) {
		String text = text(value);
		if (text != null && text.length() > max) {
			errors.put(field, label + " can be at most " + max + " characters long.");
		}
		return text;
	}

	private static LocalDate date(String value, String field, Map<String, String> errors) {
		String text = text(value);
		if (text == null) {
			return null;
		}
		try {
			return LocalDate.parse(text);
		}
		catch (DateTimeParseException ex) {
			errors.put(field, "Please enter a valid date.");
			return null;
		}
	}

	private static BigDecimal cost(String value, Map<String, String> errors) {
		String text = text(value);
		if (text == null) {
			return null;
		}
		try {
			BigDecimal amount = new BigDecimal(text.replace(',', '.'));
			if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(MAX_COST) >= 0) {
				errors.put("costAmount", "Please enter an amount of 0 or more, with at most two decimals.");
				return null;
			}
			return amount;
		}
		catch (NumberFormatException ex) {
			errors.put("costAmount", "Please enter a number, e.g. 25 or 12.50.");
			return null;
		}
	}

	/**
	 * Only absolute http and https URLs, so that a stored link can never run script
	 * ({@code javascript:}) or open another scheme.
	 */
	private static String link(String value, Map<String, String> errors) {
		String text = text(value);
		if (text == null) {
			return null;
		}
		String message = "Please enter a web address starting with http:// or https://.";
		if (text.length() > MAX_LINK) {
			errors.put("link", "The link can be at most " + MAX_LINK + " characters long.");
			return null;
		}
		try {
			URI uri = new URI(text);
			String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
			if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
				errors.put("link", message);
				return null;
			}
			return text;
		}
		catch (URISyntaxException ex) {
			errors.put("link", message);
			return null;
		}
	}

}
