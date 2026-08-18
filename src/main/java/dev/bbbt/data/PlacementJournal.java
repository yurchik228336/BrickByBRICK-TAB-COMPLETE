package dev.bbbt.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlacementJournal {
	private final int capacity;

	private final int[] xs;
	private final int[] ys;
	private final int[] zs;
	private final int[] names;
	private final byte[] orientations;
	private final int[] sessions;

	private final int[] times;

	private int head;
	private int size;
	private int currentSession;
	private long epochMillis = System.currentTimeMillis();

	private final List<String> nameTable = new ArrayList<>();
	private final Map<String, Integer> nameIndex = new HashMap<>();

	public PlacementJournal(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		this.capacity = capacity;
		this.xs = new int[capacity];
		this.ys = new int[capacity];
		this.zs = new int[capacity];
		this.names = new int[capacity];
		this.orientations = new byte[capacity];
		this.sessions = new int[capacity];
		this.times = new int[capacity];
	}

	public int size() {
		return size;
	}

	public int capacity() {
		return capacity;
	}

	public int currentSession() {
		return currentSession;
	}

	public void beginSession() {
		currentSession++;
	}

	private int slot(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException("index " + index + " of " + size);
		}
		return (head - size + index + capacity) % capacity;
	}

	public int x(int index) {
		return xs[slot(index)];
	}

	public int y(int index) {
		return ys[slot(index)];
	}

	public int z(int index) {
		return zs[slot(index)];
	}

	public int orientation(int index) {
		return orientations[slot(index)] & 0xFF;
	}

	public int session(int index) {
		return sessions[slot(index)];
	}

	public int timeSeconds(int index) {
		return times[slot(index)];
	}

	public long epochMillis() {
		return epochMillis;
	}

	public String blockName(int index) {
		return nameTable.get(names[slot(index)]);
	}

	public void record(int x, int y, int z, String blockName, int orientation) {
		int nameIdx = nameIndex.computeIfAbsent(blockName, key -> {
			nameTable.add(key);
			return nameTable.size() - 1;
		});

		xs[head] = x;
		ys[head] = y;
		zs[head] = z;
		names[head] = nameIdx;
		orientations[head] = (byte) orientation;
		sessions[head] = currentSession;
		times[head] = (int) ((System.currentTimeMillis() - epochMillis) / 1000L);

		head = (head + 1) % capacity;
		if (size < capacity) {
			size++;
		}
	}

	public boolean forget(int x, int y, int z) {
		for (int i = size - 1; i >= 0; i--) {
			int s = slot(i);
			if (xs[s] == x && ys[s] == y && zs[s] == z) {
				for (int j = i; j < size - 1; j++) {
					int from = slot(j + 1);
					int to = slot(j);
					xs[to] = xs[from];
					ys[to] = ys[from];
					zs[to] = zs[from];
					names[to] = names[from];
					orientations[to] = orientations[from];
					sessions[to] = sessions[from];
					times[to] = times[from];
				}
				head = (head - 1 + capacity) % capacity;
				size--;
				return true;
			}
		}
		return false;
	}

	public void clear() {
		head = 0;
		size = 0;
		nameTable.clear();
		nameIndex.clear();
	}

	public JournalSnapshot snapshot() {
		int n = size;
		int[] x = new int[n];
		int[] y = new int[n];
		int[] z = new int[n];
		String[] names = new String[n];
		int[] orientations = new int[n];
		int[] sessionIds = new int[n];
		int[] timeSeconds = new int[n];
		for (int i = 0; i < n; i++) {
			int s = slot(i);
			x[i] = xs[s];
			y[i] = ys[s];
			z[i] = zs[s];
			names[i] = nameTable.get(this.names[s]);
			orientations[i] = this.orientations[s] & 0xFF;
			sessionIds[i] = sessions[s];
			timeSeconds[i] = times[s];
		}
		return new JournalSnapshot(x, y, z, names, orientations, sessionIds, timeSeconds, n);
	}

	private static final int MAGIC = ('B' << 24) | ('B' << 16) | ('J' << 8) | '2';

	public void save(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(Files.newOutputStream(tmp)))) {
			out.writeInt(MAGIC);
			out.writeInt(currentSession);
			out.writeLong(epochMillis);
			out.writeInt(nameTable.size());
			for (String name : nameTable) {
				out.writeUTF(name);
			}
			out.writeInt(size);
			for (int i = 0; i < size; i++) {
				int s = slot(i);
				out.writeInt(xs[s]);
				out.writeInt(ys[s]);
				out.writeInt(zs[s]);
				out.writeInt(names[s]);
				out.writeByte(orientations[s]);
				out.writeInt(sessions[s]);
				out.writeInt(times[s]);
			}
		}
		Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
	}

	public static PlacementJournal loadOrCreate(Path path, int capacity) {
		PlacementJournal journal = new PlacementJournal(capacity);
		if (!Files.isRegularFile(path)) {
			return journal;
		}
		try (DataInputStream in = new DataInputStream(
				new BufferedInputStream(Files.newInputStream(path)))) {
			if (in.readInt() != MAGIC) {
				return journal;
			}
			journal.currentSession = in.readInt();
			journal.epochMillis = in.readLong();

			int tableSize = in.readInt();
			if (tableSize < 0 || tableSize > 1 << 20) {
				return journal;
			}
			String[] table = new String[tableSize];
			for (int i = 0; i < tableSize; i++) {
				table[i] = in.readUTF();
			}

			int count = in.readInt();
			int skip = Math.max(0, count - capacity);
			for (int i = 0; i < count; i++) {
				int x = in.readInt();
				int y = in.readInt();
				int z = in.readInt();
				int nameIdx = in.readInt();
				int orientation = in.readByte() & 0xFF;
				int session = in.readInt();
				int time = in.readInt();
				if (i < skip || nameIdx < 0 || nameIdx >= tableSize) {
					continue;
				}
				journal.record(x, y, z, table[nameIdx], orientation);
				int written = (journal.head - 1 + capacity) % capacity;
				journal.sessions[written] = session;
				journal.times[written] = time;
			}

			journal.beginSession();
		} catch (IOException | RuntimeException e) {
			journal.clear();
		}
		return journal;
	}
}
