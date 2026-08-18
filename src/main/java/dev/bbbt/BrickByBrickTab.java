package dev.bbbt;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BrickByBrickTab implements ModInitializer {
	public static final String MOD_ID = "brickbybricktab";
	public static final Logger LOG = LoggerFactory.getLogger("BrickByBrickTab");

	@Override
	public void onInitialize() {
		LOG.info("Brick by Brick Tab: core initialised");
	}
}
