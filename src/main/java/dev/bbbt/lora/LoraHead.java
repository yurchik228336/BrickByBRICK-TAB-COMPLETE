package dev.bbbt.lora;

import dev.bbbt.nn.Ops;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

public final class LoraHead {
	private final int dim;
	private final int out;
	private final int rank;
	private final float scale;

	private final float[] a;
	private final float[] b;

	private final float[] mA;
	private final float[] vA;
	private final float[] mB;
	private final float[] vB;
	private int step;

	private final float[] u;
	private final float[] grad;
	private final float[] back;

	public LoraHead(int dim, int out, int rank, float alpha, long seed) {
		this.dim = dim;
		this.out = out;
		this.rank = rank;
		this.scale = alpha / rank;

		this.a = new float[rank * dim];
		this.b = new float[out * rank];
		this.mA = new float[a.length];
		this.vA = new float[a.length];
		this.mB = new float[b.length];
		this.vB = new float[b.length];

		this.u = new float[rank];
		this.grad = new float[out];
		this.back = new float[rank];

		Random random = new Random(seed);
		float sigma = (float) (1.0 / Math.sqrt(dim));
		for (int i = 0; i < a.length; i++) {
			a[i] = (float) random.nextGaussian() * sigma;
		}
	}

	public int rank() {
		return rank;
	}

	public int outputSize() {
		return out;
	}

	public int trainedSteps() {
		return step;
	}

	public boolean isNeutral() {
		return step == 0;
	}

	public void apply(float[] hidden, float[] logits, float strength) {
		if (strength == 0f) {
			return;
		}
		project(hidden);
		float s = scale * strength;
		for (int o = 0; o < out; o++) {
			logits[o] += s * Ops.dot(b, o * rank, u, 0, rank);
		}
	}

	private void project(float[] hidden) {
		for (int j = 0; j < rank; j++) {
			u[j] = Ops.dot(a, j * dim, hidden, 0, dim);
		}
	}

	public float trainStep(float[] hidden, float[] logits, int target,
			float learningRate, float weightDecay) {
		project(hidden);

		Ops.softmaxInPlace(logits, 0, out);
		float loss = (float) -Math.log(Math.max(logits[target], 1e-9f));

		System.arraycopy(logits, 0, grad, 0, out);
		grad[target] -= 1f;

		for (int j = 0; j < rank; j++) {
			float sum = 0f;
			for (int o = 0; o < out; o++) {
				sum += grad[o] * b[o * rank + j];
			}
			back[j] = sum;
		}

		step++;
		float beta1 = 0.9f;
		float beta2 = 0.999f;
		float eps = 1e-8f;
		float biasCorr1 = 1f - (float) Math.pow(beta1, step);
		float biasCorr2 = 1f - (float) Math.pow(beta2, step);

		for (int o = 0; o < out; o++) {
			float go = scale * grad[o];
			if (go == 0f) {
				continue;
			}
			int row = o * rank;
			for (int j = 0; j < rank; j++) {
				int idx = row + j;
				float g = go * u[j] + weightDecay * b[idx];
				mB[idx] = beta1 * mB[idx] + (1f - beta1) * g;
				vB[idx] = beta2 * vB[idx] + (1f - beta2) * g * g;
				float mHat = mB[idx] / biasCorr1;
				float vHat = vB[idx] / biasCorr2;
				b[idx] -= learningRate * mHat / ((float) Math.sqrt(vHat) + eps);
			}
		}

		for (int j = 0; j < rank; j++) {
			float bj = scale * back[j];
			int row = j * dim;
			for (int d = 0; d < dim; d++) {
				int idx = row + d;
				float g = bj * hidden[d] + weightDecay * a[idx];
				mA[idx] = beta1 * mA[idx] + (1f - beta1) * g;
				vA[idx] = beta2 * vA[idx] + (1f - beta2) * g * g;
				float mHat = mA[idx] / biasCorr1;
				float vHat = vA[idx] / biasCorr2;
				a[idx] -= learningRate * mHat / ((float) Math.sqrt(vHat) + eps);
			}
		}

		return loss;
	}

	public void reset() {
		java.util.Arrays.fill(b, 0f);
		java.util.Arrays.fill(mA, 0f);
		java.util.Arrays.fill(vA, 0f);
		java.util.Arrays.fill(mB, 0f);
		java.util.Arrays.fill(vB, 0f);
		step = 0;
	}

	void write(DataOutputStream out) throws IOException {
		out.writeInt(dim);
		out.writeInt(this.out);
		out.writeInt(rank);
		out.writeFloat(scale);
		out.writeInt(step);
		for (float value : a) {
			out.writeFloat(value);
		}
		for (float value : b) {
			out.writeFloat(value);
		}
	}

	static LoraHead read(DataInputStream in, int expectedDim, int expectedOut) throws IOException {
		int dim = in.readInt();
		int out = in.readInt();
		int rank = in.readInt();
		float scale = in.readFloat();
		int step = in.readInt();

		int aLen = rank * dim;
		int bLen = out * rank;
		if (dim != expectedDim || out != expectedOut || rank <= 0 || rank > 256) {
			in.skipNBytes(4L * (aLen + bLen));
			return null;
		}

		LoraHead head = new LoraHead(dim, out, rank, scale * rank, 0L);
		for (int i = 0; i < aLen; i++) {
			head.a[i] = in.readFloat();
		}
		for (int i = 0; i < bLen; i++) {
			head.b[i] = in.readFloat();
		}
		head.step = step;
		return head;
	}
}
