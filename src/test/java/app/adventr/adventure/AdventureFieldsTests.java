package app.adventr.adventure;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AdventureFieldsTests {

	@Test
	void titleOnlyIsEnoughAndBlankOptionalFieldsBecomeNull() {
		AdventureFields fields = AdventureFields
			.validate(new AdventureForm("  Canoe trip ", " ", "", null, "", "  ", "", "", ""));

		assertThat(fields.title()).isEqualTo("Canoe trip");
		assertThat(fields.description()).isNull();
		assertThat(fields.location()).isNull();
		assertThat(fields.dateFrom()).isNull();
		assertThat(fields.costAmount()).isNull();
		assertThat(fields.link()).isNull();
	}

	@Test
	void allFieldsAreParsed() {
		AdventureFields fields = AdventureFields.validate(new AdventureForm("Canoe trip", "Line 1\r\nLine 2",
				"Lake Tahoe", "2026-10-12", "2026-10-14", "early morning", "45,50", "per person",
				"https://example.com/trip?x=1"));

		assertThat(fields.description()).isEqualTo("Line 1\nLine 2");
		assertThat(fields.dateFrom()).isEqualTo(LocalDate.of(2026, 10, 12));
		assertThat(fields.dateTo()).isEqualTo(LocalDate.of(2026, 10, 14));
		assertThat(fields.costAmount()).isEqualByComparingTo(new BigDecimal("45.50"));
		assertThat(fields.link()).isEqualTo("https://example.com/trip?x=1");
	}

	@Test
	void missingOrTooLongTitleIsRejected() {
		assertThat(errors(AdventureForm.titleOnly("   "))).containsKey("title");
		assertThat(errors(AdventureForm.titleOnly("x".repeat(121)))).containsKey("title");
		assertThat(AdventureFields.validate(AdventureForm.titleOnly("x".repeat(120))).title()).hasSize(120);
	}

	@Test
	void endDateBeforeStartDateIsRejected() {
		assertThat(errors(new AdventureForm("Trip", null, null, "2026-10-14", "2026-10-12", null, null, null, null)))
			.containsEntry("dateTo", "The end date can't be before the start date.");
		AdventureFields sameDay = AdventureFields
			.validate(new AdventureForm("Trip", null, null, "2026-10-12", "2026-10-12", null, null, null, null));
		assertThat(sameDay.dateTo()).isEqualTo(sameDay.dateFrom());
	}

	@Test
	void onlyHttpAndHttpsLinksAreAccepted() {
		assertThat(errors(link("javascript:alert(1)"))).containsKey("link");
		assertThat(errors(link("data:text/html,<script>alert(1)</script>"))).containsKey("link");
		assertThat(errors(link("ftp://example.com/file"))).containsKey("link");
		assertThat(errors(link("example.com"))).containsKey("link");
		assertThat(errors(link("https:///no-host"))).containsKey("link");
		assertThat(AdventureFields.validate(link("HTTP://Example.com")).link()).isEqualTo("HTTP://Example.com");
	}

	@Test
	void costMustBeANonNegativeAmount() {
		assertThat(errors(cost("-1"))).containsKey("costAmount");
		assertThat(errors(cost("12.345"))).containsKey("costAmount");
		assertThat(errors(cost("lots"))).containsKey("costAmount");
		assertThat(errors(cost("100000000"))).containsKey("costAmount");
		assertThat(AdventureFields.validate(cost("0")).costAmount()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void fieldLengthsAreLimited() {
		AdventureForm form = new AdventureForm("Trip", "d".repeat(5001), "l".repeat(201), null, null,
				"t".repeat(101), null, "n".repeat(101), "https://example.com/" + "p".repeat(2000));

		assertThat(errors(form)).containsOnlyKeys("description", "location", "timeHint", "costNote", "link");
	}

	@Test
	void invalidDatesAreReported() {
		assertThat(errors(new AdventureForm("Trip", null, null, "12.10.2026", null, null, null, null, null)))
			.containsKey("dateFrom");
	}

	private static java.util.Map<String, String> errors(AdventureForm form) {
		return assertThatExceptionOfType(AdventureValidationException.class)
			.isThrownBy(() -> AdventureFields.validate(form))
			.actual()
			.fieldErrors();
	}

	private static AdventureForm link(String link) {
		return new AdventureForm("Trip", null, null, null, null, null, null, null, link);
	}

	private static AdventureForm cost(String cost) {
		return new AdventureForm("Trip", null, null, null, null, null, cost, null, null);
	}

}
