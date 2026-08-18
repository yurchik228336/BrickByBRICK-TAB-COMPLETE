package dev.bbbt.data;

public record JournalSnapshot(int[] x, int[] y, int[] z, String[] names,
		int[] orientations, int[] sessions, int[] timeSeconds, int size) {

	public boolean sameSession(int a, int b) {
		return sessions[a] == sessions[b];
	}

	public int gapSeconds(int earlier, int later) {
		return Math.max(0, timeSeconds[later] - timeSeconds[earlier]);
	}
}
