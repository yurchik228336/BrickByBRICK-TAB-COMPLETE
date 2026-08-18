package dev.bbbt.suggest;

public record FocusBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

	public static FocusBox of(int ax, int ay, int az, int bx, int by, int bz) {
		return new FocusBox(
				Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
				Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
	}

	public boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
	}

	public int sizeX() {
		return maxX - minX + 1;
	}

	public int sizeY() {
		return maxY - minY + 1;
	}

	public int sizeZ() {
		return maxZ - minZ + 1;
	}
}
