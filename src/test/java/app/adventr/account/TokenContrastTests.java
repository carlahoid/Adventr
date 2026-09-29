package app.adventr.account;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import app.adventr.account.AccentColors.Derived;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the design tokens in {@code app.css}: declared foreground/background pairs meet WCAG AA
 * in both themes, and no component style uses a color literal. A stylesheet change that breaks
 * contrast fails the build here.
 */
class TokenContrastTests {

	/** Text on a background: 4.5:1. */
	private static final List<Pair> TEXT_PAIRS = List.of(new Pair("fg", "bg"), new Pair("fg", "surface"),
			new Pair("fg", "surface-2"), new Pair("muted", "bg"), new Pair("muted", "surface"),
			new Pair("muted", "surface-2"), new Pair("accent-text", "bg"), new Pair("accent-text", "surface"),
			new Pair("accent-fg", "accent"), new Pair("danger-text", "bg"), new Pair("danger-text", "surface"),
			new Pair("danger-fg", "danger"), new Pair("notice-fg", "notice-bg"), new Pair("error-fg", "error-bg"));

	/** UI component boundaries, fills, and the focus ring: 3:1. */
	private static final List<Pair> UI_PAIRS = List.of(new Pair("border-strong", "bg"),
			new Pair("border-strong", "surface"), new Pair("accent", "bg"), new Pair("accent", "surface"),
			new Pair("focus-ring", "bg"), new Pair("focus-ring", "surface"), new Pair("danger", "bg"),
			new Pair("danger", "surface"));

	private static final Pattern DECLARATION = Pattern.compile("--([a-z0-9-]+)\\s*:\\s*([^;]+);");

	private static final Pattern VAR = Pattern.compile("^var\\(--([a-z0-9-]+)\\)$");

	private static final String CSS = stylesheet();

	@Test
	void textPairsReachFourAndAHalfToOneInBothThemes() {
		for (Map<String, String> theme : List.of(light(), dark())) {
			for (Pair pair : TEXT_PAIRS) {
				assertThat(contrast(theme, pair)).as(pair + " in " + theme.get("bg")).isGreaterThanOrEqualTo(4.5);
			}
		}
	}

	@Test
	void uiPairsReachThreeToOneInBothThemes() {
		for (Map<String, String> theme : List.of(light(), dark())) {
			for (Pair pair : UI_PAIRS) {
				assertThat(contrast(theme, pair)).as(pair + " in " + theme.get("bg")).isGreaterThanOrEqualTo(3.0);
			}
		}
	}

	@Test
	void bothDarkBlocksAreIdentical() {
		assertThat(block(":root:not([data-theme=\"light\"])")).isEqualTo(block(":root[data-theme=\"dark\"]"));
	}

	@Test
	void noColorLiteralsOutsideTheTokenBlocks() {
		String components = CSS.substring(CSS.indexOf("/* End of token blocks. */"));
		assertThat(components).doesNotContainPattern("#[0-9a-fA-F]{3,8}\\b")
			.doesNotContainPattern("\\b(rgb|rgba|hsl|hsla|oklch|oklab|color-mix)\\(")
			.doesNotContainPattern(":\\s*(white|black|red|green|blue|gray|grey)\\b");
	}

	@Test
	void accentColorsUsesTheStylesheetBackgrounds() {
		assertThat(light().get("bg")).isEqualTo(AccentColors.LIGHT.bg());
		assertThat(light().get("surface")).isEqualTo(AccentColors.LIGHT.surface());
		assertThat(dark().get("bg")).isEqualTo(AccentColors.DARK.bg());
		assertThat(dark().get("surface")).isEqualTo(AccentColors.DARK.surface());
		assertThat(light().get("fg")).isEqualTo(AccentColors.LIGHT.fg());
		assertThat(dark().get("fg")).isEqualTo(AccentColors.DARK.fg());
	}

	@Test
	void stylesheetAccentDefaultsAreTheDerivedDefaultColor() {
		Derived derived = AccentColors.derive(null);
		Map<String, String> light = light();

		assertThat(light.get("accent-l")).isEqualTo(derived.light().accent());
		assertThat(light.get("accent-fg-l")).isEqualTo(derived.light().accentFg());
		assertThat(light.get("accent-text-l")).isEqualTo(derived.light().accentText());
		assertThat(light.get("accent-d")).isEqualTo(derived.dark().accent());
		assertThat(light.get("accent-fg-d")).isEqualTo(derived.dark().accentFg());
		assertThat(light.get("accent-text-d")).isEqualTo(derived.dark().accentText());
	}

	private static double contrast(Map<String, String> theme, Pair pair) {
		return AccentColors.contrast(theme.get(pair.foreground()), theme.get(pair.background()));
	}

	/**
	 * The light tokens, with {@code var()} references resolved.
	 */
	private static Map<String, String> light() {
		return resolve(block(":root"));
	}

	/**
	 * The light tokens overridden by the dark block, resolved.
	 */
	private static Map<String, String> dark() {
		Map<String, String> tokens = new LinkedHashMap<>(block(":root"));
		tokens.putAll(block(":root[data-theme=\"dark\"]"));
		return resolve(tokens);
	}

	/**
	 * The custom properties declared directly in the rule with this exact selector.
	 */
	private static Map<String, String> block(String selector) {
		int start = CSS.indexOf("\n" + selector + " {");
		if (start < 0) {
			start = CSS.indexOf("\t" + selector + " {");
		}
		assertThat(start).as("rule " + selector).isGreaterThanOrEqualTo(0);
		int open = CSS.indexOf('{', start);
		String body = CSS.substring(open + 1, CSS.indexOf('}', open));
		Map<String, String> tokens = new LinkedHashMap<>();
		Matcher matcher = DECLARATION.matcher(body);
		while (matcher.find()) {
			tokens.put(matcher.group(1), matcher.group(2).trim());
		}
		return tokens;
	}

	private static Map<String, String> resolve(Map<String, String> tokens) {
		Map<String, String> resolved = new LinkedHashMap<>();
		tokens.forEach((name, value) -> {
			String current = value;
			for (int depth = 0; depth < 5; depth++) {
				Matcher reference = VAR.matcher(current);
				if (!reference.matches()) {
					break;
				}
				current = tokens.get(reference.group(1));
			}
			resolved.put(name, current);
		});
		return resolved;
	}

	private static String stylesheet() {
		try (InputStream in = TokenContrastTests.class.getResourceAsStream("/static/css/app.css")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record Pair(String foreground, String background) {

		@Override
		public String toString() {
			return "--" + this.foreground + " on --" + this.background;
		}

	}

}
