package dev.bbbt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.bbbt.BrickByBrickTab;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class BbbtConfig {
	public enum ConsentState {
		UNASKED,
		GRANTED,
		DENIED
	}

	public enum AdapterScope {
		GLOBAL,
		PER_WORLD
	}

	public enum HudAnchor {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}

	public boolean enabled = true;

	public int suggestionCount = 3;

	public float confidenceThreshold = 0.15f;

	public int refreshDelayTicks = 3;

	public int previewDepth = 1;

	public boolean showGhostBlocks = true;
	public boolean showHudList = true;
	public HudAnchor hudAnchor = HudAnchor.TOP_LEFT;
	public int hudOffsetX = 4;
	public int hudOffsetY = 4;
	public float ghostOpacity = 0.45f;
	public boolean showConfidence = true;
	public boolean showOutline = true;

	public boolean ghostLookOnly = true;

	public boolean fastPlaceFromInventory = false;

	public boolean focusRegionEnabled = true;

	public boolean trackOtherPlayers = true;

	public int bulkChangeThreshold = 12;
	public int journalCapacity = 50_000;

	public boolean loraEnabled = true;

	public float loraStrength = 1.0f;
	public int loraRank = 8;
	public float loraAlpha = 16f;
	public float loraLearningRate = 0.003f;
	public float loraWeightDecay = 0.01f;
	public boolean loraAutoTrain = true;

	public int loraTrainEveryPlacements = 128;
	public AdapterScope loraScope = AdapterScope.GLOBAL;

	public ConsentState placementConsent = ConsentState.UNASKED;
	public String telemetryEndpoint = "https://bbb.ruscreat.dev";

	public String installId = "";

	public ConsentState captionConsent = ConsentState.UNASKED;

	public boolean captionCollectLocally = true;

	public boolean captionAutoGenerate = true;

	public boolean captionHarvestSigns = false;

	public boolean captionReviewBeforeUpload = true;
	public String captionEndpoint = "https://bbb.ruscreat.dev";

	public String captionLanguage = "";

	public int captionSegmentGapSeconds = 300;
	public int captionSegmentJumpDistance = 24;
	public int captionMinPlacements = 12;
	public int captionMaxPlacements = 4096;

	public String modelOverridePath = "";
	public String modelEndpoint = "";
	public boolean autoUpdateModel = true;
	public boolean autoPlaceEnabled = false;
	public boolean debugOverlay = false;

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	public void sanitise() {
		suggestionCount = Math.clamp(suggestionCount, 1, 8);
		confidenceThreshold = Math.clamp(confidenceThreshold, 0f, 0.99f);
		refreshDelayTicks = Math.clamp(refreshDelayTicks, 0, 40);
		previewDepth = Math.clamp(previewDepth, 1, 8);
		ghostOpacity = Math.clamp(ghostOpacity, 0.05f, 1f);
		bulkChangeThreshold = Math.clamp(bulkChangeThreshold, 2, 512);
		journalCapacity = Math.clamp(journalCapacity, 1_000, 500_000);
		loraStrength = Math.clamp(loraStrength, 0f, 4f);
		loraRank = Math.clamp(loraRank, 1, 64);
		loraAlpha = Math.clamp(loraAlpha, 0.1f, 128f);
		loraLearningRate = Math.clamp(loraLearningRate, 1e-5f, 0.5f);
		loraWeightDecay = Math.clamp(loraWeightDecay, 0f, 1f);
		loraTrainEveryPlacements = Math.clamp(loraTrainEveryPlacements, 8, 100_000);
		hudOffsetX = Math.clamp(hudOffsetX, -4096, 4096);
		hudOffsetY = Math.clamp(hudOffsetY, -4096, 4096);
		captionSegmentGapSeconds = Math.clamp(captionSegmentGapSeconds, 10, 7_200);
		captionSegmentJumpDistance = Math.clamp(captionSegmentJumpDistance, 4, 256);
		captionMinPlacements = Math.clamp(captionMinPlacements, 1, 4_096);
		captionMaxPlacements = Math.clamp(captionMaxPlacements, 64, 100_000);

		if (installId == null || installId.isBlank()) {
			installId = UUID.randomUUID().toString();
		}
		if (placementConsent == null) {
			placementConsent = ConsentState.UNASKED;
		}
		if (captionConsent == null) {
			captionConsent = ConsentState.UNASKED;
		}
		if (captionEndpoint == null) {
			captionEndpoint = "";
		}
		if (captionLanguage == null) {
			captionLanguage = "";
		}
		if (loraScope == null) {
			loraScope = AdapterScope.GLOBAL;
		}
		if (hudAnchor == null) {
			hudAnchor = HudAnchor.TOP_LEFT;
		}
		if (telemetryEndpoint == null) {
			telemetryEndpoint = "";
		}
		if (modelOverridePath == null) {
			modelOverridePath = "";
		}
		if (modelEndpoint == null) {
			modelEndpoint = "";
		}
	}

	public boolean mayUploadTelemetry() {
		return placementConsent == ConsentState.GRANTED && !telemetryEndpoint.isBlank();
	}

	public boolean mayUploadCaptions() {
		return captionConsent == ConsentState.GRANTED && !captionEndpoint.isBlank();
	}

	public boolean hasPendingConsentQuestion() {
		return placementConsent == ConsentState.UNASKED || captionConsent == ConsentState.UNASKED;
	}

	public boolean mayStoreCaptions() {
		return captionCollectLocally && captionConsent == ConsentState.GRANTED;
	}

	public static BbbtConfig load(Path path) {
		BbbtConfig config = null;
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, BbbtConfig.class);
			} catch (IOException | RuntimeException e) {
				BrickByBrickTab.LOG.warn("Could not read config, falling back to defaults", e);
			}
		}
		if (config == null) {
			config = new BbbtConfig();
		}
		config.sanitise();
		return config;
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			BrickByBrickTab.LOG.error("Could not save config", e);
		}
	}
}
