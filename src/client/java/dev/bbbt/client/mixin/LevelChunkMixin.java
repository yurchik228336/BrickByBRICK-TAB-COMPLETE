package dev.bbbt.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.bbbt.client.BbbtClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

	@Inject(method = "setBlockState", at = @At("RETURN"))
	private void bbbt$onSetBlockState(BlockPos pos, BlockState newState, int flags,
			CallbackInfoReturnable<BlockState> cir) {
		LevelChunk self = (LevelChunk) (Object) this;

		if (!self.getLevel().isClientSide()) {
			return;
		}

		BlockState oldState = cir.getReturnValue();
		if (oldState == null || oldState == newState) {
			return;
		}
		if (oldState.getBlock() == newState.getBlock()) {
			return;
		}

		BbbtClientState.onBlockChanged(self.getLevel(), pos.immutable(), oldState, newState);
	}
}
