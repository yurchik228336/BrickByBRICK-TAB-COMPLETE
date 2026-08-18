package dev.bbbt.client;

import org.lwjgl.glfw.GLFW;

import dev.bbbt.BrickByBrickTab;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class BbbtKeys {
	private BbbtKeys() {
	}

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(BrickByBrickTab.MOD_ID, "main"));

	public static KeyMapping toggle;
	public static KeyMapping cycle;
	public static KeyMapping accept;
	public static KeyMapping settings;
	public static KeyMapping annotate;
	public static KeyMapping focusA;
	public static KeyMapping focusB;
	public static KeyMapping focusClear;

	public static void register() {
		toggle = register("toggle", GLFW.GLFW_KEY_B);
		cycle = register("cycle", GLFW.GLFW_KEY_V);
		accept = register("accept", GLFW.GLFW_KEY_UNKNOWN);
		settings = register("settings", GLFW.GLFW_KEY_UNKNOWN);
		annotate = register("annotate", GLFW.GLFW_KEY_UNKNOWN);
		focusA = register("focus_a", GLFW.GLFW_KEY_UNKNOWN);
		focusB = register("focus_b", GLFW.GLFW_KEY_UNKNOWN);
		focusClear = register("focus_clear", GLFW.GLFW_KEY_UNKNOWN);
	}

	private static KeyMapping register(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + BrickByBrickTab.MOD_ID + "." + name, defaultKey, CATEGORY));
	}
}
