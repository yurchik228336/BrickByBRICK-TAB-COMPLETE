package dev.bbbt.client;

import dev.bbbt.model.ModelSpec;
import dev.bbbt.suggest.PlacementValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class ClientWorldSnapshot implements PlacementValidator {

	private static final int PAD = 1;
	private static final int MIN = ModelSpec.OFFSET_MIN - PAD;
	private static final int MAX = ModelSpec.OFFSET_MAX + PAD;
	private static final int SIDE = MAX - MIN + 1;
	private static final int VOLUME = SIDE * SIDE * SIDE;

	private final int originX;
	private final int originY;
	private final int originZ;

	private final boolean[] free = new boolean[VOLUME];

	private final boolean[] solid = new boolean[VOLUME];

	private ClientWorldSnapshot(int anchorX, int anchorY, int anchorZ) {
		this.originX = anchorX + MIN;
		this.originY = anchorY + MIN;
		this.originZ = anchorZ + MIN;
	}

	public static ClientWorldSnapshot capture(BlockGetter level, BlockPos anchor) {
		ClientWorldSnapshot snapshot = new ClientWorldSnapshot(
				anchor.getX(), anchor.getY(), anchor.getZ());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int x = 0; x < SIDE; x++) {
			for (int y = 0; y < SIDE; y++) {
				for (int z = 0; z < SIDE; z++, i++) {
					cursor.set(snapshot.originX + x, snapshot.originY + y, snapshot.originZ + z);
					BlockState state = level.getBlockState(cursor);
					snapshot.free[i] = state.isAir() || state.canBeReplaced();
					snapshot.solid[i] = !state.isAir()
							&& state.isCollisionShapeFullBlock(level, cursor);
				}
			}
		}
		return snapshot;
	}

	@Override
	public boolean isFree(int x, int y, int z) {
		int index = indexOf(x, y, z);
		return index >= 0 && free[index];
	}

	@Override
	public boolean hasSupport(int x, int y, int z) {
		return isSolid(x - 1, y, z) || isSolid(x + 1, y, z)
				|| isSolid(x, y - 1, z) || isSolid(x, y + 1, z)
				|| isSolid(x, y, z - 1) || isSolid(x, y, z + 1);
	}

	private boolean isSolid(int x, int y, int z) {
		int index = indexOf(x, y, z);
		return index >= 0 && solid[index];
	}

	private int indexOf(int x, int y, int z) {
		int lx = x - originX;
		int ly = y - originY;
		int lz = z - originZ;
		if (lx < 0 || ly < 0 || lz < 0 || lx >= SIDE || ly >= SIDE || lz >= SIDE) {
			return -1;
		}
		return (lx * SIDE + ly) * SIDE + lz;
	}
}
