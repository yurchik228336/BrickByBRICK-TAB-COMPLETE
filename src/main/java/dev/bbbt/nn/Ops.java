package dev.bbbt.nn;

public final class Ops {
	private Ops() {
	}

	private static final float GELU_C = 0.7978845608028654f;

	public static float dot(float[] a, int aOff, float[] b, int bOff, int n) {
		float sum = 0f;
		for (int i = 0; i < n; i++) {
			sum += a[aOff + i] * b[bOff + i];
		}
		return sum;
	}

	public static void linear(float[] x, int rows, int inDim,
			float[] w, float[] bias, int outDim, float[] y) {
		for (int r = 0; r < rows; r++) {
			int xOff = r * inDim;
			int yOff = r * outDim;
			for (int o = 0; o < outDim; o++) {
				float v = dot(x, xOff, w, o * inDim, inDim);
				y[yOff + o] = bias != null ? v + bias[o] : v;
			}
		}
	}

	public static void layerNorm(float[] x, int rows, int dim,
			float[] gamma, float[] beta, float eps) {
		for (int r = 0; r < rows; r++) {
			int off = r * dim;

			float mean = 0f;
			for (int i = 0; i < dim; i++) {
				mean += x[off + i];
			}
			mean /= dim;

			float var = 0f;
			for (int i = 0; i < dim; i++) {
				float d = x[off + i] - mean;
				var += d * d;
			}
			var /= dim;

			float inv = (float) (1.0 / Math.sqrt(var + eps));
			for (int i = 0; i < dim; i++) {
				x[off + i] = (x[off + i] - mean) * inv * gamma[i] + beta[i];
			}
		}
	}

	public static void geluInPlace(float[] x, int off, int n) {
		for (int i = off; i < off + n; i++) {
			float v = x[i];
			float inner = GELU_C * (v + 0.044715f * v * v * v);
			x[i] = 0.5f * v * (1f + (float) Math.tanh(inner));
		}
	}

	public static void softmaxInPlace(float[] x, int off, int n) {
		float max = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			if (x[off + i] > max) {
				max = x[off + i];
			}
		}
		float sum = 0f;
		for (int i = 0; i < n; i++) {
			float e = (float) Math.exp(x[off + i] - max);
			x[off + i] = e;
			sum += e;
		}
		float inv = sum > 0f ? 1f / sum : 0f;
		for (int i = 0; i < n; i++) {
			x[off + i] *= inv;
		}
	}

	public static void logSoftmax(float[] x, int off, int n, float[] out, int outOff) {
		float max = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			if (x[off + i] > max) {
				max = x[off + i];
			}
		}
		double sum = 0.0;
		for (int i = 0; i < n; i++) {
			sum += Math.exp(x[off + i] - max);
		}
		float logSum = (float) (max + Math.log(sum));
		for (int i = 0; i < n; i++) {
			out[outOff + i] = x[off + i] - logSum;
		}
	}

	public static void addInPlace(float[] dst, int dstOff, float[] src, int srcOff, int n) {
		for (int i = 0; i < n; i++) {
			dst[dstOff + i] += src[srcOff + i];
		}
	}

	public static void scaleInPlace(float[] x, int off, int n, float s) {
		for (int i = off; i < off + n; i++) {
			x[i] *= s;
		}
	}

	public static int argmax(float[] x, int off, int n) {
		int best = 0;
		float bestVal = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < n; i++) {
			if (x[off + i] > bestVal) {
				bestVal = x[off + i];
				best = i;
			}
		}
		return best;
	}

	public static void attentionHead(float[] q, int qOff, int qStride, int qLen,
			float[] k, int kOff, int kStride,
			float[] v, int vOff, int vStride, int kLen,
			boolean[] mask, int headDim,
			float[] scores, float[] out, int outOff, int outStride) {
		float scale = (float) (1.0 / Math.sqrt(headDim));

		for (int i = 0; i < qLen; i++) {
			int qRow = qOff + i * qStride;

			for (int j = 0; j < kLen; j++) {
				if (mask != null && !mask[j]) {
					scores[j] = Float.NEGATIVE_INFINITY;
				} else {
					scores[j] = dot(q, qRow, k, kOff + j * kStride, headDim) * scale;
				}
			}
			softmaxInPlace(scores, 0, kLen);

			int outRow = outOff + i * outStride;
			for (int d = 0; d < headDim; d++) {
				out[outRow + d] = 0f;
			}
			for (int j = 0; j < kLen; j++) {
				float w = scores[j];
				if (w == 0f) {
					continue;
				}
				int vRow = vOff + j * vStride;
				for (int d = 0; d < headDim; d++) {
					out[outRow + d] += w * v[vRow + d];
				}
			}
		}
	}
}
