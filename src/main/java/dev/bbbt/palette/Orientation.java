package dev.bbbt.palette;

public final class Orientation {
	private Orientation() {
	}

	public static final int NONE = 0;

	public static final int FACING_NORTH = 1;
	public static final int FACING_EAST = 2;
	public static final int FACING_SOUTH = 3;
	public static final int FACING_WEST = 4;
	public static final int FACING_UP = 5;
	public static final int FACING_DOWN = 6;

	public static final int FACING_NORTH_TOP = 7;
	public static final int FACING_EAST_TOP = 8;
	public static final int FACING_SOUTH_TOP = 9;
	public static final int FACING_WEST_TOP = 10;

	public static final int AXIS_X = 11;
	public static final int AXIS_Y = 12;
	public static final int AXIS_Z = 13;

	public static final int SLAB_BOTTOM = 14;
	public static final int SLAB_TOP = 15;
	public static final int SLAB_DOUBLE = 16;

	public static final int COUNT = 24;

	private static final String[] NAMES = new String[COUNT];

	static {
		for (int i = 0; i < COUNT; i++) {
			NAMES[i] = "reserved_" + i;
		}
		NAMES[NONE] = "none";
		NAMES[FACING_NORTH] = "facing_north";
		NAMES[FACING_EAST] = "facing_east";
		NAMES[FACING_SOUTH] = "facing_south";
		NAMES[FACING_WEST] = "facing_west";
		NAMES[FACING_UP] = "facing_up";
		NAMES[FACING_DOWN] = "facing_down";
		NAMES[FACING_NORTH_TOP] = "facing_north_top";
		NAMES[FACING_EAST_TOP] = "facing_east_top";
		NAMES[FACING_SOUTH_TOP] = "facing_south_top";
		NAMES[FACING_WEST_TOP] = "facing_west_top";
		NAMES[AXIS_X] = "axis_x";
		NAMES[AXIS_Y] = "axis_y";
		NAMES[AXIS_Z] = "axis_z";
		NAMES[SLAB_BOTTOM] = "slab_bottom";
		NAMES[SLAB_TOP] = "slab_top";
		NAMES[SLAB_DOUBLE] = "slab_double";
	}

	public static String name(int orientation) {
		return orientation >= 0 && orientation < COUNT ? NAMES[orientation] : "invalid";
	}

	public static boolean isValid(int orientation) {
		return orientation >= 0 && orientation < COUNT;
	}
}
