package dev.bbbt.client;

import dev.bbbt.BrickByBrickTab;
import dev.bbbt.client.render.GhostRenderer;
import dev.bbbt.client.render.SuggestionHud;
import dev.bbbt.core.BbbtRuntime;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class BrickByBrickTabClient implements ClientModInitializer {

	private static SuggestionController controller;
	private static BbbtActions actions;

	public static SuggestionController controller() {
		return controller;
	}

	public static BbbtActions actions() {
		return actions;
	}

	@Override
	public void onInitializeClient() {
		BbbtRuntime runtime = BbbtRuntime.get();
		controller = new SuggestionController(runtime);
		actions = new BbbtActions(runtime, controller);

		BbbtKeys.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				return;
			}
			runtime.tick();
			controller.tick(client);
			actions.handleInput(client);
			if (runtime.config().captionHarvestSigns) {
				SignHarvester.maybeHarvest(client, runtime);
			}
		});

		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(BrickByBrickTabClient::onLevelChanged);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			BbbtClientState.setActive(false);
			runtime.shutdown();
		});

		GhostRenderer ghosts = new GhostRenderer(runtime, controller);
		LevelRenderEvents.COLLECT_SUBMITS.register(ghosts::render);

		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(BrickByBrickTab.MOD_ID, "suggestions"),
				new SuggestionHud(runtime, controller));

		BrickByBrickTab.LOG.info("Brick by Brick Tab: client initialised, model {}",
				runtime.modelOrigin());
	}

	private static void onLevelChanged(Minecraft client, net.minecraft.client.multiplayer.ClientLevel level) {
		BbbtRuntime runtime = BbbtRuntime.get();
		controller.clear();

		if (level == null) {
			BbbtClientState.setActive(false);
			runtime.onLeaveWorld();
			return;
		}

		runtime.onJoinWorld(WorldIdentity.of(client, level));
		BbbtClientState.setActive(true);
		actions.onJoinedWorld(client);
	}
}
