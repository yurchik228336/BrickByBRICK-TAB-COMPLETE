package dev.bbbt.client.screen;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import com.mojang.serialization.Codec;

import dev.bbbt.client.BrickByBrickTabClient;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.core.BbbtRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends OptionsSubScreen {

	private static final Set<String> MODEL_ORIGIN_KEYS = Set.of("bundled", "config", "override");

	private final BbbtRuntime runtime;
	private final BbbtConfig config;

	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options,
				Component.translatable("bbbt.screen.settings.title"));
		this.runtime = BbbtRuntime.get();
		this.config = runtime.config();
	}

	@Override
	protected void addOptions() {
		list.addHeader(Component.translatable("bbbt.screen.group.general"));
		list.addBig(toggle("enabled", () -> config.enabled, value -> {
			config.enabled = value;
			BrickByBrickTabClient.controller().invalidate();
		}));
		list.addBig(intOption("suggestionCount", 1, 8, () -> config.suggestionCount,
				value -> config.suggestionCount = value));
		list.addBig(percent("confidenceThreshold", 0, 90, () -> config.confidenceThreshold,
				value -> config.confidenceThreshold = value));

		list.addHeader(Component.translatable("bbbt.screen.group.display"));
		list.addBig(toggle("showGhostBlocks", () -> config.showGhostBlocks,
				value -> config.showGhostBlocks = value));
		list.addBig(toggle("ghostLookOnly", () -> config.ghostLookOnly, value -> {
			config.ghostLookOnly = value;
			BrickByBrickTabClient.controller().invalidate();
		}));
		list.addBig(toggle("fastPlaceFromInventory", () -> config.fastPlaceFromInventory,
				value -> config.fastPlaceFromInventory = value));
		list.addBig(toggle("showHudList", () -> config.showHudList,
				value -> config.showHudList = value));
		list.addBig(toggle("showOutline", () -> config.showOutline,
				value -> config.showOutline = value));
		list.addBig(toggle("showConfidence", () -> config.showConfidence,
				value -> config.showConfidence = value));
		list.addBig(percent("ghostOpacity", 5, 100, () -> config.ghostOpacity,
				value -> config.ghostOpacity = value));
		list.addBig(enumOption("hudAnchor", BbbtConfig.HudAnchor.class, () -> config.hudAnchor,
				value -> config.hudAnchor = value));

		list.addHeader(Component.translatable("bbbt.screen.group.tracking"));
		list.addBig(toggle("trackOtherPlayers", () -> config.trackOtherPlayers,
				value -> config.trackOtherPlayers = value));
		list.addBig(toggle("focusRegion", () -> config.focusRegionEnabled, value -> {
			config.focusRegionEnabled = value;
			BrickByBrickTabClient.controller().invalidate();
		}));

		list.addHeader(Component.translatable("bbbt.screen.group.personalisation"));
		list.addBig(toggle("loraEnabled", () -> config.loraEnabled,
				value -> config.loraEnabled = value));
		list.addBig(toggle("loraAutoTrain", () -> config.loraAutoTrain,
				value -> config.loraAutoTrain = value));
		list.addBig(actionButton("bbbt.option.resetPersonalisation", () -> {
			runtime.resetPersonalisation();
			feedback("bbbt.option.resetPersonalisation.done");
		}));

		list.addHeader(Component.translatable("bbbt.screen.group.privacy"));
		list.addBig(actionButton("bbbt.consent.reopen",
				() -> minecraft.setScreenAndShow(new ConsentScreen(this))));
		list.addBig(toggle("captionAutoGenerate", () -> config.captionAutoGenerate,
				value -> config.captionAutoGenerate = value));
		list.addBig(toggle("captionHarvestSigns", () -> config.captionHarvestSigns,
				value -> config.captionHarvestSigns = value));
		list.addBig(actionButton("bbbt.option.deleteData", () -> {
			runtime.deleteCollectedData();
			feedback("bbbt.option.deleteData.done");
		}));
		list.addHeader(Component.translatable("bbbt.option.installId", config.installId));

		list.addHeader(Component.translatable("bbbt.screen.group.advanced"));
		list.addBig(toggle("autoPlaceEnabled", () -> config.autoPlaceEnabled,
				value -> config.autoPlaceEnabled = value));
		list.addBig(actionButton("bbbt.option.reloadModel", () -> {
			runtime.reloadModel();
			BrickByBrickTabClient.controller().invalidate();
			feedback("bbbt.option.reloadModel.done");
		}));
		list.addHeader(Component.translatable("bbbt.option.modelStatus", modelStatusLabel()));
	}

	private Component modelStatusLabel() {
		String origin = runtime.hasModel() ? runtime.modelOrigin() : "none";
		if (!MODEL_ORIGIN_KEYS.contains(origin)) {
			origin = runtime.hasModel() ? "config" : "none";
		}
		return Component.translatable("bbbt.option.modelStatus." + origin);
	}

	@Override
	public void removed() {
		config.sanitise();
		runtime.saveConfig();
		super.removed();
	}

	private OptionInstance<Boolean> toggle(String name, java.util.function.BooleanSupplier getter,
			Consumer<Boolean> setter) {
		return OptionInstance.createBoolean("bbbt.option." + name, tooltip(name), getter.getAsBoolean(),
				setter::accept);
	}

	private OptionInstance<Integer> intOption(String name, int min, int max, IntSupplier getter,
			IntConsumer setter) {
		return new OptionInstance<>("bbbt.option." + name, tooltip(name),
				(caption, value) -> Component.literal(caption.getString() + ": " + value),
				new OptionInstance.IntRange(min, max), getter.getAsInt(), setter::accept);
	}

	private OptionInstance<Integer> percent(String name, int min, int max,
			java.util.function.DoubleSupplier getter, Consumer<Float> setter) {
		int initial = Math.clamp(Math.round((float) getter.getAsDouble() * 100f), min, max);
		return new OptionInstance<>("bbbt.option." + name, tooltip(name),
				(caption, value) -> Component.literal(caption.getString() + ": " + value + "%"),
				new OptionInstance.IntRange(min, max), initial,
				value -> setter.accept(value / 100f));
	}

	private <E extends Enum<E>> OptionInstance<E> enumOption(String name, Class<E> type,
			java.util.function.Supplier<E> getter, Consumer<E> setter) {
		List<E> values = List.of(type.getEnumConstants());
		Codec<E> codec = Codec.STRING.xmap(id -> Enum.valueOf(type, id.toUpperCase(java.util.Locale.ROOT)),
				value -> value.name().toLowerCase(java.util.Locale.ROOT));
		return new OptionInstance<>("bbbt.option." + name, tooltip(name),
				(caption, value) -> Component.translatable("bbbt.option." + name + "."
						+ value.name().toLowerCase(java.util.Locale.ROOT)),
				new OptionInstance.Enum<>(values, codec), getter.get(), setter::accept);
	}

	private <T> OptionInstance.TooltipSupplier<T> tooltip(String name) {
		Tooltip tooltip = Tooltip.create(Component.translatable("bbbt.option." + name + ".tooltip"));
		return value -> tooltip;
	}

	private Button actionButton(String key, Runnable action) {
		return Button.builder(Component.translatable(key), button -> action.run())
				.width(310)
				.build();
	}

	private void feedback(String key, Object... args) {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.sendSystemMessage(Component.translatable(key, args));
		}
	}
}
