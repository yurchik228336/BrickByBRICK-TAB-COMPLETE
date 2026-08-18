package dev.bbbt.nn;

import dev.bbbt.model.ModelSpec;

public final class SuggestionNetwork {
	private final int dim;
	private final int heads;
	private final int headDim;
	private final int ffDim;
	private final int encLayers;
	private final int decLayers;
	private final int vocab;
	private final int orientCount;
	private final int gridVolume;
	private final float lnEps;

	private final float[] blockEmb;
	private final float[] orientEmb;
	private final float[] dxEmb;
	private final float[] dyEmb;
	private final float[] dzEmb;
	private final float[] recEmb;
	private final float[] queryEmb;
	private final float[] modeEmb;

	private final float[] ctxLnG;
	private final float[] ctxLnB;
	private final float[] qLnG;
	private final float[] qLnB;

	private final EncLayer[] enc;
	private final DecLayer[] dec;

	private final float[] lnFG;
	private final float[] lnFB;

	private final float[] posHeadW;
	private final float[] posHeadB;
	private final float[] blockHeadW;
	private final float[] blockHeadB;
	private final float[] orientHeadW;
	private final float[] orientHeadB;
	private final float[] stopHeadW;
	private final float[] stopHeadB;

	private record EncLayer(
			float[] ln1G, float[] ln1B,
			float[] wq, float[] bq, float[] wk, float[] bk,
			float[] wv, float[] bv, float[] wo, float[] bo,
			float[] ln2G, float[] ln2B,
			float[] ff1W, float[] ff1B, float[] ff2W, float[] ff2B) {
	}

	private record DecLayer(
			float[] ln1G, float[] ln1B,
			float[] wq, float[] bq, float[] wk, float[] bk,
			float[] wv, float[] bv, float[] wo, float[] bo,
			float[] ln2G, float[] ln2B,
			float[] ff1W, float[] ff1B, float[] ff2W, float[] ff2B) {
	}

	public SuggestionNetwork(WeightStore w) {
		this.dim = w.archInt("dim");
		this.heads = w.archInt("heads");
		this.ffDim = w.archInt("ff_dim");
		this.encLayers = w.archInt("enc_layers");
		this.decLayers = w.archInt("dec_layers");
		this.vocab = w.archInt("vocab");
		this.orientCount = w.archInt("orient_count");
		this.gridVolume = w.archInt("grid_volume");
		this.lnEps = w.archFloat("ln_eps", 1e-5f);

		if (dim % heads != 0) {
			throw new IllegalStateException("dim " + dim + " is not divisible by heads " + heads);
		}
		this.headDim = dim / heads;

		if (orientCount != dev.bbbt.palette.Orientation.COUNT) {
			throw new IllegalStateException("Model orientation vocabulary (" + orientCount
					+ ") does not match this build (" + dev.bbbt.palette.Orientation.COUNT + ")");
		}
		if (gridVolume != ModelSpec.GRID_VOLUME) {
			throw new IllegalStateException("Model grid volume (" + gridVolume
					+ ") does not match this build (" + ModelSpec.GRID_VOLUME + ")");
		}

		this.blockEmb = w.get("block_emb", vocab, dim);
		this.orientEmb = w.get("orient_emb", orientCount, dim);
		this.dxEmb = w.get("dx_emb", ModelSpec.OFFSET_BUCKETS, dim);
		this.dyEmb = w.get("dy_emb", ModelSpec.OFFSET_BUCKETS, dim);
		this.dzEmb = w.get("dz_emb", ModelSpec.OFFSET_BUCKETS, dim);
		this.recEmb = w.get("rec_emb", ModelSpec.RECENCY_BUCKETS, dim);
		this.queryEmb = w.get("query_emb", dim);
		this.modeEmb = w.get("mode_emb", ModelSpec.MODE_COUNT, dim);

		this.ctxLnG = w.get("ctx_ln.g", dim);
		this.ctxLnB = w.get("ctx_ln.b", dim);
		this.qLnG = w.get("q_ln.g", dim);
		this.qLnB = w.get("q_ln.b", dim);

		this.enc = new EncLayer[encLayers];
		for (int i = 0; i < encLayers; i++) {
			String p = "enc." + i + ".";
			enc[i] = new EncLayer(
					w.get(p + "ln1.g", dim), w.get(p + "ln1.b", dim),
					w.get(p + "attn.wq", dim, dim), w.get(p + "attn.bq", dim),
					w.get(p + "attn.wk", dim, dim), w.get(p + "attn.bk", dim),
					w.get(p + "attn.wv", dim, dim), w.get(p + "attn.bv", dim),
					w.get(p + "attn.wo", dim, dim), w.get(p + "attn.bo", dim),
					w.get(p + "ln2.g", dim), w.get(p + "ln2.b", dim),
					w.get(p + "ff1.w", ffDim, dim), w.get(p + "ff1.b", ffDim),
					w.get(p + "ff2.w", dim, ffDim), w.get(p + "ff2.b", dim));
		}

		this.dec = new DecLayer[decLayers];
		for (int i = 0; i < decLayers; i++) {
			String p = "dec." + i + ".";
			dec[i] = new DecLayer(
					w.get(p + "ln1.g", dim), w.get(p + "ln1.b", dim),
					w.get(p + "xattn.wq", dim, dim), w.get(p + "xattn.bq", dim),
					w.get(p + "xattn.wk", dim, dim), w.get(p + "xattn.bk", dim),
					w.get(p + "xattn.wv", dim, dim), w.get(p + "xattn.bv", dim),
					w.get(p + "xattn.wo", dim, dim), w.get(p + "xattn.bo", dim),
					w.get(p + "ln2.g", dim), w.get(p + "ln2.b", dim),
					w.get(p + "ff1.w", ffDim, dim), w.get(p + "ff1.b", ffDim),
					w.get(p + "ff2.w", dim, ffDim), w.get(p + "ff2.b", dim));
		}

		this.lnFG = w.get("ln_f.g", dim);
		this.lnFB = w.get("ln_f.b", dim);

		this.posHeadW = w.get("pos_head.w", gridVolume, dim);
		this.posHeadB = w.get("pos_head.b", gridVolume);
		this.blockHeadW = w.get("block_head.w", vocab, dim);
		this.blockHeadB = w.get("block_head.b", vocab);
		this.orientHeadW = w.get("orient_head.w", orientCount, dim);
		this.orientHeadB = w.get("orient_head.b", orientCount);
		this.stopHeadW = w.get("stop_head.w", 1, dim);
		this.stopHeadB = w.get("stop_head.b", 1);
	}

