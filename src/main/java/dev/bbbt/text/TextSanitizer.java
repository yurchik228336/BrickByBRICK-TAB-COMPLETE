package dev.bbbt.text;

import java.util.regex.Pattern;

public final class TextSanitizer {

	public static final int MAX_LENGTH = 300;
	private static final int MIN_LENGTH = 3;

	private static final Pattern EMAIL = Pattern.compile(
			"[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
	private static final Pattern URL = Pattern.compile(
			"(?i)\\b(?:https?://|www\\.)\\S+");
	private static final Pattern IP = Pattern.compile(
			"\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b(?::\\d+)?");

	private static final Pattern HANDLE = Pattern.compile(
			"(?:@[\\w.]{3,}|\\b\\w+#\\d{4}\\b)");

	private static final Pattern COORDS = Pattern.compile(
			"-?\\d{3,}\\s*[,/ ]\\s*-?\\d{1,4}\\s*[,/ ]\\s*-?\\d{3,}");

	private static final Pattern LONG_DIGITS = Pattern.compile("\\d{7,}");
	private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private static final Pattern FORMATTING = Pattern.compile("\u00a7.");

	public sealed interface Result {
		record Accepted(String text, boolean redacted) implements Result {
		}

		record Rejected(String reason) implements Result {
		}
	}

	public Result sanitize(String raw) {
		if (raw == null) {
			return new Result.Rejected("empty");
		}

		String text = FORMATTING.matcher(raw).replaceAll("");
		text = CONTROL.matcher(text).replaceAll(" ");

		String before = text;
		text = EMAIL.matcher(text).replaceAll(" ");
		text = URL.matcher(text).replaceAll(" ");
		text = IP.matcher(text).replaceAll(" ");
		text = HANDLE.matcher(text).replaceAll(" ");
		text = COORDS.matcher(text).replaceAll(" ");
		text = LONG_DIGITS.matcher(text).replaceAll(" ");
		boolean redacted = !before.equals(text);

		text = WHITESPACE.matcher(text).replaceAll(" ").trim();

		if (text.length() < MIN_LENGTH) {
			return new Result.Rejected("too_short");
		}
		if (text.length() > MAX_LENGTH) {
			text = text.substring(0, MAX_LENGTH).trim();
		}
		if (!hasEnoughLetters(text)) {
			return new Result.Rejected("not_prose");
		}
		if (isSingleRepeatedCharacter(text)) {
			return new Result.Rejected("spam");
		}
		return new Result.Accepted(text, redacted);
	}

	private static boolean hasEnoughLetters(String text) {
		int letters = 0;
		for (int i = 0; i < text.length(); i++) {
			if (Character.isLetter(text.charAt(i))) {
				letters++;
			}
		}
		return letters >= text.length() * 0.5 && letters >= MIN_LENGTH;
	}

	private static boolean isSingleRepeatedCharacter(String text) {
		char first = text.charAt(0);
		for (int i = 1; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != first && !Character.isWhitespace(c)) {
				return false;
			}
		}
		return true;
	}
}
