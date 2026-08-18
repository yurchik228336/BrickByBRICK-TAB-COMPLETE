package dev.bbbt.text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import dev.bbbt.BrickByBrickTab;
import dev.bbbt.build.BuildSegment;
import dev.bbbt.build.BuildSegmenter;
import dev.bbbt.build.StructureAttributes;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.data.JournalSnapshot;

public final class CaptionService {

	private final BbbtConfig config;
	private final CaptionDataset dataset;
	private final CaptionCollector collector;
	private final Executor executor;
	private final AtomicInteger writtenThisSession = new AtomicInteger();

	public CaptionService(BbbtConfig config, Path datasetFile, Executor executor,
			String mcVersion, String modVersion) {
		this.config = config;
		this.dataset = new CaptionDataset(datasetFile);
		this.collector = new CaptionCollector(mcVersion, modVersion);
		this.executor = executor;
	}

	public CaptionDataset dataset() {
		return dataset;
	}

	public int writtenThisSession() {
		return writtenThisSession.get();
	}

	private BuildSegmenter segmenter() {
		return new BuildSegmenter(config.captionSegmentGapSeconds, config.captionSegmentJumpDistance,
				config.captionMaxPlacements, config.captionMinPlacements,
				BuildSegmenter.DEFAULT_MIN_DENSITY);
	}

	public Described describeCurrent(JournalSnapshot journal, Locale locale) {
		BuildSegment segment = segmenter().currentSegment(journal);
		if (segment == null || segment.size() < config.captionMinPlacements) {
			return null;
		}
		StructureAttributes attributes = collector.analyse(journal, segment);
		return new Described(segment, attributes, collector.suggestCaption(attributes, locale));
	}

	public record Described(BuildSegment segment, StructureAttributes attributes,
			String suggestedCaption) {
	}

	public boolean annotate(JournalSnapshot journal, BuildSegment segment, String text,
			String lang) {
		if (!config.mayStoreCaptions()) {
			return false;
		}
		CaptionSample sample = collector.fromPlayer(journal, segment, text, lang);
		if (sample == null) {
			return false;
		}
		executor.execute(() -> write(sample));
		return true;
	}

	public void annotateFromSign(JournalSnapshot journal, BuildSegment segment, String text,
			String lang) {
		if (!config.mayStoreCaptions() || !config.captionHarvestSigns) {
			return;
		}
		executor.execute(() -> write(collector.fromSign(journal, segment, text, lang)));
	}

	public void sweepAutoCaptions(JournalSnapshot journal, Locale locale) {
		if (!config.mayStoreCaptions() || !config.captionAutoGenerate) {
			return;
		}
		executor.execute(() -> {
			List<BuildSegment> segments = segmenter().segment(journal);
			int added = 0;
			for (BuildSegment segment : segments) {
				if (write(collector.auto(journal, segment, locale))) {
					added++;
				}
			}
			if (added > 0) {
				BrickByBrickTab.LOG.debug("Text dataset: {} new captions from {} builds",
						added, segments.size());
			}
		});
	}

	private boolean write(CaptionSample sample) {
		if (sample == null) {
			return false;
		}
		try {
			if (dataset.append(sample)) {
				writtenThisSession.incrementAndGet();
				return true;
			}
		} catch (IOException e) {
			BrickByBrickTab.LOG.error("Could not write to the text dataset", e);
		}
		return false;
	}

	public void forgetEverything() {
		executor.execute(() -> {
			try {
				dataset.deleteAll();
				BrickByBrickTab.LOG.info("Text dataset deleted at the player's request");
			} catch (IOException e) {
				BrickByBrickTab.LOG.error("Could not delete the text dataset", e);
			}
		});
	}

	public void forget(Set<String> ids) {
		executor.execute(() -> {
			try {
				dataset.remove(ids);
			} catch (IOException e) {
				BrickByBrickTab.LOG.error("Could not remove samples from the text dataset", e);
			}
		});
	}
}
