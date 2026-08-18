package dev.bbbt.lora;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LoraAdapter {
	private static final int MAGIC = ('B' << 24) | ('B' << 16) | ('L' << 8) | 'O';
	private static final int VERSION = 1;

	private final LoraHead position;
	private final LoraHead block;
	private final LoraHead orientation;

	private LoraAdapter(LoraHead position, LoraHead block, LoraHead orientation) {
		this.position = position;
		this.block = block;
		this.orientation = orientation;
	}

	public static LoraAdapter create(int dim, int gridVolume, int vocab, int orientCount,
			int rank, float alpha, long seed) {
		return new LoraAdapter(
				new LoraHead(dim, gridVolume, rank, alpha, seed),
				new LoraHead(dim, vocab, rank, alpha, seed + 1),
				new LoraHead(dim, orientCount, rank, alpha, seed + 2));
	}

	public LoraHead position() {
		return position;
	}

	public LoraHead block() {
		return block;
	}

	public LoraHead orientation() {
		return orientation;
	}

	public int trainedSteps() {
		return position.trainedSteps() + block.trainedSteps() + orientation.trainedSteps();
	}

	public boolean isNeutral() {
		return position.isNeutral() && block.isNeutral() && orientation.isNeutral();
	}

	public void applyPosition(float[] hidden, float[] posLogits, float strength) {
		position.apply(hidden, posLogits, strength);
	}

	public void applyBlock(float[] hidden, float[] blockLogits, float[] orientLogits,
			float strength) {
		block.apply(hidden, blockLogits, strength);
		orientation.apply(hidden, orientLogits, strength);
	}

	public void reset() {
		position.reset();
		block.reset();
		orientation.reset();
	}

	public void save(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(Files.newOutputStream(tmp)))) {
			out.writeInt(MAGIC);
			out.writeInt(VERSION);
			position.write(out);
			block.write(out);
			orientation.write(out);
		}
		Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
	}

	public static LoraAdapter loadOrCreate(Path path, int dim, int gridVolume, int vocab,
			int orientCount, int rank, float alpha, long seed) {
		if (Files.isRegularFile(path)) {
			try (DataInputStream in = new DataInputStream(
					new BufferedInputStream(Files.newInputStream(path)))) {
				if (in.readInt() == MAGIC && in.readInt() == VERSION) {
					LoraHead pos = LoraHead.read(in, dim, gridVolume);
					LoraHead blk = LoraHead.read(in, dim, vocab);
					LoraHead ori = LoraHead.read(in, dim, orientCount);
					if (pos != null && blk != null && ori != null) {
						return new LoraAdapter(pos, blk, ori);
					}
				}
			} catch (IOException | RuntimeException e) {

			}
		}
		return create(dim, gridVolume, vocab, orientCount, rank, alpha, seed);
	}
}
