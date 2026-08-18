package dev.bbbt.suggest;

public record Suggestion(int x, int y, int z, String blockName, int orientation,
		float confidence, Source source) {

	public enum Source {
		MODEL,

		HEURISTIC
	}
}
