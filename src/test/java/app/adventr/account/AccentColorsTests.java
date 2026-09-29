package app.adventr.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.junit.jupiter.api.Test;

import app.adventr.account.AccentColors.ThemeColors;
import app.adventr.account.AccentColors.Derived;
import app.adventr.account.AccentColors.Variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AccentColorsTests {

	@Test
	void randomColorsMeetAllThresholdsInBothThemes() {
		Random random = new Random(20260929);
		List<String> colors = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			colors.add(String.format(Locale.ROOT, "#%06x", random.nextInt(0x1000000)));
		}
		colors.forEach(AccentColorsTests::assertThresholds);
	}

	@Test
	void edgeCasesMeetAllThresholdsInBothThemes() {
		List.of("#ffff00", "#0000ff", "#000000", "#ffffff", "#ff0000", "#00ff00", "#808080", "#0a0a40",
				AccentColors.LIGHT.bg(), AccentColors.LIGHT.surface(), AccentColors.DARK.bg(),
				AccentColors.DARK.surface())
			.forEach(AccentColorsTests::assertThresholds);
	}

	@Test
	void veryLightColorIsDarkenedWithBlackTextInTheLightTheme() {
		Variants light = AccentColors.derive("#ffff00").light();

		assertThat(light.accent()).isNotEqualTo("#ffff00");
		assertThat(light.accentFg()).isEqualTo("#000000");
		assertThat(light.adjusted()).isTrue();
		assertSameHue("#ffff00", light.accentText());
	}

	@Test
	void veryDarkColorIsLightenedInTheDarkTheme() {
		Derived navy = AccentColors.derive("#0a0a40");

		assertThat(navy.light().accent()).isEqualTo("#0a0a40");
		assertThat(navy.light().adjusted()).isFalse();
		assertThat(AccentColors.luminance(navy.dark().accentText())).isGreaterThan(AccentColors.luminance("#0a0a40"));
		assertThat(navy.dark().adjusted()).isTrue();
		assertSameHue("#0a0a40", navy.dark().accentText());
	}

	@Test
	void presetsAreUnchangedInLightAndOnlyLightenedInDark() {
		assertThat(AccentPreset.ALL).hasSize(8);
		assertThat(AccentPreset.ALL.getFirst().hex()).isEqualTo(AccentColors.DEFAULT);
		for (AccentPreset preset : AccentPreset.ALL) {
			Derived derived = AccentColors.derive(preset.hex());
			assertThresholds(preset.hex());
			assertThat(derived.light().accent()).as(preset.name()).isEqualTo(preset.hex());
			assertThat(derived.light().accentText()).as(preset.name()).isEqualTo(preset.hex());
			assertThat(derived.light().adjusted()).as(preset.name()).isFalse();
			// No single color reaches 4.5:1 on both the light page and the dark card, so the dark
			// text variant is always lighter; it must stay recognizably the same color.
			for (String dark : List.of(derived.dark().accent(), derived.dark().accentText())) {
				assertSameHue(preset.hex(), dark);
				assertThat(chroma(dark)).as(preset.name()).isGreaterThanOrEqualTo(0.8 * chroma(preset.hex()));
			}
		}
	}

	@Test
	void theStoredColorIsNeverChanged() {
		assertThat(AccentColors.derive("#FFFF00").picked()).isEqualTo("#ffff00");
		assertThat(AccentColors.derive(null).picked()).isEqualTo(AccentColors.DEFAULT);
		assertThat(AccentColors.derive("#c2185b").picked()).isEqualTo("#c2185b");
	}

	@Test
	void onlySixDigitHexColorsAreAccepted() {
		assertThat(AccentColors.normalize(" #C2185B ")).contains("#c2185b");
		assertThat(AccentColors.normalize("red;background:url(x)")).isEmpty();
		assertThat(AccentColors.normalize("#12345")).isEmpty();
		assertThat(AccentColors.normalize("#1234567")).isEmpty();
		assertThat(AccentColors.normalize("#12g456")).isEmpty();
		assertThat(AccentColors.normalize("123456")).isEmpty();
		assertThat(AccentColors.normalize(null)).isEmpty();
		assertThatIllegalArgumentException().isThrownBy(() -> AccentColors.derive("red"));
	}

	@Test
	void inlineStyleContainsOnlyComputedHexValues() {
		assertThat(AccentColors.derive("#ffff00").style())
			.matches("(--accent(-fg|-text)?-[ld]:#[0-9a-f]{6};?){6}");
	}

	private static void assertThresholds(String color) {
		Derived derived = AccentColors.derive(color);
		assertVariants(color, "light", derived.light(), AccentColors.LIGHT);
		assertVariants(color, "dark", derived.dark(), AccentColors.DARK);
	}

	private static void assertVariants(String color, String theme, Variants variants, ThemeColors backgrounds) {
		String what = color + " in " + theme + ": " + variants;
		for (String background : List.of(backgrounds.bg(), backgrounds.surface())) {
			assertThat(AccentColors.contrast(variants.accent(), background)).as(what)
				.isGreaterThanOrEqualTo(AccentColors.UI_CONTRAST);
			assertThat(AccentColors.contrast(variants.accentText(), background)).as(what)
				.isGreaterThanOrEqualTo(AccentColors.TEXT_CONTRAST);
		}
		assertThat(AccentColors.contrast(variants.accentFg(), variants.accent())).as(what)
			.isGreaterThanOrEqualTo(AccentColors.TEXT_CONTRAST);
		assertThat(variants.accentFg()).isIn("#000000", "#ffffff");
	}

	private static void assertSameHue(String picked, String derived) {
		double difference = Math.abs(Math.toDegrees(AccentColors.toOklch(picked)[2] - AccentColors.toOklch(derived)[2]));
		assertThat(Math.min(difference, 360 - difference)).as(picked + " → " + derived).isLessThan(10);
	}

	private static double chroma(String hex) {
		return AccentColors.toOklch(hex)[1];
	}

}
