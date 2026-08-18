package dev.bbbt.text;

import java.util.List;

public final class CaptionSample {

	public static final String SOURCE_PLAYER = "player_annotation";
	public static final String SOURCE_AUTO = "auto_caption";
	public static final String SOURCE_SIGN = "sign_text";
	public static final String SOURCE_SCHEMATIC = "schematic_name";

	public String id;
	public String source;
	public String lang;
	public String text;

	public boolean redacted;

	public int width;
	public int height;
	public int depth;
	public int placements;

	public Attributes attributes;

	public List<String> palette;

	public int[][] steps;

	public String mcVersion;
	public String modVersion;

	public long day;

	public static final class Attributes {
		public List<String> materials;
		public double hollowness;
		public double symmetryX;
		public double symmetryZ;
		public int glassCount;
		public int doorCount;
		public int stairCount;
		public int slabCount;
		public int lightCount;
		public boolean hasFloor;
		public boolean hasWalls;
		public boolean hasRoof;
		public boolean slopedRoof;
		public String footprint;
		public String heightClass;
		public String sizeClass;
		public List<String> purposes;
	}
}
