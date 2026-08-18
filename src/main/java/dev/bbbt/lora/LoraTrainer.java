package dev.bbbt.lora;

import dev.bbbt.config.BbbtConfig;
import dev.bbbt.data.JournalSnapshot;
import dev.bbbt.model.ModelSpec;
import dev.bbbt.nn.SuggestionNetwork;
import dev.bbbt.palette.BlockPalette;

public final class LoraTrainer {

	private static final int MIN_INDEX = 2;

	public record Result(int samples, float positionLoss, float blockLoss, float orientationLoss) {
		public static final Result EMPTY = new Result(0, 0f, 0f, 0f);

		public boolean didWork() {
			return samples > 0;
		}
	}

	private final SuggestionNetwork network;
	private final SuggestionNetwork.Session session;
	private final BlockPalette palette;

	private final int[] blockTokens = new int[ModelSpec.MAX_CONTEXT];
	private final int[] orientations = new int[ModelSpec.MAX_CONTEXT];
	private final int[] dx = new int[ModelSpec.MAX_CONTEXT];
	private final int[] dy = new int[ModelSpec.MAX_CONTEXT];
	private final int[] dz = new int[ModelSpec.MAX_CONTEXT];
	private final int[] recency = new int[ModelSpec.MAX_CONTEXT];

	private final int[] candidateIndex = new int[ModelSpec.MAX_CONTEXT];
	private final int[] candidateScore = new int[ModelSpec.MAX_CONTEXT];

	private final float[] hiddenCopy;

	public LoraTrainer(SuggestionNetwork network, BlockPalette palette) {
		this.network = network;
		this.session = network.newSession();
		this.palette = palette;
		this.hiddenCopy = new float[network.dim()];
	}

	public Result train(JournalSnapshot journal, LoraAdapter adapter, BbbtConfig config,
			int maxSamples) {
		if (journal.size() <= MIN_INDEX) {
			return Result.EMPTY;
		}

		float learningRate = config.loraLearningRate;
		float weightDecay = config.loraWeightDecay;

		int samples = 0;
		double positionLoss = 0;
		double blockLoss = 0;
		double orientationLoss = 0;

		int from = Math.max(MIN_INDEX, journal.size() - maxSamples);
		for (int target = from; target < journal.size() && samples < maxSamples; target++) {
			int anchor = target - 1;
			if (!journal.sameSession(anchor, target)) {
				continue;
			}

			int anchorX = journal.x()[anchor];
			int anchorY = journal.y()[anchor];
			int anchorZ = journal.z()[anchor];

			int targetDx = journal.x()[target] - anchorX;
			int targetDy = journal.y()[target] - anchorY;
			int targetDz = journal.z()[target] - anchorZ;
			if (!ModelSpec.inGrid(targetDx, targetDy, targetDz)) {
				continue;
			}

			int count = buildContext(journal, target, anchorX, anchorY, anchorZ);
			if (count == 0) {
				continue;
			}

			int targetPosition = ModelSpec.gridIndex(targetDx, targetDy, targetDz);
			int targetBlock = palette.tokenOf(journal.names()[target]);
			int targetOrientation = Math.clamp(journal.orientations()[target], 0,
					network.orientCount() - 1);

			SuggestionNetwork.Memory memory = network.encode(session, blockTokens, orientations,
					dx, dy, dz, recency, count);

			network.decode(session, memory, ModelSpec.MODE_POSITION, 0, 0, 0);
			System.arraycopy(session.hidden, 0, hiddenCopy, 0, hiddenCopy.length);
			adapter.position().apply(hiddenCopy, session.posLogits, 1f);
			positionLoss += adapter.position().trainStep(hiddenCopy, session.posLogits,
					targetPosition, learningRate, weightDecay);

			network.decode(session, memory, ModelSpec.MODE_BLOCK, targetDx, targetDy, targetDz);
			System.arraycopy(session.hidden, 0, hiddenCopy, 0, hiddenCopy.length);
			adapter.block().apply(hiddenCopy, session.blockLogits, 1f);
			blockLoss += adapter.block().trainStep(hiddenCopy, session.blockLogits,
					targetBlock, learningRate, weightDecay);

			adapter.orientation().apply(hiddenCopy, session.orientLogits, 1f);
			orientationLoss += adapter.orientation().trainStep(hiddenCopy, session.orientLogits,
					targetOrientation, learningRate, weightDecay);

			samples++;
		}

		if (samples == 0) {
			return Result.EMPTY;
		}
		return new Result(samples,
				(float) (positionLoss / samples),
				(float) (blockLoss / samples),
				(float) (orientationLoss / samples));
	}

	private int buildContext(JournalSnapshot journal, int target,
			int anchorX, int anchorY, int anchorZ) {
		int sessionId = journal.sessions()[target];
		int filled = 0;

		for (int j = target - 1; j >= 0; j--) {
			if (journal.sessions()[j] != sessionId) {
				break;
			}
			int ddx = journal.x()[j] - anchorX;
			int ddy = journal.y()[j] - anchorY;
			int ddz = journal.z()[j] - anchorZ;
			int score = ddx * ddx + ddy * ddy + ddz * ddz;

			if (filled == ModelSpec.MAX_CONTEXT) {
				if (score >= candidateScore[filled - 1]) {
					continue;
				}
				filled--;
			}
			int at = filled++;
			while (at > 0 && candidateScore[at - 1] > score) {
				candidateScore[at] = candidateScore[at - 1];
				candidateIndex[at] = candidateIndex[at - 1];
				at--;
			}
			candidateScore[at] = score;
			candidateIndex[at] = j;
		}

		for (int i = 0; i < filled; i++) {
			int j = candidateIndex[i];
			blockTokens[i] = palette.tokenOf(journal.names()[j]);
			orientations[i] = Math.clamp(journal.orientations()[j], 0, network.orientCount() - 1);
			dx[i] = ModelSpec.offsetBucket(journal.x()[j] - anchorX);
			dy[i] = ModelSpec.offsetBucket(journal.y()[j] - anchorY);
			dz[i] = ModelSpec.offsetBucket(journal.z()[j] - anchorZ);
			recency[i] = ModelSpec.recencyBucket(target - 1 - j);
		}
		return filled;
	}
}
