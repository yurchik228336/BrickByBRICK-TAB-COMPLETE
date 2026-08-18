package dev.bbbt.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class LookTarget {
	private LookTarget() {
	}

	public static BlockPos placementCell(Minecraft client) {
		if (client.player == null || client.level == null) {
			return null;
		}

		double reach = client.player.blockInteractionRange();
		HitResult hit = client.player.pick(reach, 0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}

		BlockPos cell = blockHit.getBlockPos().relative(blockHit.getDirection());
		BlockState state = client.level.getBlockState(cell);
		if (!(state.isAir() || state.canBeReplaced())) {
			return null;
		}
		return cell;
	}
}
