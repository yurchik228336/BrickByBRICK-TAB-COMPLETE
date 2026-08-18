package dev.bbbt.suggest;

import dev.bbbt.data.PlacementJournal;
import dev.bbbt.model.ModelSpec;
import dev.bbbt.palette.BlockPalette;
import dev.bbbt.store.PlacedBlockStore;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class ContextBuilder {

	public static final int RADIUS = ModelSpec.OFFSET_MAX + 1;

	private static final int RECENCY_LOOKBACK = 512;

	private final BlockPalette palette;

	private int[] scratchX = new int[256];
	private int[] scratchY = new int[256];
	private int[] scratchZ = new int[256];
	private String[] scratchName = new String[256];
	private int[] scratchOrientation = new int[256];
	private int[] scratchScore = new int[256];
	private int scratchCount;

	private final Map<Long, Integer> ageByPosition = new HashMap<>();

	public ContextBuilder(BlockPalette palette) {
		this.palette = palette;
	}

	public void build(BuildContext out, PlacedBlockStore store, PlacementJournal journal,
			BlockPos anchor) {
		build(out, store, journal, anchor, null);
	}

	public void build(BuildContext out, PlacedBlockStore store, PlacementJournal journal,
			BlockPos anchor, FocusBox focus) {
		out.reset(anchor.getX(), anchor.getY(), anchor.getZ());
		indexRecency(journal);

		scratchCount = 0;
		store.forEachInCube(anchor.getX(), anchor.getY(), anchor.getZ(), RADIUS,
				(x, y, z, name, orientation) -> collect(x, y, z, name, orientation, anchor, focus));

		int keep = Math.min(scratchCount, ModelSpec.MAX_CONTEXT);
		if (scratchCount > keep) {
			partialSortByScore(keep);
		}

		for (int i = 0; i < keep; i++) {
			int rawDx = scratchX[i] - out.anchorX;
			int rawDy = scratchY[i] - out.anchorY;
			int rawDz = scratchZ[i] - out.anchorZ;

			out.blockTokens[i] = palette.tokenOf(scratchName[i]);
			out.orientations[i] = scratchOrientation[i];
			out.dx[i] = ModelSpec.offsetBucket(rawDx);
			out.dy[i] = ModelSpec.offsetBucket(rawDy);
			out.dz[i] = ModelSpec.offsetBucket(rawDz);

			Integer age = ageByPosition.get(BlockPos.asLong(scratchX[i], scratchY[i], scratchZ[i]));
			int rawAge = age != null ? age : -1;
			out.recency[i] = ModelSpec.recencyBucket(rawAge);

			out.rawDx[i] = rawDx;
			out.rawDy[i] = rawDy;
			out.rawDz[i] = rawDz;
			out.rawAge[i] = rawAge;
			out.names[i] = scratchName[i];
		}
		out.count = keep;
	}

	private void collect(int x, int y, int z, String name, int orientation, BlockPos anchor,
			FocusBox focus) {
		if (scratchCount == scratchX.length) {
			grow();
		}
		int dx = x - anchor.getX();
		int dy = y - anchor.getY();
		int dz = z - anchor.getZ();

		scratchX[scratchCount] = x;
		scratchY[scratchCount] = y;
		scratchZ[scratchCount] = z;
		scratchName[scratchCount] = name;
		scratchOrientation[scratchCount] = orientation;

		Integer age = ageByPosition.get(BlockPos.asLong(x, y, z));
		int recencyBonus = age != null ? Math.max(0, 64 - age) : 0;
		int score = dx * dx + dy * dy + dz * dz - recencyBonus;
		if (focus != null) {

			score += focus.contains(x, y, z) ? -10_000 : 1_000_000;
		}
		scratchScore[scratchCount] = score;

		scratchCount++;
	}

	private void grow() {
		int size = scratchX.length * 2;
		scratchX = java.util.Arrays.copyOf(scratchX, size);
		scratchY = java.util.Arrays.copyOf(scratchY, size);
		scratchZ = java.util.Arrays.copyOf(scratchZ, size);
		scratchName = java.util.Arrays.copyOf(scratchName, size);
		scratchOrientation = java.util.Arrays.copyOf(scratchOrientation, size);
		scratchScore = java.util.Arrays.copyOf(scratchScore, size);
	}

	private void partialSortByScore(int keep) {
		for (int i = 0; i < keep; i++) {
			int best = i;
			for (int j = i + 1; j < scratchCount; j++) {
				if (scratchScore[j] < scratchScore[best]) {
					best = j;
				}
			}
			if (best != i) {
				swap(i, best);
			}
		}
	}

	private void swap(int a, int b) {
		int ix = scratchX[a];
		scratchX[a] = scratchX[b];
		scratchX[b] = ix;
		int iy = scratchY[a];
		scratchY[a] = scratchY[b];
		scratchY[b] = iy;
		int iz = scratchZ[a];
		scratchZ[a] = scratchZ[b];
		scratchZ[b] = iz;
		String name = scratchName[a];
		scratchName[a] = scratchName[b];
		scratchName[b] = name;
		int io = scratchOrientation[a];
		scratchOrientation[a] = scratchOrientation[b];
		scratchOrientation[b] = io;
		int is = scratchScore[a];
		scratchScore[a] = scratchScore[b];
		scratchScore[b] = is;
	}

	private void indexRecency(PlacementJournal journal) {
		ageByPosition.clear();
		int size = journal.size();
		if (size == 0) {
			return;
		}
		int session = journal.session(size - 1);
		int from = Math.max(0, size - RECENCY_LOOKBACK);
		for (int i = size - 1; i >= from; i--) {
			if (journal.session(i) != session) {
				break;
			}
			ageByPosition.putIfAbsent(
					BlockPos.asLong(journal.x(i), journal.y(i), journal.z(i)),
					size - 1 - i);
		}
	}
}
