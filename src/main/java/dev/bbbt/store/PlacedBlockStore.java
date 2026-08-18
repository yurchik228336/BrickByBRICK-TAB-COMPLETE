package dev.bbbt.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PlacedBlockStore {

	private static final class Section {
		private short[] local = new short[8];
		private int[] names = new int[8];
		private byte[] orientations = new byte[8];
		private int count;

		private int indexOf(short key) {
			int low = 0;
			int high = count - 1;
			while (low <= high) {
				int mid = (low + high) >>> 1;
				int cmp = Short.compare(local[mid], key);
				if (cmp < 0) {
					low = mid + 1;
				} else if (cmp > 0) {
					high = mid - 1;
				} else {
					return mid;
				}
			}
			return -(low + 1);
		}

		void put(short key, int nameIdx, byte orientation) {
			int at = indexOf(key);
			if (at >= 0) {
				names[at] = nameIdx;
				orientations[at] = orientation;
				return;
			}
			int insert = -(at + 1);
			if (count == local.length) {
				int grown = Math.max(8, local.length * 2);
				local = Arrays.copyOf(local, grown);
				names = Arrays.copyOf(names, grown);
				orientations = Arrays.copyOf(orientations, grown);
			}
			System.arraycopy(local, insert, local, insert + 1, count - insert);
			System.arraycopy(names, insert, names, insert + 1, count - insert);
			System.arraycopy(orientations, insert, orientations, insert + 1, count - insert);
			local[insert] = key;
			names[insert] = nameIdx;
			orientations[insert] = orientation;
			count++;
		}

		boolean remove(short key) {
			int at = indexOf(key);
			if (at < 0) {
				return false;
			}
			int tail = count - at - 1;
			System.arraycopy(local, at + 1, local, at, tail);
			System.arraycopy(names, at + 1, names, at, tail);
			System.arraycopy(orientations, at + 1, orientations, at, tail);
			count--;
			return true;
		}

		int find(short key) {
			return indexOf(key);
		}
	}

	public interface Visitor {
		void accept(int x, int y, int z, String blockName, int orientation);
	}

	private final Map<Long, Section> sections = new HashMap<>();
	private final List<String> nameTable = new ArrayList<>();
	private final Map<String, Integer> nameIndex = new HashMap<>();
	private boolean dirty;

	private static long sectionKey(int sx, int sy, int sz) {
		return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sy & 0xFFFFF) << 22) | (sz & 0x3FFFFF);
	}

	private static short localKey(int x, int y, int z) {
		return (short) (((y & 15) << 8) | ((z & 15) << 4) | (x & 15));
	}

	private static int floorDiv16(int v) {
		return v >> 4;
	}

	public boolean isDirty() {
		return dirty;
	}

	public int trackedBlocks() {
		int total = 0;
		for (Section section : sections.values()) {
			total += section.count;
		}
		return total;
	}

	public void put(int x, int y, int z, String blockName, int orientation) {
		int nameIdx = nameIndex.computeIfAbsent(blockName, key -> {
			nameTable.add(key);
			return nameTable.size() - 1;
		});
		long key = sectionKey(floorDiv16(x), floorDiv16(y), floorDiv16(z));
		sections.computeIfAbsent(key, k -> new Section())
				.put(localKey(x, y, z), nameIdx, (byte) orientation);
		dirty = true;
	}

	public boolean remove(int x, int y, int z) {
		long key = sectionKey(floorDiv16(x), floorDiv16(y), floorDiv16(z));
		Section section = sections.get(key);
		if (section == null) {
			return false;
		}
		boolean removed = section.remove(localKey(x, y, z));
		if (removed) {
			dirty = true;
			if (section.count == 0) {
				sections.remove(key);
			}
		}
		return removed;
	}

	public boolean contains(int x, int y, int z) {
		Section section = sections.get(sectionKey(floorDiv16(x), floorDiv16(y), floorDiv16(z)));
		return section != null && section.find(localKey(x, y, z)) >= 0;
	}

	public String blockNameAt(int x, int y, int z) {
		Section section = sections.get(sectionKey(floorDiv16(x), floorDiv16(y), floorDiv16(z)));
		if (section == null) {
			return null;
		}
		int at = section.find(localKey(x, y, z));
		return at >= 0 ? nameTable.get(section.names[at]) : null;
	}

	public int orientationAt(int x, int y, int z) {
		Section section = sections.get(sectionKey(floorDiv16(x), floorDiv16(y), floorDiv16(z)));
		if (section == null) {
			return 0;
		}
		int at = section.find(localKey(x, y, z));
		return at >= 0 ? section.orientations[at] & 0xFF : 0;
	}

	public void forEachInCube(int cx, int cy, int cz, int radius, Visitor visitor) {
		int minX = cx - radius;
		int minY = cy - radius;
		int minZ = cz - radius;
		int maxX = cx + radius;
		int maxY = cy + radius;
		int maxZ = cz + radius;

		for (int sx = floorDiv16(minX); sx <= floorDiv16(maxX); sx++) {
			for (int sy = floorDiv16(minY); sy <= floorDiv16(maxY); sy++) {
				for (int sz = floorDiv16(minZ); sz <= floorDiv16(maxZ); sz++) {
					Section section = sections.get(sectionKey(sx, sy, sz));
					if (section == null) {
						continue;
					}
					int baseX = sx << 4;
					int baseY = sy << 4;
					int baseZ = sz << 4;
					for (int i = 0; i < section.count; i++) {
						int packed = section.local[i] & 0xFFFF;
						int x = baseX + (packed & 15);
						int z = baseZ + ((packed >> 4) & 15);
						int y = baseY + ((packed >> 8) & 15);
						if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
							continue;
						}
						visitor.accept(x, y, z, nameTable.get(section.names[i]),
								section.orientations[i] & 0xFF);
					}
				}
			}
		}
	}

	private static final int MAGIC = ('B' << 24) | ('B' << 16) | ('S' << 8) | '1';

	public void save(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
				new GZIPOutputStream(Files.newOutputStream(tmp))))) {
			out.writeInt(MAGIC);
			out.writeInt(nameTable.size());
			for (String name : nameTable) {
				out.writeUTF(name);
			}
			out.writeInt(sections.size());
			for (Map.Entry<Long, Section> entry : sections.entrySet()) {
				Section section = entry.getValue();
				out.writeLong(entry.getKey());
				out.writeInt(section.count);
				for (int i = 0; i < section.count; i++) {
					out.writeShort(section.local[i]);
					out.writeInt(section.names[i]);
					out.writeByte(section.orientations[i]);
				}
			}
		}
		Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		dirty = false;
	}

	public static PlacedBlockStore loadOrCreate(Path path) {
		PlacedBlockStore store = new PlacedBlockStore();
		if (!Files.isRegularFile(path)) {
			return store;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(
				new GZIPInputStream(Files.newInputStream(path))))) {
			if (in.readInt() != MAGIC) {
				return store;
			}
			int tableSize = in.readInt();
			if (tableSize < 0 || tableSize > 1 << 20) {
				return store;
			}
			String[] table = new String[tableSize];
			for (int i = 0; i < tableSize; i++) {
				table[i] = in.readUTF();
				store.nameIndex.putIfAbsent(table[i], i);
				store.nameTable.add(table[i]);
			}

			int sectionCount = in.readInt();
			for (int s = 0; s < sectionCount; s++) {
				long key = in.readLong();
				int entries = in.readInt();
				if (entries < 0 || entries > 4096) {
					store.sections.clear();
					return store;
				}
				Section section = new Section();
				for (int i = 0; i < entries; i++) {
					short local = in.readShort();
					int nameIdx = in.readInt();
					byte orientation = in.readByte();
					if (nameIdx >= 0 && nameIdx < tableSize) {
						section.put(local, nameIdx, orientation);
					}
				}
				if (section.count > 0) {
					store.sections.put(key, section);
				}
			}
			store.dirty = false;
		} catch (IOException | RuntimeException e) {
			store.sections.clear();
		}
		return store;
	}
}
