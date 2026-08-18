package dev.bbbt.palette;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlockPalette {

	public static final int TOKEN_NONE = 0;

	public static final int TOKEN_UNK = 1;
	public static final int FIRST_BLOCK_TOKEN = 2;

	private final String[] tokenToName;
	private final Map<String, Integer> nameToToken;

	private BlockPalette(String[] tokenToName, Map<String, Integer> nameToToken) {
		this.tokenToName = tokenToName;
		this.nameToToken = nameToToken;
	}

	public int vocabSize() {
		return tokenToName.length;
	}

	public int blockCount() {
		return tokenToName.length - FIRST_BLOCK_TOKEN;
	}

	public int tokenOf(String registryName) {
		Integer token = nameToToken.get(registryName);
		return token != null ? token : TOKEN_UNK;
	}

	public boolean contains(String registryName) {
		return nameToToken.containsKey(registryName);
	}

	public String nameOf(int token) {
		return token >= FIRST_BLOCK_TOKEN && token < tokenToName.length ? tokenToName[token] : "";
	}

	public static BlockPalette load(InputStream in) throws IOException {
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			int format = root.has("format") ? root.get("format").getAsInt() : 1;
			if (format != 1) {
				throw new IOException("Unsupported palette format: " + format);
			}

			JsonArray blocks = root.getAsJsonArray("blocks");
			List<String> names = new ArrayList<>(blocks.size() + FIRST_BLOCK_TOKEN);
			names.add("");
			names.add("");

			Map<String, Integer> index = new HashMap<>(blocks.size() * 2);
			for (int i = 0; i < blocks.size(); i++) {
				String name = blocks.get(i).getAsString();
				if (index.putIfAbsent(name, names.size()) == null) {
					names.add(name);
				}
			}

			if (root.has("aliases")) {
				JsonObject aliases = root.getAsJsonObject("aliases");
				for (Map.Entry<String, com.google.gson.JsonElement> entry : aliases.entrySet()) {
					Integer target = index.get(entry.getValue().getAsString());
					if (target != null) {
						index.putIfAbsent(entry.getKey(), target);
					}
				}
			}

			return new BlockPalette(names.toArray(new String[0]), index);
		} catch (RuntimeException e) {
			throw new IOException("Malformed palette", e);
		}
	}

	public static BlockPalette empty() {
		return new BlockPalette(new String[] { "", "" }, new HashMap<>());
	}
}
