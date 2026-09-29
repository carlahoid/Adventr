package app.adventr.account;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Derives the colors actually used for a user's primary color, per theme, so that they keep the
 * picked hue and meet WCAG 2.x AA contrast against that theme's page ({@code --bg}) and card
 * ({@code --surface}) backgrounds:
 * <ul>
 * <li>{@code accent} (button fill, selected states): at least 3:1</li>
 * <li>{@code accent-fg} (text on the fill): black or white, at least 4.5:1 against the fill</li>
 * <li>{@code accent-text} (links, accent text, focus ring): at least 4.5:1</li>
 * </ul>
 * A color that misses a threshold is moved in OKLCH lightness towards black (light theme) or
 * white (dark theme), keeping hue and chroma; chroma is reduced only as far as needed to stay in
 * the sRGB gamut. Black and white clear every threshold against these backgrounds, so the
 * search always ends. The picked color itself is never changed; derivation happens per request.
 */
public final class AccentColors {

	/** The default primary color (green). Stored as {@code null} on the account. */
	public static final String DEFAULT = "#1f7a5c";

	/** Page, card, and text colors of the light theme; must match {@code app.css}. */
	public static final ThemeColors LIGHT = new ThemeColors("#faf8f5", "#ffffff", "#1f2328");

	/** Page, card, and text colors of the dark theme; must match {@code app.css}. */
	public static final ThemeColors DARK = new ThemeColors("#16181b", "#1f2226", "#e8e6e3");

	public static final double UI_CONTRAST = 3.0;

	public static final double TEXT_CONTRAST = 4.5;

	private static final Pattern HEX = Pattern.compile("^#[0-9a-f]{6}$");

	private static final double LIGHTNESS_STEP = 0.005;

	/** OKLab distance above which a derived color counts as visibly different from the pick. */
	private static final double NOTICEABLE = 0.04;

	private AccentColors() {
	}

	/**
	 * The color as stored: trimmed, lower case, {@code #rrggbb}; empty for anything else.
	 */
	public static Optional<String> normalize(String input) {
		if (input == null) {
			return Optional.empty();
		}
		String hex = input.trim().toLowerCase(Locale.ROOT);
		return HEX.matcher(hex).matches() ? Optional.of(hex) : Optional.empty();
	}

	/**
	 * The light and dark variants for a stored color ({@code null} means the default).
	 * @throws IllegalArgumentException if the color is not {@code #rrggbb}
	 */
	public static Derived derive(String picked) {
		String hex = (picked != null) ? normalize(picked)
			.orElseThrow(() -> new IllegalArgumentException("Not a #rrggbb color: " + picked)) : DEFAULT;
		return new Derived(hex, variants(hex, LIGHT, false), variants(hex, DARK, true));
	}

	private static Variants variants(String picked, ThemeColors backgrounds, boolean lighten) {
		String accent = adjust(picked, lighten,
				(hex) -> backgrounds.minContrast(hex) >= UI_CONTRAST && bestForeground(hex).contrast() >= TEXT_CONTRAST);
		String accentText = adjust(picked, lighten, (hex) -> backgrounds.minContrast(hex) >= TEXT_CONTRAST);
		return new Variants(accent, bestForeground(accent).hex(), accentText,
				distance(picked, accent) > NOTICEABLE || distance(picked, accentText) > NOTICEABLE);
	}

	/**
	 * The picked color if it passes; otherwise the first color along the lightness ramp that does.
	 */
	private static String adjust(String picked, boolean lighten, java.util.function.Predicate<String> passes) {
		if (passes.test(picked)) {
			return picked;
		}
		double[] lch = toOklch(picked);
		for (int step = 1;; step++) {
			double lightness = lch[0] + (lighten ? step : -step) * LIGHTNESS_STEP;
			if (lightness <= 0) {
				return "#000000";
			}
			if (lightness >= 1) {
				return "#ffffff";
			}
			String candidate = inGamut(lightness, lch[1], lch[2]);
			if (passes.test(candidate)) {
				return candidate;
			}
		}
	}

	/**
	 * Black or white, whichever contrasts more with the color.
	 */
	static Foreground bestForeground(String hex) {
		double black = contrast(hex, "#000000");
		double white = contrast(hex, "#ffffff");
		return (black >= white) ? new Foreground("#000000", black) : new Foreground("#ffffff", white);
	}

