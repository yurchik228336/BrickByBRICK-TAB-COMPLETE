package dev.bbbt.track;

import dev.bbbt.config.BbbtConfig;
import dev.bbbt.data.PlacementJournal;
import dev.bbbt.palette.BlockStateCodec;
import dev.bbbt.store.PlacedBlockStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class PlacementTracker {

	private static final int EXPECTATION_TICKS = 10;

	private static final int EXPECTATION_RADIUS = 2;

	private record Expectation(int x, int y, int z, String blockName, long expiresAtTick) {
		boolean covers(int px, int py, int pz, String name, long tick) {
			return tick <= expiresAtTick
					&& blockName.equals(name)
					&& Math.abs(px - x) <= EXPECTATION_RADIUS
					&& Math.abs(py - y) <= EXPECTATION_RADIUS
					&& Math.abs(pz - z) <= EXPECTATION_RADIUS;
		}
	}

	private record Addition(int x, int y, int z, String blockName, int orientation) {
	}

	private final BbbtConfig config;

	private PlacedBlockStore store = new PlacedBlockStore();
	private PlacementJournal journal = new PlacementJournal(1024);

	private final List<Expectation> expectations = new ArrayList<>();
	private final List<Addition> tickAdditions = new ArrayList<>();

	private long tick;
	private int placementsSinceTraining;
	private int recordedThisSession;

	public PlacementTracker(BbbtConfig config) {
		this.config = config;
	}

	public PlacedBlockStore store() {
		return store;
	}

	public PlacementJournal journal() {
		return journal;
	}

	public int placementsSinceTraining() {
		return placementsSinceTraining;
	}

	public void clearTrainingCounter() {
		placementsSinceTraining = 0;
	}

	public int recordedThisSession() {
		return recordedThisSession;
	}

	public void bind(PlacedBlockStore store, PlacementJournal journal) {
		this.store = store;
		this.journal = journal;
		this.expectations.clear();
		this.tickAdditions.clear();
		this.recordedThisSession = 0;
		journal.beginSession();
	}

	public void expectPlacement(BlockPos interactedAt, String blockName) {
		expectations.add(new Expectation(interactedAt.getX(), interactedAt.getY(),
				interactedAt.getZ(), blockName, tick + EXPECTATION_TICKS));
	}

	public void onBlockAdded(BlockPos pos, BlockState state) {
		if (tickAdditions.size() > 4096) {
			return;
		}
		String name = BlockStateCodec.registryName(state);
		if (NaturalCauseFilter.isNaturalOnly(name)) {
			return;
		}
		tickAdditions.add(new Addition(pos.getX(), pos.getY(), pos.getZ(), name,
				BlockStateCodec.orientationOf(state)));
	}

	public void onBlockRemoved(BlockPos pos) {
		if (store.remove(pos.getX(), pos.getY(), pos.getZ())) {
			journal.forget(pos.getX(), pos.getY(), pos.getZ());
		}
	}

	public void endTick() {
		tick++;
		expectations.removeIf(e -> tick > e.expiresAtTick());

		if (tickAdditions.isEmpty()) {
			return;
		}

		boolean burst = tickAdditions.size() > config.bulkChangeThreshold;

		for (Addition addition : tickAdditions) {
			boolean mine = isLocalPlacement(addition);

			if (!mine) {

				if (burst
						|| !config.trackOtherPlayers
						|| NaturalCauseFilter.isGravityAffected(addition.blockName())) {
					continue;
				}
				store.put(addition.x(), addition.y(), addition.z(),
						addition.blockName(), addition.orientation());
				continue;
			}

			store.put(addition.x(), addition.y(), addition.z(),
					addition.blockName(), addition.orientation());
			journal.record(addition.x(), addition.y(), addition.z(),
					addition.blockName(), addition.orientation());
			placementsSinceTraining++;
			recordedThisSession++;
		}
		tickAdditions.clear();
	}

	private boolean isLocalPlacement(Addition addition) {
		for (Expectation expectation : expectations) {
			if (expectation.covers(addition.x(), addition.y(), addition.z(),
					addition.blockName(), tick)) {
				return true;
			}
		}
		return false;
	}
}
