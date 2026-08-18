package dev.bbbt.palette;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockStateCodec {
	private BlockStateCodec() {
	}

	private static final Map<Block, String> NAME_CACHE = new ConcurrentHashMap<>();

	public static String registryName(BlockState state) {
		return registryName(state.getBlock());
	}

	public static String registryName(Block block) {
		return NAME_CACHE.computeIfAbsent(block, b -> BuiltInRegistries.BLOCK.getKey(b).toString());
	}

	public static Optional<Block> blockByName(String registryName) {
		Identifier id = Identifier.tryParse(registryName);
		if (id == null) {
			return Optional.empty();
		}
		return BuiltInRegistries.BLOCK.getOptional(id);
	}

	public static int orientationOf(BlockState state) {
		if (state.hasProperty(BlockStateProperties.SLAB_TYPE)) {
			return switch (state.getValue(BlockStateProperties.SLAB_TYPE)) {
				case BOTTOM -> Orientation.SLAB_BOTTOM;
				case TOP -> Orientation.SLAB_TOP;
				case DOUBLE -> Orientation.SLAB_DOUBLE;
			};
		}

		boolean topHalf = state.hasProperty(BlockStateProperties.HALF)
				&& state.getValue(BlockStateProperties.HALF) == Half.TOP;

		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			return topHalf ? topFacing(facing) : facing(facing);
		}
		if (state.hasProperty(BlockStateProperties.FACING)) {
			return facing(state.getValue(BlockStateProperties.FACING));
		}
		if (state.hasProperty(BlockStateProperties.AXIS)) {
			return axis(state.getValue(BlockStateProperties.AXIS));
		}
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
			return axis(state.getValue(BlockStateProperties.HORIZONTAL_AXIS));
		}
		return Orientation.NONE;
	}

	private static int facing(Direction direction) {
		return switch (direction) {
			case NORTH -> Orientation.FACING_NORTH;
			case EAST -> Orientation.FACING_EAST;
			case SOUTH -> Orientation.FACING_SOUTH;
			case WEST -> Orientation.FACING_WEST;
			case UP -> Orientation.FACING_UP;
			case DOWN -> Orientation.FACING_DOWN;
		};
	}

	private static int topFacing(Direction direction) {
		return switch (direction) {
			case NORTH -> Orientation.FACING_NORTH_TOP;
			case EAST -> Orientation.FACING_EAST_TOP;
			case SOUTH -> Orientation.FACING_SOUTH_TOP;
			case WEST -> Orientation.FACING_WEST_TOP;
			default -> facing(direction);
		};
	}

	private static int axis(Direction.Axis value) {
		return switch (value) {
			case X -> Orientation.AXIS_X;
			case Y -> Orientation.AXIS_Y;
			case Z -> Orientation.AXIS_Z;
		};
	}

	public static BlockState withOrientation(BlockState base, int orientation) {
		return switch (orientation) {
			case Orientation.SLAB_BOTTOM -> base.trySetValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
			case Orientation.SLAB_TOP -> base.trySetValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
			case Orientation.SLAB_DOUBLE -> base.trySetValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE);

			case Orientation.FACING_NORTH -> withFacing(base, Direction.NORTH, false);
			case Orientation.FACING_EAST -> withFacing(base, Direction.EAST, false);
			case Orientation.FACING_SOUTH -> withFacing(base, Direction.SOUTH, false);
			case Orientation.FACING_WEST -> withFacing(base, Direction.WEST, false);
			case Orientation.FACING_UP -> withFacing(base, Direction.UP, false);
			case Orientation.FACING_DOWN -> withFacing(base, Direction.DOWN, false);

			case Orientation.FACING_NORTH_TOP -> withFacing(base, Direction.NORTH, true);
			case Orientation.FACING_EAST_TOP -> withFacing(base, Direction.EAST, true);
			case Orientation.FACING_SOUTH_TOP -> withFacing(base, Direction.SOUTH, true);
			case Orientation.FACING_WEST_TOP -> withFacing(base, Direction.WEST, true);

			case Orientation.AXIS_X -> withAxis(base, Direction.Axis.X);
			case Orientation.AXIS_Y -> withAxis(base, Direction.Axis.Y);
			case Orientation.AXIS_Z -> withAxis(base, Direction.Axis.Z);

			default -> base;
		};
	}

	private static BlockState withFacing(BlockState base, Direction direction, boolean topHalf) {
		BlockState result = base;
		if (direction.getAxis().isHorizontal()) {
			result = result.trySetValue(BlockStateProperties.HORIZONTAL_FACING, direction);
		}
		result = result.trySetValue(BlockStateProperties.FACING, direction);
		if (topHalf) {
			result = result.trySetValue(BlockStateProperties.HALF, Half.TOP);
		}
		return result;
	}

	private static BlockState withAxis(BlockState base, Direction.Axis axis) {
		BlockState result = base.trySetValue(BlockStateProperties.AXIS, axis);
		if (axis != Direction.Axis.Y) {
			result = result.trySetValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
		}
		return result;
	}
}
