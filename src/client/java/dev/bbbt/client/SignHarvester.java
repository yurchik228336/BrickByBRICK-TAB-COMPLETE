package dev.bbbt.client;

import dev.bbbt.build.BuildSegment;
import dev.bbbt.core.BbbtRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public final class SignHarvester {
	private SignHarvester() {
	}

	private static final int PADDING = 2;

	private static final int MAX_VOLUME = 48 * 48 * 48;
	private static final long INTERVAL_MS = 300_000L;

	private static long lastHarvestAt;

	public static void maybeHarvest(Minecraft client, BbbtRuntime runtime) {
		long now = System.currentTimeMillis();
		if (now - lastHarvestAt < INTERVAL_MS) {
			return;
		}
		lastHarvestAt = now;
		harvest(client, runtime);
	}

	public static void harvest(Minecraft client, BbbtRuntime runtime) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (!runtime.config().mayStoreCaptions() || !runtime.config().captionHarvestSigns) {
			return;
		}

		var journal = runtime.tracker().journal().snapshot();
		var described = runtime.captions().describeCurrent(journal, runtime.captionLocale());
		if (described == null) {
			return;
		}

		String text = read(client.level, described.segment());
		if (text.isBlank()) {
			return;
		}

		String lang = language(client);
		runtime.captions().annotateFromSign(journal, described.segment(), text, lang);
	}

	static String read(ClientLevel level, BuildSegment segment) {
		int minX = segment.minX() - PADDING;
		int minY = segment.minY() - PADDING;
		int minZ = segment.minZ() - PADDING;
		int maxX = segment.maxX() + PADDING;
		int maxY = segment.maxY() + PADDING;
		int maxZ = segment.maxZ() + PADDING;
		long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		if (volume > MAX_VOLUME) {
			return "";
		}

		StringBuilder out = new StringBuilder();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					cursor.set(x, y, z);
					BlockEntity entity = level.getBlockEntity(cursor);
					if (entity instanceof SignBlockEntity sign) {
						append(out, sign.getFrontText());
						append(out, sign.getBackText());
					}
				}
			}
		}
		return out.toString().trim();
	}

	private static void append(StringBuilder out, SignText text) {
		for (Component line : text.getMessages(false)) {
			String plain = line.getString().trim();
			if (plain.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(plain);
		}
	}

	private static String language(Minecraft client) {
		String selected = client.getLanguageManager().getSelected();
		int underscore = selected.indexOf('_');
		return underscore > 0 ? selected.substring(0, underscore) : selected;
	}
}
