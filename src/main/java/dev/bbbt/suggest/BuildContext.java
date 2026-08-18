package dev.bbbt.suggest;

import dev.bbbt.model.ModelSpec;

public final class BuildContext {
	public final int[] blockTokens = new int[ModelSpec.MAX_CONTEXT];
	public final int[] orientations = new int[ModelSpec.MAX_CONTEXT];

	public final int[] dx = new int[ModelSpec.MAX_CONTEXT];
	public final int[] dy = new int[ModelSpec.MAX_CONTEXT];
	public final int[] dz = new int[ModelSpec.MAX_CONTEXT];
	public final int[] recency = new int[ModelSpec.MAX_CONTEXT];

	public int count;

	public int anchorX;
	public int anchorY;
	public int anchorZ;

	public final int[] rawDx = new int[ModelSpec.MAX_CONTEXT];
	public final int[] rawDy = new int[ModelSpec.MAX_CONTEXT];
	public final int[] rawDz = new int[ModelSpec.MAX_CONTEXT];
	public final String[] names = new String[ModelSpec.MAX_CONTEXT];

	public final int[] rawAge = new int[ModelSpec.MAX_CONTEXT];

	public boolean isEmpty() {
		return count == 0;
	}

	public void reset(int anchorX, int anchorY, int anchorZ) {
		this.count = 0;
		this.anchorX = anchorX;
		this.anchorY = anchorY;
		this.anchorZ = anchorZ;
	}

	public BuildContext copy() {
		BuildContext copy = new BuildContext();
		copy.reset(anchorX, anchorY, anchorZ);
		copy.count = count;
		System.arraycopy(blockTokens, 0, copy.blockTokens, 0, count);
		System.arraycopy(orientations, 0, copy.orientations, 0, count);
		System.arraycopy(dx, 0, copy.dx, 0, count);
		System.arraycopy(dy, 0, copy.dy, 0, count);
		System.arraycopy(dz, 0, copy.dz, 0, count);
		System.arraycopy(recency, 0, copy.recency, 0, count);
		System.arraycopy(rawDx, 0, copy.rawDx, 0, count);
		System.arraycopy(rawDy, 0, copy.rawDy, 0, count);
		System.arraycopy(rawDz, 0, copy.rawDz, 0, count);
		System.arraycopy(rawAge, 0, copy.rawAge, 0, count);
		System.arraycopy(names, 0, copy.names, 0, count);
		return copy;
	}
}
