package dev.bbbt.client.render;

import java.util.List;
import java.util.Locale;

import dev.bbbt.client.InventorySupport;
import dev.bbbt.client.SuggestionController;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.core.BbbtRuntime;
import dev.bbbt.palette.BlockStateCodec;
import dev.bbbt.suggest.Suggestion;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public final class SuggestionHud implements HudElement {

	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 3;
	private static final int COLOUR_TITLE = 0xFFFFFFFF;
	private static final int COLOUR_SELECTED = 0xFF88FF99;
	private static final int COLOUR_OTHER = 0xFFBBBBBB;
	private static final int COLOUR_MUTED = 0xFF888888;
	private static final int COLOUR_BACKDROP = 0x88000000;

	private final BbbtRuntime runtime;
	private final SuggestionController controller;

	public SuggestionHud(BbbtRuntime runtime, SuggestionController controller) {
		this.runtime = runtime;
		this.controller = controller;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		BbbtConfig config = runtime.config();
		if (!config.showHudList || client.player == null) {
			return;
		}

		List<Component> lines = buildLines(config);
		if (lines.isEmpty()) {
			return;
		}

		Font font = client.font;
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, font.width(line));
		}
		int height = lines.size() * LINE_HEIGHT;

		int x = switch (config.hudAnchor) {
			case TOP_LEFT, BOTTOM_LEFT -> config.hudOffsetX;
			case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - width - config.hudOffsetX
					- PADDING * 2;
		};
		int y = switch (config.hudAnchor) {
			case TOP_LEFT, TOP_RIGHT -> config.hudOffsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - height - config.hudOffsetY
					- PADDING * 2;
		};

		graphics.fill(x, y, x + width + PADDING * 2, y + height + PADDING * 2, COLOUR_BACKDROP);

		int textY = y + PADDING;
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + PADDING, textY, colourFor(i, lines.size()), true);
			textY += LINE_HEIGHT;
		}
	}

	private int colourFor(int lineIndex, int total) {
		if (lineIndex == 0) {
			return COLOUR_TITLE;
		}
		int suggestionIndex = lineIndex - 1;
		if (suggestionIndex >= controller.suggestions().size()) {
			return COLOUR_MUTED;
		}
		return suggestionIndex == controller.selectedIndex() ? COLOUR_SELECTED : COLOUR_OTHER;
	}

	private List<Component> buildLines(BbbtConfig config) {
		if (!config.enabled) {
			return List.of(Component.translatable("bbbt.hud.disabled"));
		}

		List<Suggestion> suggestions = controller.suggestions();
		if (suggestions.isEmpty()) {

			return controller.isPending()
					? List.of(Component.translatable("bbbt.hud.thinking"))
					: List.of();
		}

		List<Component> lines = new java.util.ArrayList<>(suggestions.size() + 2);
		lines.add(Component.translatable("bbbt.hud.title"));
		for (int i = 0; i < suggestions.size(); i++) {
			Suggestion suggestion = suggestions.get(i);
			String label = blockLabel(suggestion);
			if (config.showConfidence) {
				label += " " + Component.translatable("bbbt.hud.confidence",
						String.format(Locale.ROOT, "%.0f", suggestion.confidence() * 100f))
						.getString();
			}
			lines.add(Component.translatable(
					i == controller.selectedIndex() ? "bbbt.hud.selected" : "bbbt.hud.candidate",
					label));
		}

		Minecraft client = Minecraft.getInstance();
		if (config.debugOverlay) {
			String origin = runtime.hasModel() ? runtime.modelOrigin() : "none";
			lines.add(Component.translatable("bbbt.hud.debug",
					Component.translatable("bbbt.option.modelStatus." + origin),
					String.format(Locale.ROOT, "%.1f",
							controller.lastComputeNanos() / 1_000_000.0)));
			if (controller.focusBox() != null) {
				lines.add(Component.translatable("bbbt.hud.focus"));
			}
		}
		Suggestion selected = controller.selected();
		if (selected != null && client.player != null) {
			var block = BlockStateCodec.blockByName(selected.blockName());
			if (block.isPresent() && !InventorySupport.has(client.player, block.get())) {
				lines.add(Component.translatable("bbbt.hud.not_in_inventory"));
			}
		}
		return lines;
	}

	private static String blockLabel(Suggestion suggestion) {
		return dev.bbbt.palette.BlockStateCodec.blockByName(suggestion.blockName())
				.map(block -> {
					BlockState state = block.defaultBlockState();
					return state.getBlock().getName().getString();
				})
				.orElse(suggestion.blockName());
	}
}
