package dev.bbbt.client;

import dev.bbbt.core.BbbtRuntime;
import dev.bbbt.palette.BlockStateCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BbbtClientState {
	private BbbtClientState() {
	}

	private static boolean active;

	public static void setActive(boolean value) {
		active = value;
	}

	public static void onBlockChanged(Level level, BlockPos pos, BlockState oldState,
			BlockState newState) {
		if (!active) {
			return;
		}
		BbbtRuntime runtime = BbbtRuntime.get();
		if (!runtime.config().enabled) {
			return;
		}
		if (level != Minecraft.getInstance().level) {
			return;
		}

		if (newState.isAir()) {
			runtime.tracker().onBlockRemoved(pos);
		} else {
			runtime.tracker().onBlockAdded(pos, newState);
		}
	}

	public static void onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (!active) {
			return;
		}
		BbbtRuntime runtime = BbbtRuntime.get();
		if (!runtime.config().enabled) {
			return;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return;
		}
		runtime.tracker().expectPlacement(hit.getBlockPos(),
				BlockStateCodec.registryName(blockItem.getBlock()));
	}
}
