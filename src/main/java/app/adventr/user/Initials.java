package app.adventr.user;

import java.util.Locale;

/**
 * The letters of the avatar placeholder: the first letter of the first and the last word of a
 * name ("Kim the Climber" → "KC"), or one letter for a single word.
 */
public final class Initials {

	private Initials() {
	}

	public static String of(String name) {
		if (name == null || name.isBlank()) {
			return "?";
		}
		String[] words = name.strip().split("\\s+");
		String first = firstLetter(words[0]);
		return (words.length == 1) ? first : first + firstLetter(words[words.length - 1]);
	}

	private static String firstLetter(String word) {
		return new String(Character.toChars(word.codePointAt(0))).toUpperCase(Locale.ROOT);
	}

}
