package app.adventr.account;

import java.util.Locale;

import app.adventr.user.Theme;

/**
 * What the layout needs to render a page in the user's theme and colors from the first byte:
 * the {@code data-theme} attribute and the inline accent variables for {@code <html>}.
 */
public record Appearance(Theme theme, AccentColors.Derived colors) {

	/** System theme and default color, e.g. for logged-out visitors. */
	public static final Appearance DEFAULT = of(Theme.SYSTEM, null);

	/**
	 * @param accentColor the stored color, {@code null} for the default
	 */
	public static Appearance of(Theme theme, String accentColor) {
		return new Appearance((theme != null) ? theme : Theme.SYSTEM, AccentColors.derive(accentColor));
	}

	/**
	 * {@code light} or {@code dark}; {@code null} for the system theme, so that the stylesheet's
	 * {@code prefers-color-scheme} query decides.
	 */
	public String dataTheme() {
		return (this.theme == Theme.SYSTEM) ? null : this.theme.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The inline {@code style} for {@code <html>}: server-computed hex values only.
	 */
	public String style() {
		return this.colors.style();
	}

}
