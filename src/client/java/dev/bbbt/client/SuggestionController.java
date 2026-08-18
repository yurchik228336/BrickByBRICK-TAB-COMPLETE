package dev.bbbt.client;

import java.util.List;

import dev.bbbt.config.BbbtConfig;
import dev.bbbt.core.BbbtRuntime;
import dev.bbbt.suggest.BuildContext;
import dev.bbbt.suggest.FocusBox;
import dev.bbbt.suggest.Suggestion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SuggestionController {

	private static final double REACH = 24.0;

	private final BbbtRuntime runtime;
	private final BuildContext context = new BuildContext();

	private volatile List<Suggestion> suggestions = List.of();
	private volatile List<Suggestion> preview = List.of();
	private volatile boolean pending;

	private volatile int selected;
	private int ticksUntilRefresh;
	private BlockPos lastAnchor;
	private volatile BlockPos lastLookCell;
	private volatile BlockPos requestedLookCell;
	private int lastJournalSize = -1;
	private volatile long lastComputeNanos;

	private volatile boolean answered;

	private BuildContext lastFrozen;
	private ClientWorldSnapshot lastWorld;
	private BlockPos focusA;
	private BlockPos focusB;

	public SuggestionController(BbbtRuntime runtime) {
		this.runtime = runtime;
	}

	public List<Suggestion> suggestions() {
		return suggestions;
	}

	public List<Suggestion> preview() {
		return preview;
	}

	public boolean isPending() {
		return pending;
	}

	public long lastComputeNanos() {
		return lastComputeNanos;
	}

	public Suggestion selected() {
		List<Suggestion> current = suggestions;
		if (current.isEmpty()) {
			return null;
		}
		return current.get(Math.min(selected, current.size() - 1));
	}

	public int selectedIndex() {
		return selected;
	}

	public FocusBox focusBox() {
		if (!runtime.config().focusRegionEnabled || focusA == null || focusB == null) {
			return null;
		}
		return FocusBox.of(focusA.getX(), focusA.getY(), focusA.getZ(),
				focusB.getX(), focusB.getY(), focusB.getZ());
	}

	public BlockPos focusA() {
		return focusA;
	}

	public BlockPos focusB() {
		return focusB;
	}

	public void setFocusA(BlockPos pos) {
		focusA = pos.immutable();
		invalidate();
	}

	public void setFocusB(BlockPos pos) {
		focusB = pos.immutable();
		invalidate();
	}

	public void clearFocus() {
		focusA = null;
		focusB = null;
		invalidate();
	}

	public void cycle() {
		List<Suggestion> current = suggestions;
		if (current.isEmpty()) {
			return;
		}
		selected = (selected + 1) % current.size();
		requestPreview();
	}

	public void clear() {
		suggestions = List.of();
		preview = List.of();
		selected = 0;
		lastAnchor = null;
		lastLookCell = null;
		requestedLookCell = null;
		lastJournalSize = -1;
		answered = false;
		lastFrozen = null;
		lastWorld = null;
	}

	public void tick(Minecraft client) {
		BbbtConfig config = runtime.config();
		if (!config.enabled || client.level == null || client.player == null) {
			if (answered || !suggestions.isEmpty()) {
				clear();
			}
			return;
		}

		BlockPos anchor = resolveAnchor(client);
		if (anchor == null) {
			if (answered || !suggestions.isEmpty()) {
				clear();
			}
			return;
		}

		BlockPos lookCell = LookTarget.placementCell(client);
		int journalSize = runtime.tracker().journal().size();
		if (!anchor.equals(lastAnchor) || journalSize != lastJournalSize) {
			lastAnchor = anchor;
			lastJournalSize = journalSize;
			lastLookCell = lookCell;
			requestedLookCell = null;
			suggestions = List.of();
			preview = List.of();
			selected = 0;
			ticksUntilRefresh = config.refreshDelayTicks;
			answered = false;
			return;
		}

		if (config.ghostLookOnly) {
			if (lookChanged(lookCell)) {
				lastLookCell = lookCell;
				suggestions = List.of();
				preview = List.of();
				selected = 0;
			}
			if (lookCell == null) {
				return;
			}
			if (!pending && answered && lastFrozen != null && lookCellChanged(lookCell)) {
				requestAt(lookCell);
			}
			if (answered || pending) {
				return;
			}
		} else if (answered || pending) {
			return;
		}
		if (ticksUntilRefresh > 0) {
			ticksUntilRefresh--;
			return;
		}
		request(anchor, lookCell);
	}

	private boolean lookChanged(BlockPos lookCell) {
		if (lookCell == null) {
			return lastLookCell != null;
		}
		return !lookCell.equals(lastLookCell);
	}

	private boolean lookCellChanged(BlockPos lookCell) {
		if (lookCell == null) {
			return false;
		}
		return requestedLookCell == null || !lookCell.equals(requestedLookCell);
	}

	public void invalidate() {
		suggestions = List.of();
		preview = List.of();
		selected = 0;
		lastAnchor = null;
		lastLookCell = null;
		requestedLookCell = null;
		lastJournalSize = -1;
		ticksUntilRefresh = 0;
		answered = false;
		lastFrozen = null;
		lastWorld = null;
	}

	private void request(BlockPos anchor, BlockPos lookCell) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}

		runtime.contextBuilder().build(context, runtime.tracker().store(),
				runtime.tracker().journal(), anchor, focusBox());
		if (context.isEmpty()) {
			suggestions = List.of();
			preview = List.of();
			answered = true;
			return;
		}

		BuildContext frozen = context.copy();
		var journal = runtime.tracker().journal().snapshot();
		ClientWorldSnapshot world = ClientWorldSnapshot.capture(client.level, anchor);
		BbbtConfig config = runtime.config();
		int depth = config.previewDepth;
		boolean lookOnly = config.ghostLookOnly;
		lastFrozen = frozen;
		lastWorld = world;
		lastLookCell = lookCell;
		requestedLookCell = lookCell;

		pending = true;
		final BlockPos expectedLook = lookCell;
		boolean submitted = runtime.submitPrediction(() -> {
			long started = System.nanoTime();
			try {
				List<Suggestion> found;
				if (lookOnly) {
					found = expectedLook == null
							? List.of()
							: runtime.engine().suggestAt(frozen, expectedLook.getX(),
									expectedLook.getY(), expectedLook.getZ(), journal, config,
									world, runtime.adapter());
				} else {
					found = runtime.engine().suggest(frozen, journal, config, world,
							runtime.adapter());
				}
				if (lookOnly && expectedLook != null && !expectedLook.equals(lastLookCell)) {
					return;
				}
				suggestions = found;
				selected = 0;
				preview = found.isEmpty() || depth <= 1 || lookOnly
						? List.of()
						: runtime.engine().extend(frozen, found.get(0), depth - 1, config, world,
								runtime.adapter());
				answered = true;
			} finally {
				lastComputeNanos = System.nanoTime() - started;
				pending = false;
			}
		});
		if (!submitted) {
			pending = false;
		}
	}

	private void requestAt(BlockPos lookCell) {
		BuildContext frozen = lastFrozen;
		ClientWorldSnapshot world = lastWorld;
		if (frozen == null || world == null) {
			return;
		}
		BbbtConfig config = runtime.config();
		var journal = runtime.tracker().journal().snapshot();
		BlockPos expected = lookCell.immutable();
		requestedLookCell = expected;
		pending = true;
		boolean submitted = runtime.submitPrediction(() -> {
			long started = System.nanoTime();
			try {
				List<Suggestion> found = runtime.engine().suggestAt(frozen, expected.getX(),
						expected.getY(), expected.getZ(), journal, config, world,
						runtime.adapter());
				if (!expected.equals(lastLookCell)) {
					return;
				}
				suggestions = found;
				selected = 0;
				preview = List.of();
			} finally {
				lastComputeNanos = System.nanoTime() - started;
				pending = false;
			}
		});
		if (!submitted) {
			pending = false;
		}
	}

	private void requestPreview() {
		BbbtConfig config = runtime.config();
		Suggestion current = selected();
		if (current == null || config.previewDepth <= 1 || config.ghostLookOnly) {
			preview = List.of();
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || lastAnchor == null) {
			return;
		}

		BuildContext frozen = context.copy();
		ClientWorldSnapshot world = ClientWorldSnapshot.capture(client.level, lastAnchor);
		runtime.submitPrediction(() -> preview = runtime.engine().extend(frozen, current,
				config.previewDepth - 1, config, world, runtime.adapter()));
	}

	private BlockPos resolveAnchor(Minecraft client) {
		var journal = runtime.tracker().journal();
		int size = journal.size();
		if (size > 0) {
			BlockPos last = new BlockPos(journal.x(size - 1), journal.y(size - 1),
					journal.z(size - 1));
			if (client.player.blockPosition().distSqr(last) <= REACH * REACH) {
				return last;
			}
		}

		HitResult hit = client.player.pick(REACH, 0f, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos();
		}
		return null;
	}
}
