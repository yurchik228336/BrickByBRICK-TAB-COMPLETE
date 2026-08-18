package dev.bbbt.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.bbbt.build.BuildSegment;
import dev.bbbt.build.CaptionGenerator;
import dev.bbbt.build.StructureAnalyzer;
import dev.bbbt.build.StructureAttributes;
import dev.bbbt.data.JournalSnapshot;

public final class CaptionCollector {

	private final StructureAnalyzer analyzer = new StructureAnalyzer();
	private final CaptionGenerator captions = new CaptionGenerator();
	private final TextSanitizer sanitizer = new TextSanitizer();
	private final String mcVersion;
	private final String modVersion;

	public CaptionCollector(String mcVersion, String modVersion) {
		this.mcVersion = mcVersion;
		this.modVersion = modVersion;
	}

	public CaptionSample auto(JournalSnapshot journal, BuildSegment segment, Locale locale) {
		StructureAttributes attributes = analyzer.analyse(journal, segment);
		String text = captions.caption(attributes, locale);
		return build(journal, segment, attributes, text, false,
				CaptionSample.SOURCE_AUTO, locale.getLanguage());
	}

	public CaptionSample fromPlayer(JournalSnapshot journal, BuildSegment segment,
			String rawText, String lang) {
		return fromText(journal, segment, rawText, lang, CaptionSample.SOURCE_PLAYER);
	}

	public CaptionSample fromSign(JournalSnapshot journal, BuildSegment segment,
			String rawText, String lang) {
		return fromText(journal, segment, rawText, lang, CaptionSample.SOURCE_SIGN);
	}

	public CaptionSample fromText(JournalSnapshot journal, BuildSegment segment,
			String rawText, String lang, String source) {
		TextSanitizer.Result result = sanitizer.sanitize(rawText);
		if (!(result instanceof TextSanitizer.Result.Accepted accepted)) {
			return null;
		}
		StructureAttributes attributes = analyzer.analyse(journal, segment);
		return build(journal, segment, attributes, accepted.text(), accepted.redacted(),
				source, lang);
	}

	public StructureAttributes analyse(JournalSnapshot journal, BuildSegment segment) {
		return analyzer.analyse(journal, segment);
	}

	public String suggestCaption(StructureAttributes attributes, Locale locale) {
		return captions.caption(attributes, locale);
	}

	private CaptionSample build(JournalSnapshot journal, BuildSegment segment,
			StructureAttributes attributes, String text, boolean redacted,
			String source, String lang) {
		CaptionSample sample = new CaptionSample();
		sample.source = source;
		sample.lang = lang;
		sample.text = text;
		sample.redacted = redacted;
		sample.width = attributes.width();
		sample.height = attributes.height();
		sample.depth = attributes.depth();
		sample.placements = attributes.placements();
		sample.attributes = flatten(attributes);
		sample.mcVersion = mcVersion;
		sample.modVersion = modVersion;
		sample.day = Instant.now().getEpochSecond() / Duration.ofDays(1).getSeconds();

		Map<String, Integer> paletteIndex = new HashMap<>();
		List<String> palette = new ArrayList<>();
		int n = segment.size();
		int[][] steps = new int[n][];
		for (int i = 0; i < n; i++) {
			int j = segment.from() + i;
			String name = journal.names()[j];
			Integer index = paletteIndex.get(name);
			if (index == null) {
				index = palette.size();
				paletteIndex.put(name, index);
				palette.add(name);
			}
			steps[i] = new int[] {
					journal.x()[j] - segment.minX(),
					journal.y()[j] - segment.minY(),
					journal.z()[j] - segment.minZ(),
					index,
					journal.orientations()[j],
			};
		}
		sample.palette = palette;
		sample.steps = steps;
		sample.id = hash(text, palette, steps);
		return sample;
	}

	private static CaptionSample.Attributes flatten(StructureAttributes a) {
		CaptionSample.Attributes out = new CaptionSample.Attributes();
		out.materials = new ArrayList<>();
		for (StructureAttributes.MaterialShare m : a.materials()) {
			out.materials.add(m.family());
		}
		out.hollowness = round(a.hollowness());
		out.symmetryX = round(a.symmetryX());
		out.symmetryZ = round(a.symmetryZ());
		out.glassCount = a.glassCount();
		out.doorCount = a.doorCount();
		out.stairCount = a.stairCount();
		out.slabCount = a.slabCount();
		out.lightCount = a.lightCount();
		out.hasFloor = a.hasFloor();
		out.hasWalls = a.hasWalls();
		out.hasRoof = a.hasRoof();
		out.slopedRoof = a.slopedRoof();
		out.footprint = a.footprint().name().toLowerCase(Locale.ROOT);
		out.heightClass = a.heightClass().name().toLowerCase(Locale.ROOT);
		out.sizeClass = a.sizeClass().name().toLowerCase(Locale.ROOT);
		out.purposes = new ArrayList<>();
		for (StructureAttributes.Purpose p : a.purposes()) {
			out.purposes.add(p.name().toLowerCase(Locale.ROOT));
		}
		return out;
	}

	private static double round(double v) {
		return Math.round(v * 1000.0) / 1000.0;
	}

	private static String hash(String text, List<String> palette, int[][] steps) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(text.getBytes(StandardCharsets.UTF_8));
			for (String name : palette) {
				digest.update((byte) 0);
				digest.update(name.getBytes(StandardCharsets.UTF_8));
			}
			byte[] scratch = new byte[5 * 4];
			for (int[] step : steps) {
				for (int k = 0; k < 5; k++) {
					int v = step[k];
					scratch[k * 4] = (byte) (v >>> 24);
					scratch[k * 4 + 1] = (byte) (v >>> 16);
					scratch[k * 4 + 2] = (byte) (v >>> 8);
					scratch[k * 4 + 3] = (byte) v;
				}
				digest.update(scratch);
			}
			byte[] out = digest.digest();
			StringBuilder sb = new StringBuilder(32);
			for (int i = 0; i < 16; i++) {
				sb.append(Character.forDigit((out[i] >> 4) & 0xF, 16));
				sb.append(Character.forDigit(out[i] & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the platform", e);
		}
	}
}