	public int dim() {
		return dim;
	}

	public int vocabSize() {
		return vocab;
	}

	public int orientCount() {
		return orientCount;
	}

	public int gridVolume() {
		return gridVolume;
	}

	public static final class Memory {
		final int count;
		final float[][] keys;
		final float[][] values;

		Memory(int count, float[][] keys, float[][] values) {
			this.count = count;
			this.keys = keys;
			this.values = values;
		}

		public int tokenCount() {
			return count;
		}
	}

	public final class Session {
		private final int maxTokens = ModelSpec.MAX_CONTEXT;
		private final float[] tokens = new float[maxTokens * dim];
		private final float[] normed = new float[maxTokens * dim];
		private final float[] q = new float[maxTokens * dim];
		private final float[] k = new float[maxTokens * dim];
		private final float[] v = new float[maxTokens * dim];
		private final float[] attnOut = new float[maxTokens * dim];
		private final float[] projOut = new float[maxTokens * dim];
		private final float[] ff = new float[maxTokens * ffDim];
		private final float[] scores = new float[maxTokens];

		private final float[] qTok = new float[dim];
		private final float[] qNorm = new float[dim];
		private final float[] qProj = new float[dim];
		private final float[] qAttn = new float[dim];
		private final float[] qFf = new float[ffDim];

		public final float[] hidden = new float[dim];

		public final float[] posLogits = new float[gridVolume];
		public final float[] blockLogits = new float[vocab];
		public final float[] orientLogits = new float[orientCount];
		public float stopLogit;

		private final float[] stopScratch = new float[1];
	}

	public Session newSession() {
		return new Session();
	}

	public Memory encode(Session s, int[] blockTokens, int[] orient,
			int[] dx, int[] dy, int[] dz, int[] recency, int count) {
		if (count > ModelSpec.MAX_CONTEXT) {
			throw new IllegalArgumentException("Context of " + count + " exceeds "
					+ ModelSpec.MAX_CONTEXT);
		}

		for (int i = 0; i < count; i++) {
			int off = i * dim;
			int bt = Math.clamp(blockTokens[i], 0, vocab - 1);
			int ot = Math.clamp(orient[i], 0, orientCount - 1);
			int rb = Math.clamp(recency[i], 0, ModelSpec.RECENCY_BUCKETS - 1);

			for (int d = 0; d < dim; d++) {
				s.tokens[off + d] = blockEmb[bt * dim + d]
						+ orientEmb[ot * dim + d]
						+ dxEmb[dx[i] * dim + d]
						+ dyEmb[dy[i] * dim + d]
						+ dzEmb[dz[i] * dim + d]
						+ recEmb[rb * dim + d];
			}
		}
		Ops.layerNorm(s.tokens, count, dim, ctxLnG, ctxLnB, lnEps);

		for (EncLayer layer : enc) {
			encoderBlock(s, layer, count);
		}

		float[][] keys = new float[decLayers][];
		float[][] values = new float[decLayers][];
		for (int i = 0; i < decLayers; i++) {
			DecLayer layer = dec[i];
			float[] kk = new float[count * dim];
			float[] vv = new float[count * dim];
			Ops.linear(s.tokens, count, dim, layer.wk(), layer.bk(), dim, kk);
			Ops.linear(s.tokens, count, dim, layer.wv(), layer.bv(), dim, vv);
			keys[i] = kk;
			values[i] = vv;
		}
		return new Memory(count, keys, values);
	}

