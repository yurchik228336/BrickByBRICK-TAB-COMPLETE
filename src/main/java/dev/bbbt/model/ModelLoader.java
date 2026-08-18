package dev.bbbt.model;

import dev.bbbt.BrickByBrickTab;
import dev.bbbt.nn.SuggestionNetwork;
import dev.bbbt.nn.WeightStore;
import dev.bbbt.palette.BlockPalette;
import dev.bbbt.net.ModelSync;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModelLoader {
	public static final String PALETTE_FILE = "palette.json";
	public static final String WEIGHTS_FILE = "model.bbbt";

	private static final String RESOURCE_ROOT = "/assets/" + BrickByBrickTab.MOD_ID + "/model/";

	public record Loaded(BlockPalette palette, SuggestionNetwork network, String origin, String version) {
		public boolean hasNetwork() {
			return network != null;
		}
	}

	private ModelLoader() {
	}

	public static Loaded load(Path modelDir, String overridePath) {
		Path weights = null;
		Path palette = null;
		String diskOrigin = null;
		String version = "";

		if (overridePath != null && !overridePath.isBlank()) {
			Path candidate = Path.of(overridePath);
			if (Files.isRegularFile(candidate)) {
				weights = candidate;
				diskOrigin = "override";
				Path sibling = candidate.resolveSibling(PALETTE_FILE);
				palette = Files.isRegularFile(sibling) ? sibling : null;
				Path parent = candidate.getParent();
				if (parent != null) {
					version = ModelSync.readVersionFile(parent);
				}
			} else {
				BrickByBrickTab.LOG.warn("Model override path does not exist: {}", overridePath);
			}
		}

		if (weights == null) {
			Path candidate = modelDir.resolve(WEIGHTS_FILE);
			if (Files.isRegularFile(candidate)) {
				weights = candidate;
				version = ModelSync.readVersionFile(modelDir);
				diskOrigin = version.isBlank() ? "config" : "server";
			}
		}
		if (palette == null) {
			Path candidate = modelDir.resolve(PALETTE_FILE);
			if (Files.isRegularFile(candidate)) {
				palette = candidate;
			}
		}

		BlockPalette loadedPalette = readPalette(palette);
		if (loadedPalette == null) {
			BrickByBrickTab.LOG.warn(
					"No block palette found; suggestions will use pattern rules only");
			return new Loaded(BlockPalette.empty(), null, "none", "");
		}

		SuggestionNetwork fromDisk = readNetworkFromFile(weights);
		if (fromDisk != null) {
			return new Loaded(loadedPalette, fromDisk, diskOrigin != null ? diskOrigin : "config",
					version);
		}
		SuggestionNetwork bundled = readBundledNetwork();
		return new Loaded(loadedPalette, bundled, bundled != null ? "bundled" : "none", "");
	}

	private static BlockPalette readPalette(Path path) {
		if (path != null) {
			try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
				BlockPalette palette = BlockPalette.load(in);
				BrickByBrickTab.LOG.info("Loaded palette from {} ({} blocks)",
						path, palette.blockCount());
				return palette;
			} catch (IOException e) {
				BrickByBrickTab.LOG.error("Could not read palette {}", path, e);
			}
		}
		try (InputStream in = ModelLoader.class.getResourceAsStream(RESOURCE_ROOT + PALETTE_FILE)) {
			if (in == null) {
				return null;
			}
			BlockPalette palette = BlockPalette.load(new BufferedInputStream(in));
			BrickByBrickTab.LOG.info("Loaded bundled palette ({} blocks)", palette.blockCount());
			return palette;
		} catch (IOException e) {
			BrickByBrickTab.LOG.error("Could not read bundled palette", e);
			return null;
		}
	}

	private static SuggestionNetwork readNetworkFromFile(Path path) {
		if (path == null) {
			return null;
		}
		try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
			SuggestionNetwork network = new SuggestionNetwork(WeightStore.load(in));
			BrickByBrickTab.LOG.info("Loaded model weights from {}", path);
			return network;
		} catch (IOException | RuntimeException e) {
			BrickByBrickTab.LOG.error("Could not load model weights {}", path, e);
			return null;
		}
	}

	private static SuggestionNetwork readBundledNetwork() {
		try (InputStream in = ModelLoader.class.getResourceAsStream(RESOURCE_ROOT + WEIGHTS_FILE)) {
			if (in == null) {
				BrickByBrickTab.LOG.info(
						"No model weights installed; using pattern rules until one is added");
				return null;
			}
			SuggestionNetwork network = new SuggestionNetwork(
					WeightStore.load(new BufferedInputStream(in)));
			BrickByBrickTab.LOG.info("Loaded bundled model weights");
			return network;
		} catch (IOException | RuntimeException e) {
			BrickByBrickTab.LOG.error("Could not load bundled model weights", e);
			return null;
		}
	}
}
