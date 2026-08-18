package dev.bbbt.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.bbbt.client.BrickByBrickTabClient;
import net.minecraft.client.Minecraft;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	@Shadow
	private int rightClickDelay;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void bbbt$fastPlaceGhost(CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (BrickByBrickTabClient.actions() != null && BrickByBrickTabClient.actions().tryFastPlace(self)) {
			this.rightClickDelay = 4;
			ci.cancel();
		}
	}
}
