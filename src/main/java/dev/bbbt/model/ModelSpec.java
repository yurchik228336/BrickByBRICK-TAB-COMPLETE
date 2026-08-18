package dev.bbbt.model;

public final class ModelSpec {
	private ModelSpec() {
	}

	public static final int FORMAT_VERSION = 1;

	public static final int MAX_CONTEXT = 64;

	public static final int GRID = 16;
	public static final int GRID_VOLUME = GRID * GRID * GRID;
	public static final int OFFSET_MIN = -8;
	public static final int OFFSET_MAX = 7;

	public static final int OFFSET_BUCKETS = GRID;

	public static final int RECENCY_BUCKETS = 16;

	public static final int MODE_POSITION = 0;
	public static final int MODE_BLOCK = 1;
	public static final int MODE_COUNT = 2;

	public static boolean inGrid(int dx, int dy, int dz) {
		return dx >= OFFSET_MIN && dx <= OFFSET_MAX
				&& dy >= OFFSET_MIN && dy <= OFFSET_MAX
				&& dz >= OFFSET_MIN && dz <= OFFSET_MAX;
	}

	public static int gridIndex(int dx, int dy, int dz) {
		int x = dx - OFFSET_MIN;
		int y = dy - OFFSET_MIN;
		int z = dz - OFFSET_MIN;
		return (x * GRID + y) * GRID + z;
	}

	public static int gridDx(int index) {
		return index / (GRID * GRID) + OFFSET_MIN;
	}

	public static int gridDy(int index) {
		return (index / GRID) % GRID + OFFSET_MIN;
	}

	public static int gridDz(int index) {
		return index % GRID + OFFSET_MIN;
	}

	public static int offsetBucket(int delta) {
		int clamped = Math.clamp(delta, OFFSET_MIN, OFFSET_MAX);
		return clamped - OFFSET_MIN;
	}

	public static int recencyBucket(int placementsAgo) {
		if (placementsAgo < 0) {
			return 0;
		}
		int bucket = 1 + (31 - Integer.numberOfLeadingZeros(placementsAgo + 1));
		return Math.min(bucket, RECENCY_BUCKETS - 1);
	}
}
