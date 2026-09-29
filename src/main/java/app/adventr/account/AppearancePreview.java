package app.adventr.account;

import java.util.List;

/**
 * The live preview of a color being picked: a sample button, link, and focus ring in the light
 * and in the dark theme, using the derived colors. {@code valid} is false for anything that is
 * not {@code #rrggbb}; nothing is derived from such input.
 */
public record AppearancePreview(boolean valid, String picked, List<Panel> panels) {

	public static AppearancePreview of(String color) {
		return AccentColors.normalize(color).map((hex) -> {
			AccentColors.Derived derived = AccentColors.derive(hex);
			return new AppearancePreview(true, hex, List.of(new Panel("Light", AccentColors.LIGHT, derived.light()),
					new Panel("Dark", AccentColors.DARK, derived.dark())));
		}).orElseGet(() -> new AppearancePreview(false, null, List.of()));
	}

	/**
	 * One theme's sample. The style holds only computed hex values.
	 */
	public record Panel(String label, AccentColors.ThemeColors theme, AccentColors.Variants colors) {

		public boolean adjusted() {
			return this.colors.adjusted();
		}

		public String style() {
			return "--p-bg:" + this.theme.bg() + ";--p-surface:" + this.theme.surface() + ";--p-fg:" + this.theme.fg()
					+ ";--p-accent:" + this.colors.accent() + ";--p-accent-fg:" + this.colors.accentFg()
					+ ";--p-accent-text:" + this.colors.accentText();
		}

	}

}
