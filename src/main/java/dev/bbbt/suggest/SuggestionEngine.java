package dev.bbbt.suggest;

import dev.bbbt.config.BbbtConfig;
import dev.bbbt.data.JournalSnapshot;
import dev.bbbt.lora.LoraAdapter;
import dev.bbbt.model.ModelSpec;
import dev.bbbt.nn.Ops;
import dev.bbbt.nn.SuggestionNetwork;
import dev.bbbt.palette.BlockPalette;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SuggestionEngine {

	private static final int CANDIDATE_POOL = 48;

	private final BlockPalette palette;
	private final HeuristicSuggester heuristic = new HeuristicSuggester();

	private SuggestionNetwork network;
	private SuggestionNetwork.Session session;

	private float[] posProbabilities = new float[ModelSpec.GRID_VOLUME];
	private float[] positionHidden = new float[0];
	private final int[] poolIndex = new int[CANDIDATE_POOL];
	private final float[] poolProbability = new float[CANDIDATE_POOL];
	private final Set<Long> occupied = new HashSet<>();

	public SuggestionEngine(BlockPalette palette) {
		this.palette = palette;
	}

	public void setNetwork(SuggestionNetwork network) {
		this.network = network;
		this.session = network != null ? network.newSession() : null;
		this.positionHidden = network != null ? new float[network.dim()] : new float[0];
		this.cachedMemory = null;
		this.cachedCtx = null;
	}

	public boolean hasModel() {
		return network != null;
	}

	private SuggestionNetwork.Memory cachedMemory;
	private BuildContext cachedCtx;

	public List<Suggestion> suggest(BuildContext ctx, JournalSnapshot journal,
			BbbtConfig config, PlacementValidator validator, LoraAdapter adapter) {
		int topK = config.suggestionCount;
		if (ctx.isEmpty()) {
			return List.of();
		}
		if (network == null) {
			return heuristic.suggest(ctx, journal, topK, validator);
		}

		List<Suggestion> out = predict(ctx, config, validator, adapter, topK);
		if (out.isEmpty()) {

			return heuristic.suggest(ctx, journal, topK, validator);
		}
		return out;
	}

	public List<Suggestion> suggestAt(BuildContext ctx, int x, int y, int z,
			JournalSnapshot journal, BbbtConfig config, PlacementValidator validator,
			LoraAdapter adapter) {
		int topK = config.suggestionCount;
		if (ctx.isEmpty()) {
			return List.of();
		}
		int dx = x - ctx.anchorX;
		int dy = y - ctx.anchorY;
		int dz = z - ctx.anchorZ;
		if (!ModelSpec.inGrid(dx, dy, dz) || (dx | dy | dz) == 0) {
			return List.of();
		}
		if (!validator.isFree(x, y, z) || !validator.hasSupport(x, y, z)) {
			return List.of();
		}
		indexOccupied(ctx);
		if (occupied.contains(pack(dx, dy, dz))) {
			return List.of();
		}

		if (network == null) {
			return heuristic.suggestAt(ctx, x, y, z, topK, validator);
		}

		float strength = adapter != null && config.loraEnabled ? config.loraStrength : 0f;
		SuggestionNetwork.Memory memory = memoryFor(ctx);
		network.decode(session, memory, ModelSpec.MODE_BLOCK, dx, dy, dz);
		if (strength > 0f) {
			adapter.applyBlock(session.hidden, session.blockLogits, session.orientLogits, strength);
		}
		Ops.softmaxInPlace(session.blockLogits, 0, session.blockLogits.length);

		int orientation = Ops.argmax(session.orientLogits, 0, session.orientLogits.length);
		List<Suggestion> out = new ArrayList<>(topK);
		boolean[] taken = new boolean[session.blockLogits.length];
		for (int n = 0; n < topK; n++) {
			int token = bestUntakenBlock(session.blockLogits, taken);
			if (token < 0) {
				break;
			}
			taken[token] = true;
			float confidence = session.blockLogits[token];
			if (confidence < config.confidenceThreshold) {
				break;
			}
			String blockName = palette.nameOf(token);
			if (blockName.isEmpty()) {
				continue;
			}
			out.add(new Suggestion(x, y, z, blockName, orientation, confidence,
					Suggestion.Source.MODEL));
		}
		if (out.isEmpty()) {
			return heuristic.suggestAt(ctx, x, y, z, topK, validator);
		}
		return out;
	}

	private SuggestionNetwork.Memory memoryFor(BuildContext ctx) {
		if (cachedMemory != null && cachedCtx == ctx) {
			return cachedMemory;
		}
		cachedCtx = ctx;
		cachedMemory = network.encode(session, ctx.blockTokens, ctx.orientations, ctx.dx, ctx.dy,
				ctx.dz, ctx.recency, ctx.count);
		return cachedMemory;
	}

	private static int bestUntakenBlock(float[] probabilities, boolean[] taken) {
		int best = -1;
		float bestValue = -1f;
		for (int t = BlockPalette.FIRST_BLOCK_TOKEN; t < probabilities.length; t++) {
			if (!taken[t] && probabilities[t] > bestValue) {
				bestValue = probabilities[t];
				best = t;
			}
		}
		return best;
	}

	public List<Suggestion> extend(BuildContext ctx, Suggestion accepted, int steps,
			BbbtConfig config, PlacementValidator validator, LoraAdapter adapter) {
		List<Suggestion> chain = new ArrayList<>();
		if (network == null || steps <= 0) {
			return chain;
		}

		BuildContext working = ctx.copy();
		Suggestion previous = accepted;
		for (int i = 0; i < steps; i++) {
			appendAndRebase(working, previous);
			List<Suggestion> next = predict(working, config, validator, adapter, 1);
			if (next.isEmpty()) {
				break;
			}
			previous = next.get(0);
			chain.add(previous);
		}
		return chain;
	}

	private List<Suggestion> predict(BuildContext ctx, BbbtConfig config,
			PlacementValidator validator, LoraAdapter adapter, int topK) {
		float strength = adapter != null && config.loraEnabled ? config.loraStrength : 0f;

		SuggestionNetwork.Memory memory = memoryFor(ctx);

		network.decode(session, memory, ModelSpec.MODE_POSITION, 0, 0, 0);
		System.arraycopy(session.hidden, 0, positionHidden, 0, positionHidden.length);
		System.arraycopy(session.posLogits, 0, posProbabilities, 0, posProbabilities.length);
		if (strength > 0f) {
			adapter.applyPosition(positionHidden, posProbabilities, strength);
		}
		Ops.softmaxInPlace(posProbabilities, 0, posProbabilities.length);

		indexOccupied(ctx);
		int pooled = selectCandidates(ctx, validator);

		List<Suggestion> out = new ArrayList<>(topK);
		for (int i = 0; i < pooled; i++) {
			int gridIndex = poolIndex[i];
			int dx = ModelSpec.gridDx(gridIndex);
			int dy = ModelSpec.gridDy(gridIndex);
			int dz = ModelSpec.gridDz(gridIndex);

			network.decode(session, memory, ModelSpec.MODE_BLOCK, dx, dy, dz);
			if (strength > 0f) {
				adapter.applyBlock(session.hidden, session.blockLogits, session.orientLogits,
						strength);
			}
			Ops.softmaxInPlace(session.blockLogits, 0, session.blockLogits.length);

			int blockToken = bestRealBlock(session.blockLogits);
			if (blockToken < 0) {
				continue;
			}
			String blockName = palette.nameOf(blockToken);
			if (blockName.isEmpty()) {
				continue;
			}

			int orientation = Ops.argmax(session.orientLogits, 0, session.orientLogits.length);
			float confidence = poolProbability[i] * session.blockLogits[blockToken];
			if (confidence < config.confidenceThreshold) {
				continue;
			}

			out.add(new Suggestion(ctx.anchorX + dx, ctx.anchorY + dy, ctx.anchorZ + dz,
					blockName, orientation, confidence, Suggestion.Source.MODEL));
			if (out.size() >= topK) {
				break;
			}
		}
		return out;
	}

	private static int bestRealBlock(float[] probabilities) {
		int best = -1;
		float bestValue = -1f;
		for (int t = BlockPalette.FIRST_BLOCK_TOKEN; t < probabilities.length; t++) {
			if (probabilities[t] > bestValue) {
				bestValue = probabilities[t];
				best = t;
			}
		}
		return best;
	}

	private int selectCandidates(BuildContext ctx, PlacementValidator validator) {
		int filled = 0;
		for (int index = 0; index < posProbabilities.length; index++) {
			float probability = posProbabilities[index];
			if (filled == CANDIDATE_POOL && probability <= poolProbability[filled - 1]) {
				continue;
			}

			int dx = ModelSpec.gridDx(index);
			int dy = ModelSpec.gridDy(index);
			int dz = ModelSpec.gridDz(index);
			if ((dx | dy | dz) == 0 || occupied.contains(pack(dx, dy, dz))) {
				continue;
			}

			int x = ctx.anchorX + dx;
			int y = ctx.anchorY + dy;
			int z = ctx.anchorZ + dz;
			if (!validator.isFree(x, y, z) || !validator.hasSupport(x, y, z)) {
				continue;
			}

			int at = filled < CANDIDATE_POOL ? filled++ : CANDIDATE_POOL - 1;
			while (at > 0 && poolProbability[at - 1] < probability) {
				poolIndex[at] = poolIndex[at - 1];
				poolProbability[at] = poolProbability[at - 1];
				at--;
			}
			poolIndex[at] = index;
			poolProbability[at] = probability;
		}
		return filled;
	}

	private void indexOccupied(BuildContext ctx) {
		occupied.clear();
		for (int i = 0; i < ctx.count; i++) {
			occupied.add(pack(ctx.rawDx[i], ctx.rawDy[i], ctx.rawDz[i]));
		}
	}

	private void appendAndRebase(BuildContext ctx, Suggestion placed) {
		if (ctx.count < ModelSpec.MAX_CONTEXT) {
			int i = ctx.count++;
			ctx.blockTokens[i] = palette.tokenOf(placed.blockName());
			ctx.orientations[i] = placed.orientation();
			ctx.names[i] = placed.blockName();
			ctx.rawDx[i] = placed.x() - ctx.anchorX;
			ctx.rawDy[i] = placed.y() - ctx.anchorY;
			ctx.rawDz[i] = placed.z() - ctx.anchorZ;
			ctx.rawAge[i] = -1;
		}

		int shiftX = placed.x() - ctx.anchorX;
		int shiftY = placed.y() - ctx.anchorY;
		int shiftZ = placed.z() - ctx.anchorZ;
		ctx.anchorX = placed.x();
		ctx.anchorY = placed.y();
		ctx.anchorZ = placed.z();

		for (int i = 0; i < ctx.count; i++) {
			ctx.rawDx[i] -= shiftX;
			ctx.rawDy[i] -= shiftY;
			ctx.rawDz[i] -= shiftZ;
			ctx.dx[i] = ModelSpec.offsetBucket(ctx.rawDx[i]);
			ctx.dy[i] = ModelSpec.offsetBucket(ctx.rawDy[i]);
			ctx.dz[i] = ModelSpec.offsetBucket(ctx.rawDz[i]);

			int age = ctx.rawAge[i];
			ctx.rawAge[i] = age < 0 ? 0 : age + 1;
			ctx.recency[i] = ModelSpec.recencyBucket(ctx.rawAge[i]);
		}
	}

	private static long pack(int dx, int dy, int dz) {
		return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
	}
}
