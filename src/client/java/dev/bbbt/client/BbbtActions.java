package dev.bbbt.client;

import dev.bbbt.client.screen.AnnotateScreen;
import dev.bbbt.client.screen.ConsentScreen;
import dev.bbbt.client.screen.SettingsScreen;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.core.BbbtRuntime;
import dev.bbbt.palette.BlockStateCodec;
import dev.bbbt.suggest.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class BbbtActions {

	private final BbbtRuntime runtime;
	private final SuggestionController controller;

	private boolean consentPending;

	public BbbtActions(BbbtRuntime runtime, SuggestionController controller) {
		this.runtime = runtime;
		this.controller = controller;
	}

	public void onJoinedWorld(Minecraft client) {
		consentPending = runtime.config().hasPendingConsentQuestion();
	}

	public void handleInput(Minecraft client) {
		if (consentPending && client.player != null) {
			consentPending = false;
			client.setScreenAndShow(new ConsentScreen(null));
			return;
		}

		while (BbbtKeys.toggle.consumeClick()) {
			toggle(client);
		}
		while (BbbtKeys.cycle.consumeClick()) {
			controller.cycle();
		}
		while (BbbtKeys.accept.consumeClick()) {
			accept(client);
		}
		while (BbbtKeys.settings.consumeClick()) {
			client.setScreenAndShow(new SettingsScreen(null));
		}
		while (BbbtKeys.annotate.consumeClick()) {
			client.setScreenAndShow(new AnnotateScreen(null));
		}
		while (BbbtKeys.focusA.consumeClick()) {
			setFocusCorner(client, true);
		}
		while (BbbtKeys.focusB.consumeClick()) {
			setFocusCorner(client, false);
		}
		while (BbbtKeys.focusClear.consumeClick()) {
			controller.clearFocus();
			notifyPlayer(client, Component.translatable("bbbt.message.focus.clear"));
		}
	}

	public boolean tryFastPlace(Minecraft client) {
		BbbtConfig config = runtime.config();
		if (!config.enabled || !config.fastPlaceFromInventory || client.gui.screen() != null) {
			return false;
		}
		if (client.player == null || client.level == null || client.gameMode == null) {
			return false;
		}

		Suggestion suggestion = ghostUnderCrosshair(client);
		if (suggestion == null) {
			return false;
		}

		Block block = BlockStateCodec.blockByName(suggestion.blockName()).orElse(null);
		if (block == null) {
			return false;
		}
		if (!InventorySupport.has(client.player, block)) {
			return false;
		}
		if (!InventorySupport.moveToMainHand(client, block)) {
			return false;
		}
		return place(client, suggestion);
	}

	private Suggestion ghostUnderCrosshair(Minecraft client) {
		Suggestion selected = controller.selected();
		if (selected == null) {
			return null;
		}
		BlockPos cell = LookTarget.placementCell(client);
		if (cell == null) {
			return null;
		}
		if (selected.x() != cell.getX() || selected.y() != cell.getY() || selected.z() != cell.getZ()) {
			return null;
		}
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			Vec3 eye = client.player.getEyePosition(1f);
			double blockDist = blockHit.getLocation().distanceToSqr(eye);
			Vec3 ghostHit = Vec3.atCenterOf(cell);
			if (ghostHit.distanceToSqr(eye) > blockDist + 0.75) {
				return null;
			}
		}
		return selected;
	}

	private void setFocusCorner(Minecraft client, boolean first) {
		if (!runtime.config().focusRegionEnabled) {
			notifyPlayer(client, Component.translatable("bbbt.message.focus.disabled"));
			return;
		}
		BlockPos pos = aimedBlock(client);
		if (pos == null) {
			notifyPlayer(client, Component.translatable("bbbt.message.no_suggestion"));
			return;
		}
		if (first) {
			controller.setFocusA(pos);
			notifyPlayer(client, Component.translatable("bbbt.message.focus.a",
					pos.getX(), pos.getY(), pos.getZ()));
		} else {
			controller.setFocusB(pos);
			notifyPlayer(client, Component.translatable("bbbt.message.focus.b",
					pos.getX(), pos.getY(), pos.getZ()));
		}
	}

	private static BlockPos aimedBlock(Minecraft client) {
		if (client.player == null) {
			return null;
		}
		HitResult hit = client.player.pick(client.player.blockInteractionRange(), 0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos();
		}
		return null;
	}

	private void toggle(Minecraft client) {
		BbbtConfig config = runtime.config();
		config.enabled = !config.enabled;
		runtime.saveConfig();
		controller.invalidate();
		notifyPlayer(client, Component.translatable(
				config.enabled ? "bbbt.message.toggled.on" : "bbbt.message.toggled.off"));
	}

	private void accept(Minecraft client) {
		Suggestion suggestion = controller.selected();
		if (suggestion == null || client.player == null || client.level == null) {
			notifyPlayer(client, Component.translatable("bbbt.message.no_suggestion"));
			return;
		}

		Block block = BlockStateCodec.blockByName(suggestion.blockName()).orElse(null);
		if (block == null) {
			return;
		}
		if (!InventorySupport.moveToMainHand(client, block)
				&& !selectInHotbar(client, block)) {
			notifyPlayer(client, Component.translatable("bbbt.message.fast_place_no_block"));
			return;
		}
		if (!runtime.config().autoPlaceEnabled) {
			notifyPlayer(client, Component.translatable("bbbt.message.auto_place_off"));
			return;
		}
		place(client, suggestion);
	}

	private static boolean selectInHotbar(Minecraft client, Block block) {
		var inventory = client.player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block) {
				inventory.setSelectedSlot(slot);
				return true;
			}
		}
		return false;
	}

	private boolean place(Minecraft client, Suggestion suggestion) {
		BlockPos target = new BlockPos(suggestion.x(), suggestion.y(), suggestion.z());

		for (Direction direction : Direction.values()) {
			BlockPos support = target.relative(direction);
			if (client.level.getBlockState(support).isAir()) {
				continue;
			}
			Direction face = direction.getOpposite();
			Vec3 hitPoint = Vec3.atCenterOf(support).relative(face, 0.5);
			BlockHitResult hit = new BlockHitResult(hitPoint, face, support, false);

			if (client.gameMode != null) {
				client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
				return true;
			}
		}
		notifyPlayer(client, Component.translatable("bbbt.message.no_suggestion"));
		return false;
	}

	private static void notifyPlayer(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.sendOverlayMessage(message);
		}
	}
}