	/**
	 * WCAG 2.x contrast ratio, 1 to 21.
	 */
	public static double contrast(String a, String b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/**
	 * WCAG 2.x relative luminance.
	 */
	static double luminance(String hex) {
		double[] rgb = linearRgb(hex);
		return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
	}

	// OKLab / OKLCH (Björn Ottosson), on linear sRGB.

	static double[] toOklch(String hex) {
		double[] lab = toOklab(hex);
		double chroma = Math.hypot(lab[1], lab[2]);
		double hue = Math.atan2(lab[2], lab[1]);
		return new double[] { lab[0], chroma, hue };
	}

	private static double[] toOklab(String hex) {
		double[] c = linearRgb(hex);
		double l = Math.cbrt(0.4122214708 * c[0] + 0.5363325363 * c[1] + 0.0514459929 * c[2]);
		double m = Math.cbrt(0.2119034982 * c[0] + 0.6806995451 * c[1] + 0.1073969566 * c[2]);
		double s = Math.cbrt(0.0883024619 * c[0] + 0.2817188376 * c[1] + 0.6299787005 * c[2]);
		return new double[] { 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
				1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
				0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s };
	}

	/**
	 * Linear sRGB for an OKLCH color; values outside 0–1 mean out of gamut.
	 */
	private static double[] oklchToLinearRgb(double lightness, double chroma, double hue) {
		double a = chroma * Math.cos(hue);
		double b = chroma * Math.sin(hue);
		double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
		double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
		double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
		return new double[] { 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
				-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
				-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s };
	}

	/**
	 * The color at this lightness and hue with as much of the chroma as fits into sRGB.
	 */
	private static String inGamut(double lightness, double chroma, double hue) {
		double[] rgb = oklchToLinearRgb(lightness, chroma, hue);
		if (!fits(rgb)) {
			double low = 0;
			double high = chroma;
			for (int i = 0; i < 24; i++) {
				double mid = (low + high) / 2;
				if (fits(oklchToLinearRgb(lightness, mid, hue))) {
					low = mid;
				}
				else {
					high = mid;
				}
			}
			rgb = oklchToLinearRgb(lightness, low, hue);
		}
		return toHex(rgb);
	}

	private static boolean fits(double[] rgb) {
		double epsilon = 1e-6;
		for (double channel : rgb) {
			if (channel < -epsilon || channel > 1 + epsilon) {
				return false;
			}
		}
		return true;
	}

	private static double distance(String a, String b) {
		double[] x = toOklab(a);
		double[] y = toOklab(b);
		return Math.sqrt(Math.pow(x[0] - y[0], 2) + Math.pow(x[1] - y[1], 2) + Math.pow(x[2] - y[2], 2));
	}

	private static double[] linearRgb(String hex) {
		double[] rgb = new double[3];
		for (int i = 0; i < 3; i++) {
			double channel = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
			rgb[i] = (channel <= 0.04045) ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
		}
		return rgb;
	}

	private static String toHex(double[] linear) {
		StringBuilder hex = new StringBuilder("#");
		for (double channel : linear) {
			double clamped = Math.min(1, Math.max(0, channel));
			double srgb = (clamped <= 0.0031308) ? 12.92 * clamped : 1.055 * Math.pow(clamped, 1 / 2.4) - 0.055;
			hex.append(String.format(Locale.ROOT, "%02x", Math.round(srgb * 255)));
		}
		return hex.toString();
	}

	/**
	 * A theme's page and card backgrounds ({@code --bg}, {@code --surface}) and text color
	 * ({@code --fg}).
	 */
	public record ThemeColors(String bg, String surface, String fg) {

		double minContrast(String hex) {
			return Math.min(contrast(hex, this.bg), contrast(hex, this.surface));
		}

	}

	/**
	 * The colors used in one theme. {@code adjusted} is true when they differ visibly from the
	 * picked color.
	 */
	public record Variants(String accent, String accentFg, String accentText, boolean adjusted) {
	}

	/**
	 * A picked color with its light and dark variants.
	 */
	public record Derived(String picked, Variants light, Variants dark) {

		/**
		 * The inline custom properties for {@code <html style="…">}; only computed hex values.
		 */
		public String style() {
			return "--accent-l:" + this.light.accent() + ";--accent-fg-l:" + this.light.accentFg() + ";--accent-text-l:"
					+ this.light.accentText() + ";--accent-d:" + this.dark.accent() + ";--accent-fg-d:"
					+ this.dark.accentFg() + ";--accent-text-d:" + this.dark.accentText();
		}

	}

	record Foreground(String hex, double contrast) {
	}

}
