package dev.bbbt.text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

public final class CaptionDataset {

	private static final long MAX_BYTES = 32L * 1024 * 1024;

	private final Path file;
	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private final Set<String> knownIds = new HashSet<>();
	private boolean idsLoaded;

	public CaptionDataset(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	public synchronized boolean append(CaptionSample sample) throws IOException {
		loadIds();
		if (sample.id == null || !knownIds.add(sample.id)) {
			return false;
		}
		Files.createDirectories(file.getParent());
		rotateIfLarge();
		String line = gson.toJson(sample);
		try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
			out.write(line);
			out.write('\n');
		}
		return true;
	}

	public synchronized List<CaptionSample> read(int limit) throws IOException {
		List<CaptionSample> out = new ArrayList<>();
		if (!Files.isRegularFile(file)) {
			return out;
		}
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = in.readLine()) != null && out.size() < limit) {
				CaptionSample sample = parse(line);
				if (sample != null) {
					out.add(sample);
				}
			}
		}
		return out;
	}

	public synchronized int count() throws IOException {
		if (!Files.isRegularFile(file)) {
			return 0;
		}
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			int n = 0;
			while (in.readLine() != null) {
				n++;
			}
			return n;
		}
	}

	public synchronized int remove(Set<String> ids) throws IOException {
		if (ids.isEmpty() || !Files.isRegularFile(file)) {
			return 0;
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		int removed = 0;
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8);
				BufferedWriter out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			String line;
			while ((line = in.readLine()) != null) {
				CaptionSample sample = parse(line);
				if (sample != null && ids.contains(sample.id)) {
					removed++;
					knownIds.remove(sample.id);
					continue;
				}
				out.write(line);
				out.write('\n');
			}
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		return removed;
	}

	public synchronized void deleteAll() throws IOException {
		Files.deleteIfExists(file);
		knownIds.clear();
		idsLoaded = true;
	}

	private CaptionSample parse(String line) {
		if (line.isBlank()) {
			return null;
		}
		try {
			return gson.fromJson(line, CaptionSample.class);
		} catch (JsonSyntaxException e) {

			return null;
		}
	}

	private void loadIds() throws IOException {
		if (idsLoaded) {
			return;
		}
		idsLoaded = true;
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = in.readLine()) != null) {
				CaptionSample sample = parse(line);
				if (sample != null && sample.id != null) {
					knownIds.add(sample.id);
				}
			}
		}
	}

	private void rotateIfLarge() throws IOException {
		if (!Files.isRegularFile(file) || Files.size(file) < MAX_BYTES) {
			return;
		}
		Path archive = file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis());
		Files.move(file, archive, StandardCopyOption.REPLACE_EXISTING);
		knownIds.clear();
	}
}
