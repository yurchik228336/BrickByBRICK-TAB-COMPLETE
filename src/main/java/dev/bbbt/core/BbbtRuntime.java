package dev.bbbt.core;

import dev.bbbt.BrickByBrickTab;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.data.PlacementJournal;
import dev.bbbt.lora.LoraAdapter;
import dev.bbbt.lora.LoraTrainer;
import dev.bbbt.model.ModelLoader;
import dev.bbbt.model.ModelSpec;
import dev.bbbt.palette.BlockPalette;
import dev.bbbt.store.PlacedBlockStore;
import dev.bbbt.suggest.ContextBuilder;
import dev.bbbt.suggest.SuggestionEngine;
import dev.bbbt.text.CaptionService;
import dev.bbbt.track.PlacementTracker;
import dev.bbbt.net.TelemetryClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BbbtRuntime {

	private static final int TRAINING_CHUNK = 96;
	private static final long AUTOSAVE_INTERVAL_MS = 60_000L;

	private static final long CAPTION_SWEEP_INTERVAL_MS = 300_000L;

	private static BbbtRuntime instance;

	private final Path rootDir;
	private final Path configPath;
	private final Path modelDir;
	private final Path profileDir;
	private final Path worldDir;
	private final Path datasetDir;

	private final BbbtConfig config;
	private final PlacementTracker tracker;
	private final ExecutorService worker;
	private final CaptionService captions;
	private final TelemetryClient telemetry;
	private final AtomicBoolean predictionInFlight = new AtomicBoolean();

	private BlockPalette palette;
	private dev.bbbt.nn.SuggestionNetwork network;
	private SuggestionEngine engine;
	private ContextBuilder contextBuilder;
	private LoraAdapter adapter;
	private LoraTrainer trainer;
	private String modelOrigin = "none";

	private String worldId;
	private long lastSaveAt;
	private long lastCaptionSweepAt;

	private BbbtRuntime() {
		this.rootDir = FabricLoader.getInstance().getConfigDir().resolve(BrickByBrickTab.MOD_ID);
		this.configPath = rootDir.resolve("config.json");
		this.modelDir = rootDir.resolve("model");
		this.profileDir = rootDir.resolve("profiles");
		this.worldDir = rootDir.resolve("worlds");
		this.datasetDir = rootDir.resolve("dataset");

		this.config = BbbtConfig.load(configPath);
		this.tracker = new PlacementTracker(config);

		ThreadFactory factory = runnable -> {
			Thread thread = new Thread(runnable, "bbbt-inference");
			thread.setDaemon(true);
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			return thread;
		};
		this.worker = Executors.newSingleThreadExecutor(factory);
		this.captions = new CaptionService(config, datasetDir.resolve("captions.jsonl"),
				worker, versionOf("minecraft"), versionOf(BrickByBrickTab.MOD_ID));
		this.telemetry = new TelemetryClient(config, datasetDir.resolve("telemetry-state.json"),
				versionOf("minecraft"), versionOf(BrickByBrickTab.MOD_ID));

		reloadModel();
	}

	private static String versionOf(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	public static synchronized BbbtRuntime get() {
		if (instance == null) {
			instance = new BbbtRuntime();
		}
		return instance;
	}

	public BbbtConfig config() {
		return config;
	}

	public PlacementTracker tracker() {
		return tracker;
	}

	public SuggestionEngine engine() {
		return engine;
	}

	public ContextBuilder contextBuilder() {
		return contextBuilder;
	}

	public LoraAdapter adapter() {
		return adapter;
	}

	public BlockPalette palette() {
		return palette;
	}

	public CaptionService captions() {
		return captions;
	}

	public TelemetryClient telemetry() {
		return telemetry;
	}

	public String modelOrigin() {
		return modelOrigin;
	}

	public boolean hasModel() {
		return engine != null && engine.hasModel();
	}

	public Path modelDir() {
		return modelDir;
	}

	public void saveConfig() {
		config.save(configPath);
	}

	public void reloadModel() {
		ModelLoader.Loaded loaded = ModelLoader.load(modelDir, config.modelOverridePath);
		this.palette = loaded.palette();
		this.network = loaded.network();
		this.modelOrigin = loaded.origin();
		this.contextBuilder = new ContextBuilder(palette);

		SuggestionEngine newEngine = new SuggestionEngine(palette);
		newEngine.setNetwork(network);
		this.engine = newEngine;

		if (network != null) {
			this.adapter = loadAdapter();
			this.trainer = new LoraTrainer(network, palette);
		} else {
			this.adapter = null;
			this.trainer = null;
		}
	}

	private LoraAdapter loadAdapter() {
		return LoraAdapter.loadOrCreate(adapterPath(), network.dim(), ModelSpec.GRID_VOLUME,
				network.vocabSize(), network.orientCount(),
				config.loraRank, config.loraAlpha, config.installId.hashCode());
	}

	private Path adapterPath() {
		String name = switch (config.loraScope) {
			case GLOBAL -> "global";
			case PER_WORLD -> worldId != null ? worldId : "global";
		};
		return profileDir.resolve(name + ".lora");
	}

	public void onJoinWorld(String identity) {
		this.worldId = sanitiseIdentity(identity);
		Path storePath = worldDir.resolve(worldId).resolve("placed.bin");
		Path journalPath = worldDir.resolve(worldId).resolve("journal.bin");

		tracker.bind(PlacedBlockStore.loadOrCreate(storePath),
				PlacementJournal.loadOrCreate(journalPath, config.journalCapacity));

		if (config.loraScope == BbbtConfig.AdapterScope.PER_WORLD && network != null) {
			worker.execute(() -> this.adapter = loadAdapter());
		}
		lastSaveAt = System.currentTimeMillis();
		lastCaptionSweepAt = lastSaveAt;
		BrickByBrickTab.LOG.info("Tracking placements for world '{}' ({} blocks known)",
				worldId, tracker.store().trackedBlocks());
	}

	public void onLeaveWorld() {

		sweepCaptions();
		flush();
		worldId = null;
	}

	public void sweepCaptions() {
		if (worldId == null) {
			return;
		}
		lastCaptionSweepAt = System.currentTimeMillis();
		captions.sweepAutoCaptions(tracker.journal().snapshot(), captionLocale());
	}

	public Locale captionLocale() {
		String tag = config.captionLanguage;
		return tag == null || tag.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(tag);
	}

	public void flush() {
		if (worldId == null) {
			return;
		}
		Path dir = worldDir.resolve(worldId);
		try {
			if (tracker.store().isDirty()) {
				tracker.store().save(dir.resolve("placed.bin"));
			}
			tracker.journal().save(dir.resolve("journal.bin"));
		} catch (IOException e) {
			BrickByBrickTab.LOG.error("Could not persist tracked placements", e);
		}

		if (adapter != null && !adapter.isNeutral()) {
			try {
				adapter.save(adapterPath());
			} catch (IOException e) {
				BrickByBrickTab.LOG.error("Could not save personalisation profile", e);
			}
		}
		lastSaveAt = System.currentTimeMillis();
		worker.execute(() -> telemetry.maybeUpload(tracker.journal().snapshot(),
				captions.dataset()));
	}

	public void tick() {
		tracker.endTick();

		if (config.loraEnabled && config.loraAutoTrain && trainer != null
				&& tracker.placementsSinceTraining() >= config.loraTrainEveryPlacements) {
			tracker.clearTrainingCounter();
			submitTraining(TRAINING_CHUNK);
		}

		if (System.currentTimeMillis() - lastSaveAt > AUTOSAVE_INTERVAL_MS) {
			flush();
		}

		if (System.currentTimeMillis() - lastCaptionSweepAt > CAPTION_SWEEP_INTERVAL_MS) {
			sweepCaptions();
		}
	}

	public void submitTraining(int maxSamples) {
		if (trainer == null || adapter == null) {
			return;
		}
		var snapshot = tracker.journal().snapshot();
		LoraTrainer localTrainer = trainer;
		LoraAdapter localAdapter = adapter;
		worker.execute(() -> {
			LoraTrainer.Result result = localTrainer.train(snapshot, localAdapter, config,
					maxSamples);
			if (result.didWork()) {
				BrickByBrickTab.LOG.debug(
						"Personalisation: {} samples, loss pos={} block={} orient={}",
						result.samples(), result.positionLoss(), result.blockLoss(),
						result.orientationLoss());
			}
		});
	}

	public boolean submitPrediction(Runnable task) {
		if (!predictionInFlight.compareAndSet(false, true)) {
			return false;
		}
		worker.execute(() -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				BrickByBrickTab.LOG.error("Suggestion failed", e);
			} finally {
				predictionInFlight.set(false);
			}
		});
		return true;
	}

	public void resetPersonalisation() {
		if (adapter != null) {
			adapter.reset();
			try {
				adapter.save(adapterPath());
			} catch (IOException e) {
				BrickByBrickTab.LOG.error("Could not clear personalisation profile", e);
			}
		}
	}

	public void deleteCollectedData() {
		captions.forgetEverything();
		worker.execute(telemetry::requestDeletion);
	}

	public void workerDeleteCaptions() {
		worker.execute(telemetry::requestCaptionDeletion);
	}

	public void workerDeletePlacements() {
		worker.execute(telemetry::requestPlacementDeletion);
	}

	public void shutdown() {
		flush();
		saveConfig();
		worker.shutdown();
		try {
			if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
				worker.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			worker.shutdownNow();
		}
	}

	private static String sanitiseIdentity(String identity) {
		String cleaned = identity.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
		if (cleaned.length() > 64) {
			cleaned = cleaned.substring(0, 48) + "_" + Integer.toHexString(identity.hashCode());
		}
		return cleaned.isBlank() ? "unknown" : cleaned;
	}
}