	private void encoderBlock(Session s, EncLayer layer, int n) {
		System.arraycopy(s.tokens, 0, s.normed, 0, n * dim);
		Ops.layerNorm(s.normed, n, dim, layer.ln1G(), layer.ln1B(), lnEps);

		Ops.linear(s.normed, n, dim, layer.wq(), layer.bq(), dim, s.q);
		Ops.linear(s.normed, n, dim, layer.wk(), layer.bk(), dim, s.k);
		Ops.linear(s.normed, n, dim, layer.wv(), layer.bv(), dim, s.v);

		for (int h = 0; h < heads; h++) {
			int base = h * headDim;
			Ops.attentionHead(s.q, base, dim, n,
					s.k, base, dim,
					s.v, base, dim, n,
					null, headDim,
					s.scores, s.attnOut, base, dim);
		}

		Ops.linear(s.attnOut, n, dim, layer.wo(), layer.bo(), dim, s.projOut);
		Ops.addInPlace(s.tokens, 0, s.projOut, 0, n * dim);

		System.arraycopy(s.tokens, 0, s.normed, 0, n * dim);
		Ops.layerNorm(s.normed, n, dim, layer.ln2G(), layer.ln2B(), lnEps);

		Ops.linear(s.normed, n, dim, layer.ff1W(), layer.ff1B(), ffDim, s.ff);
		Ops.geluInPlace(s.ff, 0, n * ffDim);
		Ops.linear(s.ff, n, ffDim, layer.ff2W(), layer.ff2B(), dim, s.projOut);
		Ops.addInPlace(s.tokens, 0, s.projOut, 0, n * dim);
	}

	public void decode(Session s, Memory memory, int mode, int qdx, int qdy, int qdz) {
		int m = Math.clamp(mode, 0, ModelSpec.MODE_COUNT - 1);

		for (int d = 0; d < dim; d++) {
			s.qTok[d] = queryEmb[d] + modeEmb[m * dim + d];
		}
		if (m == ModelSpec.MODE_BLOCK) {
			int bx = ModelSpec.offsetBucket(qdx);
			int by = ModelSpec.offsetBucket(qdy);
			int bz = ModelSpec.offsetBucket(qdz);
			for (int d = 0; d < dim; d++) {
				s.qTok[d] += dxEmb[bx * dim + d] + dyEmb[by * dim + d] + dzEmb[bz * dim + d];
			}
		}
		Ops.layerNorm(s.qTok, 1, dim, qLnG, qLnB, lnEps);

		for (int i = 0; i < decLayers; i++) {
			decoderBlock(s, dec[i], memory, i);
		}

		System.arraycopy(s.qTok, 0, s.hidden, 0, dim);
		Ops.layerNorm(s.hidden, 1, dim, lnFG, lnFB, lnEps);

		if (m == ModelSpec.MODE_POSITION) {
			Ops.linear(s.hidden, 1, dim, posHeadW, posHeadB, gridVolume, s.posLogits);
			Ops.linear(s.hidden, 1, dim, stopHeadW, stopHeadB, 1, s.stopScratch);
			s.stopLogit = s.stopScratch[0];
		} else {
			Ops.linear(s.hidden, 1, dim, blockHeadW, blockHeadB, vocab, s.blockLogits);
			Ops.linear(s.hidden, 1, dim, orientHeadW, orientHeadB, orientCount, s.orientLogits);
		}
	}

	private void decoderBlock(Session s, DecLayer layer, Memory memory, int layerIndex) {
		System.arraycopy(s.qTok, 0, s.qNorm, 0, dim);
		Ops.layerNorm(s.qNorm, 1, dim, layer.ln1G(), layer.ln1B(), lnEps);

		Ops.linear(s.qNorm, 1, dim, layer.wq(), layer.bq(), dim, s.qProj);

		float[] k = memory.keys[layerIndex];
		float[] v = memory.values[layerIndex];
		for (int h = 0; h < heads; h++) {
			int base = h * headDim;
			Ops.attentionHead(s.qProj, base, dim, 1,
					k, base, dim,
					v, base, dim, memory.count,
					null, headDim,
					s.scores, s.qAttn, base, dim);
		}

		Ops.linear(s.qAttn, 1, dim, layer.wo(), layer.bo(), dim, s.qProj);
		Ops.addInPlace(s.qTok, 0, s.qProj, 0, dim);

		System.arraycopy(s.qTok, 0, s.qNorm, 0, dim);
		Ops.layerNorm(s.qNorm, 1, dim, layer.ln2G(), layer.ln2B(), lnEps);

		Ops.linear(s.qNorm, 1, dim, layer.ff1W(), layer.ff1B(), ffDim, s.qFf);
		Ops.geluInPlace(s.qFf, 0, ffDim);
		Ops.linear(s.qFf, 1, ffDim, layer.ff2W(), layer.ff2B(), dim, s.qProj);
		Ops.addInPlace(s.qTok, 0, s.qProj, 0, dim);
	}
}
