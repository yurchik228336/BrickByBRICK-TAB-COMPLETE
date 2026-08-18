package dev.bbbt.net;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.bbbt.BrickByBrickTab;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.data.JournalSnapshot;
import dev.bbbt.text.CaptionDataset;
import dev.bbbt.text.CaptionSample;

public final class TelemetryClient {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final int MIN_STEPS = 3;
	private static final int MAX_SEQUENCES = 64;
	private static final int MAX_CAPTIONS = 32;

	private final BbbtConfig config;
	private final Path statePath;
	private final String mcVersion;
	private final String modVersion;
	private final HttpClient http;

	private final Set<String> uploadedCaptionIds = new HashSet<>();
	private int lastPlacementCount;

	public TelemetryClient(BbbtConfig config, Path statePath, String mcVersion, String modVersion) {
		this.config = config;
		this.statePath = statePath;
		this.mcVersion = mcVersion;
		this.modVersion = modVersion;
		this.http = HttpClient.newBuilder()
				.proxy(ProxySelector.getDefault())
				.connectTimeout(Duration.ofSeconds(8))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		loadState();
	}

	public void maybeUpload(JournalSnapshot journal, CaptionDataset captions) {
		if (config.mayUploadTelemetry()) {
			uploadPlacements(journal);
		}
		if (config.mayUploadCaptions()) {
			uploadCaptions(captions);
		}
	}

	public void requestDeletion() {
		requestPlacementDeletion();
		requestCaptionDeletion();
	}

	public void requestPlacementDeletion() {
		deleteAt(config.telemetryEndpoint);
	}

	public void requestCaptionDeletion() {
		deleteAt(config.captionEndpoint);
	}

	private void uploadPlacements(JournalSnapshot journal) {
		if (journal.size() <= lastPlacementCount) {
			return;
		}
		List<Sequence> sequences = sequencesFrom(journal);
		if (sequences.isEmpty()) {
			lastPlacementCount = journal.size();
			saveState();
			return;
		}
		if (sequences.size() > MAX_SEQUENCES) {
			sequences = new ArrayList<>(sequences.subList(sequences.size() - MAX_SEQUENCES,
					sequences.size()));
		}

		PlacementBatch batch = new PlacementBatch();
		batch.installId = config.installId;
		batch.mcVersion = mcVersion;
		batch.modVersion = modVersion;
		batch.sequences = sequences;

		if (post(join(config.telemetryEndpoint, "/v1/placements"), batch)) {
			lastPlacementCount = journal.size();
			saveState();
		}
	}

	private void uploadCaptions(CaptionDataset dataset) {
		try {
			List<CaptionSample> pending = new ArrayList<>();
			for (CaptionSample sample : dataset.read(4096)) {
				if (sample.id != null && uploadedCaptionIds.add(sample.id)) {
					pending.add(sample);
				}
			}
			if (pending.isEmpty()) {
				return;
			}
			if (pending.size() > MAX_CAPTIONS) {
				pending = pending.subList(0, MAX_CAPTIONS);
			}

			CaptionBatch batch = new CaptionBatch();
			batch.installId = config.installId;
			batch.samples = pending;
			if (post(join(config.captionEndpoint, "/v1/captions"), batch)) {
				saveState();
			} else {
				for (CaptionSample sample : pending) {
					uploadedCaptionIds.remove(sample.id);
				}
			}
		} catch (IOException e) {
			BrickByBrickTab.LOG.warn("Could not read captions for upload", e);
		}
	}

	static List<Sequence> sequencesFrom(JournalSnapshot journal) {
		List<Sequence> out = new ArrayList<>();
		int n = journal.size();
		if (n == 0) {
			return out;
		}

		int from = 0;
		for (int i = 1; i <= n; i++) {
			boolean cut = i == n || journal.sessions()[i] != journal.sessions()[i - 1];
			if (!cut) {
				continue;
			}
			int length = i - from;
			if (length >= MIN_STEPS) {
				int originX = journal.x()[from];
				int originY = journal.y()[from];
				int originZ = journal.z()[from];
				Sequence sequence = new Sequence();
				sequence.session = journal.sessions()[from];
				sequence.steps = new ArrayList<>(length);
				for (int s = from; s < i; s++) {
					Step step = new Step();
					step.dx = journal.x()[s] - originX;
					step.dy = journal.y()[s] - originY;
					step.dz = journal.z()[s] - originZ;
					step.block = journal.names()[s];
					step.orient = journal.orientations()[s];
					sequence.steps.add(step);
				}
				out.add(sequence);
			}
			from = i;
		}
		return out;
	}

	private boolean post(String url, Object body) {
		URI uri = parse(url);
		if (uri == null) {
			return false;
		}
		String json = GSON.toJson(body);
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				BrickByBrickTab.LOG.debug("Telemetry POST {} -> {}", uri, response.statusCode());
				return true;
			}
			BrickByBrickTab.LOG.warn("Telemetry POST {} returned {}", uri, response.statusCode());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			BrickByBrickTab.LOG.warn("Telemetry POST {} failed: {}", uri, e.toString());
		}
		return false;
	}

	private void deleteAt(String endpoint) {
		if (endpoint == null || endpoint.isBlank() || config.installId.isBlank()) {
			return;
		}
		URI uri = parse(join(endpoint, "/v1/data/" + config.installId));
		if (uri == null) {
			return;
		}
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(15))
				.DELETE()
				.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			BrickByBrickTab.LOG.info("Deletion at {} -> {}", uri, response.statusCode());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			BrickByBrickTab.LOG.warn("Deletion at {} failed: {}", uri, e.toString());
		}
	}

	private static String join(String endpoint, String path) {
		if (endpoint.endsWith("/")) {
			endpoint = endpoint.substring(0, endpoint.length() - 1);
		}
		return endpoint + path;
	}

	private static URI parse(String url) {
		try {
			URI uri = URI.create(url);
			if (uri.getScheme() == null || uri.getHost() == null) {
				return null;
			}
			return uri;
		} catch (IllegalArgumentException e) {
			BrickByBrickTab.LOG.warn("Ignoring malformed telemetry URL: {}", url);
			return null;
		}
	}

	private void loadState() {
		if (!Files.isRegularFile(statePath)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
			State state = GSON.fromJson(reader, State.class);
			if (state != null) {
				lastPlacementCount = Math.max(0, state.lastPlacementCount);
				if (state.uploadedCaptionIds != null) {
					uploadedCaptionIds.addAll(state.uploadedCaptionIds);
				}
			}
		} catch (IOException | RuntimeException e) {
			BrickByBrickTab.LOG.warn("Could not read telemetry state", e);
		}
	}

	private void saveState() {
		try {
			Files.createDirectories(statePath.getParent());
			State state = new State();
			state.lastPlacementCount = lastPlacementCount;
			state.uploadedCaptionIds = new ArrayList<>(uploadedCaptionIds);
			try (Writer writer = Files.newBufferedWriter(statePath, StandardCharsets.UTF_8)) {
				GSON.toJson(state, writer);
			}
		} catch (IOException e) {
			BrickByBrickTab.LOG.warn("Could not save telemetry state", e);
		}
	}

	static final class PlacementBatch {
		String installId;
		String mcVersion;
		String modVersion;
		List<Sequence> sequences;
	}

	static final class Sequence {
		int session;
		List<Step> steps;
	}

	static final class Step {
		int dx;
		int dy;
		int dz;
		String block;
		int orient;
	}

	static final class CaptionBatch {
		String installId;
		List<CaptionSample> samples;
	}

	static final class State {
		int lastPlacementCount;
		List<String> uploadedCaptionIds;
	}
}
