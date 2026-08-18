package dev.bbbt.suggest;

import dev.bbbt.data.JournalSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HeuristicSuggester {
	private static final float CONFIDENCE_COLLINEAR = 0.72f;
	private static final float CONFIDENCE_EXTEND = 0.45f;
	private static final float CONFIDENCE_NEXT_COURSE = 0.30f;
	private static final float CONFIDENCE_NEIGHBOUR = 0.18f;

	private final Set<Long> occupied = new HashSet<>();
	private final Map<String, Integer> nameCounts = new HashMap<>();

	public List<Suggestion> suggest(BuildContext ctx, JournalSnapshot journal, int topK,
			PlacementValidator validator) {
		List<Suggestion> out = new ArrayList<>();
		if (ctx.isEmpty()) {
			return out;
		}

		indexContext(ctx);
		String dominantBlock = dominantBlock(ctx);
		int dominantOrientation = orientationOf(ctx, dominantBlock);

		int size = journal.size();
		int[] xs = journal.x();
		int[] ys = journal.y();
		int[] zs = journal.z();
		int[] sessions = journal.sessions();
		int session = size > 0 ? sessions[size - 1] : -1;

		if (size >= 3 && sessions[size - 2] == session && sessions[size - 3] == session) {
			int ax = xs[size - 1];
			int ay = ys[size - 1];
			int az = zs[size - 1];
			int s1x = ax - xs[size - 2];
			int s1y = ay - ys[size - 2];
			int s1z = az - zs[size - 2];
			int s2x = xs[size - 2] - xs[size - 3];
			int s2y = ys[size - 2] - ys[size - 3];
			int s2z = zs[size - 2] - zs[size - 3];

			if (isUsableStep(s1x, s1y, s1z) && s1x == s2x && s1y == s2y && s1z == s2z) {
				add(out, ctx, validator, ax + s1x, ay + s1y, az + s1z,
						journal.names()[size - 1], journal.orientations()[size - 1],
						CONFIDENCE_COLLINEAR);
			}
		}

		if (size >= 2 && sessions[size - 2] == session) {
			int ax = xs[size - 1];
			int ay = ys[size - 1];
			int az = zs[size - 1];
			int sx = ax - xs[size - 2];
			int sy = ay - ys[size - 2];
			int sz = az - zs[size - 2];
			if (isUsableStep(sx, sy, sz)) {
				add(out, ctx, validator, ax + sx, ay + sy, az + sz,
						journal.names()[size - 1], journal.orientations()[size - 1],
						CONFIDENCE_EXTEND);
			}
		}

		if (horizontalNeighbours(0, 0, 0) >= 2) {
			add(out, ctx, validator, ctx.anchorX, ctx.anchorY + 1, ctx.anchorZ,
					dominantBlock, dominantOrientation, CONFIDENCE_NEXT_COURSE);
		}

		int[][] faces = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
				{ 0, 1, 0 }, { 0, -1, 0 } };
		int bestScore = -1;
		int[] bestFace = null;
		for (int[] face : faces) {
			if (isOccupied(face[0], face[1], face[2])) {
				continue;
			}
			int score = neighbourCount(face[0], face[1], face[2]);
			if (score > bestScore) {
				bestScore = score;
				bestFace = face;
			}
		}
		if (bestFace != null && bestScore > 0) {
			add(out, ctx, validator,
					ctx.anchorX + bestFace[0], ctx.anchorY + bestFace[1], ctx.anchorZ + bestFace[2],
					dominantBlock, dominantOrientation, CONFIDENCE_NEIGHBOUR);
		}

		out.sort((a, b) -> Float.compare(b.confidence(), a.confidence()));
		return out.size() > topK ? new ArrayList<>(out.subList(0, topK)) : out;
	}

	public List<Suggestion> suggestAt(BuildContext ctx, int x, int y, int z, int topK,
			PlacementValidator validator) {
		if (ctx.isEmpty()) {
			return List.of();
		}
		indexContext(ctx);
		String block = dominantBlock(ctx);
		if (block == null) {
			return List.of();
		}
		List<Suggestion> out = new ArrayList<>(1);
		add(out, ctx, validator, x, y, z, block, orientationOf(ctx, block), CONFIDENCE_NEIGHBOUR);
		return out.size() > topK ? new ArrayList<>(out.subList(0, topK)) : out;
	}

	private static boolean isUsableStep(int dx, int dy, int dz) {
		int steps = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
		return steps == 1
				&& Math.abs(dx) <= 2 && Math.abs(dy) <= 2 && Math.abs(dz) <= 2;
	}

	private void add(List<Suggestion> out, BuildContext ctx, PlacementValidator validator,
			int x, int y, int z, String blockName, int orientation, float confidence) {
		if (blockName == null || blockName.isEmpty()) {
			return;
		}
		if (isOccupied(x - ctx.anchorX, y - ctx.anchorY, z - ctx.anchorZ)) {
			return;
		}
		if (!validator.isFree(x, y, z)) {
			return;
		}
		for (Suggestion existing : out) {
			if (existing.x() == x && existing.y() == y && existing.z() == z) {
				return;
			}
		}
		out.add(new Suggestion(x, y, z, blockName, orientation, confidence,
				Suggestion.Source.HEURISTIC));
	}

	private void indexContext(BuildContext ctx) {
		occupied.clear();
		nameCounts.clear();
		for (int i = 0; i < ctx.count; i++) {
			occupied.add(pack(ctx.rawDx[i], ctx.rawDy[i], ctx.rawDz[i]));
			nameCounts.merge(ctx.names[i], 1, Integer::sum);
		}
	}

	private String dominantBlock(BuildContext ctx) {
		String best = null;
		int bestCount = -1;
		for (Map.Entry<String, Integer> entry : nameCounts.entrySet()) {
			if (entry.getValue() > bestCount) {
				bestCount = entry.getValue();
				best = entry.getKey();
			}
		}
		return best;
	}

	private int orientationOf(BuildContext ctx, String blockName) {
		for (int i = 0; i < ctx.count; i++) {
			if (ctx.names[i].equals(blockName)) {
				return ctx.orientations[i];
			}
		}
		return 0;
	}

	private boolean isOccupied(int dx, int dy, int dz) {
		return occupied.contains(pack(dx, dy, dz));
	}

	private int neighbourCount(int dx, int dy, int dz) {
		int count = 0;
		for (int ox = -1; ox <= 1; ox++) {
			for (int oy = -1; oy <= 1; oy++) {
				for (int oz = -1; oz <= 1; oz++) {
					if ((ox | oy | oz) != 0 && isOccupied(dx + ox, dy + oy, dz + oz)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private int horizontalNeighbours(int dx, int dy, int dz) {
		int count = 0;
		if (isOccupied(dx + 1, dy, dz)) {
			count++;
		}
		if (isOccupied(dx - 1, dy, dz)) {
			count++;
		}
		if (isOccupied(dx, dy, dz + 1)) {
			count++;
		}
		if (isOccupied(dx, dy, dz - 1)) {
			count++;
		}
		return count;
	}

	private static long pack(int dx, int dy, int dz) {
		return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
	}
}
