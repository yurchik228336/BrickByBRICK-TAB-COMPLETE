package dev.bbbt.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.bbbt.BrickByBrickTab;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.model.ModelLoader;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public final class ModelSync {

	public static final String VERSION_FILE = "version.txt";

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final long MAX_WEIGHTS = 80L << 20;
	private static final long MAX_PALETTE = 2L << 20;
	private static final HexFormat HEX = HexFormat.of();

	private final BbbtConfig config;
	private final Path modelDir;
	private final HttpClient http;

	public ModelSync(BbbtConfig config, Path modelDir) {
		this.config = config;
		this.modelDir = modelDir;
		this.http = HttpClient.newBuilder()
				.proxy(ProxySelector.getDefault())
				.connectTimeout(Duration.ofSeconds(8))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	/**
	 * Downloads the catalog latest into {@code modelDir} when it differs from what is
	 * already installed. Returns true if files on disk changed and the runtime should
	 * reload. Override paths are never overwritten.
	 */
	public boolean sync(boolean force) {
		if (!force && !config.autoUpdateModel) {
			return false;
		}
		if (config.modelOverridePath != null && !config.modelOverridePath.isBlank()) {
			return false;
		}
		String base = endpoint();
		if (base.isBlank()) {
			return false;
		}
		Latest latest = fetchLatest(base);
		if (latest == null) {
			return false;
		}
		if (!validVersion(latest.version) || !validSha(latest.weightsSha256)
				|| !validSha(latest.paletteSha256)) {
			BrickByBrickTab.LOG.warn("Ignoring malformed model catalog entry");
			return false;
		}
		if (latest.weightsBytes <= 0 || latest.weightsBytes > MAX_WEIGHTS
				|| latest.paletteBytes <= 0 || latest.paletteBytes > MAX_PALETTE) {
			BrickByBrickTab.LOG.warn("Ignoring model {} with implausible size", latest.version);
			return false;
		}

		Path weightsPath = modelDir.resolve(ModelLoader.WEIGHTS_FILE);
		Path palettePath = modelDir.resolve(ModelLoader.PALETTE_FILE);
		if (!force && latest.version.equals(readInstalledVersion())
				&& shaOf(weightsPath).equalsIgnoreCase(latest.weightsSha256)
				&& shaOf(palettePath).equalsIgnoreCase(latest.paletteSha256)) {
			return false;
		}

		Path tmp = modelDir.resolve(".download");
		try {
			Files.createDirectories(tmp);
			Path weightsTmp = tmp.resolve(ModelLoader.WEIGHTS_FILE);
			Path paletteTmp = tmp.resolve(ModelLoader.PALETTE_FILE);
			download(join(base, "/v1/model/" + latest.version + "/weights"), weightsTmp,
					latest.weightsBytes, MAX_WEIGHTS);
			download(join(base, "/v1/model/" + latest.version + "/palette"), paletteTmp,
					latest.paletteBytes, MAX_PALETTE);
			if (!shaOf(weightsTmp).equalsIgnoreCase(latest.weightsSha256)
					|| !shaOf(paletteTmp).equalsIgnoreCase(latest.paletteSha256)) {
				BrickByBrickTab.LOG.warn("Model {} failed sha256 check, leaving the current files",
						latest.version);
				return false;
			}
			Files.createDirectories(modelDir);
			Files.move(weightsTmp, weightsPath, StandardCopyOption.REPLACE_EXISTING);
			Files.move(paletteTmp, palettePath, StandardCopyOption.REPLACE_EXISTING);
			writeVersion(latest.version);
			BrickByBrickTab.LOG.info("Installed model {} from {}", latest.version, base);
			return true;
		} catch (IOException e) {
			BrickByBrickTab.LOG.warn("Could not install model {}: {}", latest.version, e.toString());
			return false;
		} finally {
			deleteQuietly(tmp);
		}
	}

	public static String readVersionFile(Path modelDir) {
		Path path = modelDir.resolve(VERSION_FILE);
		if (!Files.isRegularFile(path)) {
			return "";
		}
		try {
			String value = Files.readString(path).trim();
			return validVersion(value) ? value : "";
		} catch (IOException e) {
			return "";
		}
	}

	private String endpoint() {
		if (config.modelEndpoint != null && !config.modelEndpoint.isBlank()) {
			return config.modelEndpoint.trim();
		}
		return config.telemetryEndpoint == null ? "" : config.telemetryEndpoint.trim();
	}

	private String readInstalledVersion() {
		return readVersionFile(modelDir);
	}

	private void writeVersion(String version) throws IOException {
		Path path = modelDir.resolve(VERSION_FILE);
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.writeString(tmp, version + "\n");
		Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
	}

	private Latest fetchLatest(String base) {
		URI uri = parse(join(base, "/v1/model/latest"));
		if (uri == null) {
			return null;
		}
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(12))
				.GET()
				.build();
		try {
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 404) {
				return null;
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				BrickByBrickTab.LOG.warn("Model catalog {} returned {}", uri, response.statusCode());
				return null;
			}
			return GSON.fromJson(response.body(), Latest.class);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException | JsonSyntaxException e) {
			BrickByBrickTab.LOG.warn("Model catalog {} failed: {}", uri, e.toString());
			return null;
		}
	}

	private void download(String url, Path dest, long expected, long max) throws IOException {
		URI uri = parse(url);
		if (uri == null) {
			throw new IOException("malformed model URL");
		}
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(120))
				.GET()
				.build();
		try {
			HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(dest));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IOException("GET " + uri + " returned " + response.statusCode());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted downloading " + uri);
		}
		long size = Files.size(dest);
		if (size != expected || size > max) {
			Files.deleteIfExists(dest);
			throw new IOException("downloaded " + size + " bytes, expected " + expected);
		}
	}

	private static boolean validVersion(String value) {
		return value != null && value.matches("[A-Za-z0-9._-]{1,32}");
	}

	private static boolean validSha(String value) {
		return value != null && value.matches("[0-9a-fA-F]{64}");
	}

	private static String shaOf(Path path) {
		if (!Files.isRegularFile(path)) {
			return "";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(Files.readAllBytes(path));
			return HEX.formatHex(digest.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			return "";
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
			BrickByBrickTab.LOG.warn("Ignoring malformed model URL: {}", url);
			return null;
		}
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null || !Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			stream.forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			});
			Files.deleteIfExists(dir);
		} catch (IOException ignored) {
		}
	}

	static final class Latest {
		String version;
		String notes;
		String weightsSha256;
		String paletteSha256;
		long weightsBytes;
		long paletteBytes;
	}
}
