package dev.bbbt.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;

public final class WorldIdentity {
	private WorldIdentity() {
	}

	public static String of(Minecraft client, ClientLevel level) {
		String dimension = level.dimension().identifier().getPath();

		if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
			return "sp-" + client.getSingleplayerServer().getWorldData().getLevelName()
					+ "-" + dimension;
		}

		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return "mp-" + server.ip + "-" + dimension;
		}
		return "unknown-" + dimension;
	}
}
