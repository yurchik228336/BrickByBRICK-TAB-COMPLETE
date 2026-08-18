package dev.bbbt.nn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class WeightStore {
	private static final int MAGIC = ('B' << 24) | ('B' << 16) | ('B' << 8) | 'T';
	private static final int SUPPORTED_VERSION = 1;

	private final JsonObject arch;
	private final Map<String, float[]> tensors;
	private final Map<String, int[]> shapes;

	private WeightStore(JsonObject arch, Map<String, float[]> tensors, Map<String, int[]> shapes) {
		this.arch = arch;
		this.tensors = tensors;
		this.shapes = shapes;
	}

	public JsonObject arch() {
		return arch;
	}

	public int archInt(String key) {
		if (!arch.has(key)) {
			throw new IllegalStateException("Model header is missing '" + key + "'");
		}
		return arch.get(key).getAsInt();
	}

	public float archFloat(String key, float fallback) {
		return arch.has(key) ? arch.get(key).getAsFloat() : fallback;
	}

	public boolean has(String name) {
		return tensors.containsKey(name);
	}

	public float[] get(String name) {
		float[] t = tensors.get(name);
		if (t == null) {
			throw new IllegalStateException("Model is missing tensor '" + name + "'");
		}
		return t;
	}

	public float[] get(String name, int... expectedShape) {
		float[] t = get(name);
		int[] shape = shapes.get(name);
		if (shape.length != expectedShape.length) {
			throw new IllegalStateException("Tensor '" + name + "' has rank " + shape.length
					+ ", expected " + expectedShape.length);
		}
		for (int i = 0; i < shape.length; i++) {
			if (shape[i] != expectedShape[i]) {
				throw new IllegalStateException("Tensor '" + name + "' has shape "
						+ java.util.Arrays.toString(shape) + ", expected "
						+ java.util.Arrays.toString(expectedShape));
			}
		}
		return t;
	}

	public int[] shape(String name) {
		return shapes.get(name).clone();
	}

	public static WeightStore load(InputStream rawIn) throws IOException {
		try (DataInputStream in = new DataInputStream(rawIn)) {
			if (in.readInt() != MAGIC) {
				throw new IOException("Not a .bbbt weight file");
			}
			int version = in.readInt();
			if (version != SUPPORTED_VERSION) {
				throw new IOException("Unsupported weight container version " + version
						+ " (this build reads " + SUPPORTED_VERSION + ")");
			}

			int headerLen = in.readInt();
			if (headerLen <= 0 || headerLen > 8 << 20) {
				throw new IOException("Implausible header length " + headerLen);
			}
			byte[] headerBytes = in.readNBytes(headerLen);
			if (headerBytes.length != headerLen) {
				throw new IOException("Truncated header");
			}

			JsonObject header;
			try {
				header = JsonParser.parseString(new String(headerBytes, StandardCharsets.UTF_8))
						.getAsJsonObject();
			} catch (RuntimeException e) {
				throw new IOException("Malformed model header", e);
			}

			JsonObject arch = header.getAsJsonObject("arch");
			if (arch == null) {
				throw new IOException("Model header has no 'arch' block");
			}

			byte[] payload = in.readAllBytes();
			ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

			Map<String, float[]> tensors = new HashMap<>();
			Map<String, int[]> shapes = new HashMap<>();

			for (var element : header.getAsJsonArray("tensors")) {
				JsonObject spec = element.getAsJsonObject();
				String name = spec.get("name").getAsString();
				String dtype = spec.get("dtype").getAsString();
				long offset = spec.get("offset").getAsLong();

				var shapeArray = spec.getAsJsonArray("shape");
				int[] shape = new int[shapeArray.size()];
				int count = 1;
				for (int i = 0; i < shape.length; i++) {
					shape[i] = shapeArray.get(i).getAsInt();
					count *= shape[i];
				}

				tensors.put(name, readTensor(buf, name, dtype, offset, count));
				shapes.put(name, shape);
			}

			return new WeightStore(arch, tensors, shapes);
		}
	}

	private static float[] readTensor(ByteBuffer buf, String name, String dtype,
			long offset, int count) throws IOException {
		int elementSize = switch (dtype) {
			case "fp32" -> 4;
			case "fp16" -> 2;
			default -> throw new IOException("Unsupported dtype '" + dtype + "' for tensor " + name);
		};

		long end = offset + (long) count * elementSize;
		if (offset < 0 || end > buf.capacity()) {
			throw new IOException("Tensor '" + name + "' runs past the end of the payload");
		}

		float[] out = new float[count];
		buf.position((int) offset);
		if (elementSize == 4) {
			buf.asFloatBuffer().get(out, 0, count);
		} else {
			for (int i = 0; i < count; i++) {
				out[i] = Float.float16ToFloat(buf.getShort((int) offset + i * 2));
			}
		}
		return out;
	}
}
