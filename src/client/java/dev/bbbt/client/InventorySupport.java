package dev.bbbt.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class InventorySupport {
	private InventorySupport() {
	}

	public record Match(int inventorySlot, boolean offhand) {
	}

	public static boolean has(LocalPlayer player, Block block) {
		return find(player, block) != null;
	}

	public static Match find(LocalPlayer player, Block block) {
		if (player == null || block == null) {
			return null;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (isBlock(inventory.getItem(slot), block)) {
				return new Match(slot, false);
			}
		}
		ItemStack offhand = inventory.getItem(40);
		if (isBlock(offhand, block)) {
			return new Match(40, true);
		}
		for (int slot = 9; slot < 36; slot++) {
			if (isBlock(inventory.getItem(slot), block)) {
				return new Match(slot, false);
			}
		}
		return null;
	}

	public static boolean moveToMainHand(Minecraft client, Block block) {
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			return false;
		}
		Match match = find(player, block);
		if (match == null) {
			return false;
		}

		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		if (!match.offhand() && match.inventorySlot() == selected) {
			return true;
		}
		int source = match.offhand() ? 40 : match.inventorySlot();
		if (!swapSlots(client, source, selected)) {
			return false;
		}
		return isBlock(inventory.getItem(selected), block);
	}

	private static boolean swapSlots(Minecraft client, int inventorySlot, int hotbarSlot) {
		if (client.gui.screen() != null || client.player == null || client.gameMode == null) {
			return false;
		}
		int menuSlot = toMenuSlot(inventorySlot);
		if (menuSlot < 0 || hotbarSlot < 0 || hotbarSlot > 8) {
			return false;
		}
		var menu = client.player.inventoryMenu;
		client.gameMode.handleContainerInput(menu.containerId, menuSlot, hotbarSlot,
				ContainerInput.SWAP, client.player);
		return true;
	}

	private static int toMenuSlot(int inventorySlot) {
		if (inventorySlot >= 0 && inventorySlot < 9) {
			return 36 + inventorySlot;
		}
		if (inventorySlot >= 9 && inventorySlot < 36) {
			return inventorySlot;
		}
		if (inventorySlot == 40) {
			return 45;
		}
		return -1;
	}

	private static boolean isBlock(ItemStack stack, Block block) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == block;
	}
}
