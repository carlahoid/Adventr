package app.adventr.account;

import java.util.List;

/**
 * A named primary color on the appearance form. Presets are plain hex values: picking a preset
 * and entering the same color by hand are the same thing. Each one works unchanged in the light
 * theme; in the dark theme only its lightness is raised (see {@code AccentColorsTests}).
 */
public record AccentPreset(String name, String hex) {

	public static final List<AccentPreset> ALL = List.of(new AccentPreset("Green", AccentColors.DEFAULT),
			new AccentPreset("Teal", "#0f766e"), new AccentPreset("Blue", "#1d4ed8"),
			new AccentPreset("Indigo", "#4f46e5"), new AccentPreset("Purple", "#7c3aed"),
			new AccentPreset("Rose", "#be185d"), new AccentPreset("Red", "#c0262d"),
			new AccentPreset("Orange", "#c2410c"));

}
