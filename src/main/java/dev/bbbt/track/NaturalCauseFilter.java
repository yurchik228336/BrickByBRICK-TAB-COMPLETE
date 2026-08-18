package dev.bbbt.track;

import java.util.Set;

public final class NaturalCauseFilter {
	private NaturalCauseFilter() {
	}

	private static final Set<String> NEVER_A_PLACEMENT = Set.of(
			"minecraft:water", "minecraft:lava", "minecraft:bubble_column",
			"minecraft:fire", "minecraft:soul_fire",
			"minecraft:snow", "minecraft:ice", "minecraft:frosted_ice",
			"minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
			"minecraft:beetroots", "minecraft:torchflower_crop", "minecraft:pitcher_crop",
			"minecraft:melon", "minecraft:pumpkin_stem", "minecraft:melon_stem",
			"minecraft:attached_pumpkin_stem", "minecraft:attached_melon_stem",
			"minecraft:sugar_cane", "minecraft:bamboo", "minecraft:bamboo_sapling",
			"minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass",
			"minecraft:tall_seagrass", "minecraft:cactus", "minecraft:chorus_flower",
			"minecraft:chorus_plant", "minecraft:cocoa", "minecraft:sweet_berry_bush",
			"minecraft:turtle_egg", "minecraft:frogspawn", "minecraft:tripwire",
			"minecraft:powder_snow", "minecraft:pointed_dripstone",
			"minecraft:moss_carpet", "minecraft:moss_block", "minecraft:pale_moss_carpet",
			"minecraft:nether_wart", "minecraft:cobweb");

	private static final String[] NATURAL_SUFFIXES = {
			"_sapling", "_bud", "_cluster", "_vines", "_vine", "_fungus", "_roots",
			"_wart_block", "_mushroom_block", "_sprouts"
	};

	private static final String[] NATURAL_PREFIXES = {
			"minecraft:sculk", "minecraft:cave_vines", "minecraft:big_dripleaf",
			"minecraft:small_dripleaf", "minecraft:twisting_vines",
			"minecraft:weeping_vines", "minecraft:glow_lichen", "minecraft:vine"
	};

	public static boolean isNaturalOnly(String registryName) {
		if (NEVER_A_PLACEMENT.contains(registryName)) {
			return true;
		}
		for (String suffix : NATURAL_SUFFIXES) {
			if (registryName.endsWith(suffix)) {
				return true;
			}
		}
		for (String prefix : NATURAL_PREFIXES) {
			if (registryName.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isGravityAffected(String registryName) {
		return registryName.endsWith("_concrete_powder")
				|| registryName.endsWith("_anvil")
				|| registryName.equals("minecraft:anvil")
				|| registryName.equals("minecraft:sand")
				|| registryName.equals("minecraft:red_sand")
				|| registryName.equals("minecraft:gravel")
				|| registryName.equals("minecraft:suspicious_sand")
				|| registryName.equals("minecraft:suspicious_gravel")
				|| registryName.equals("minecraft:dragon_egg")
				|| registryName.equals("minecraft:pointed_dripstone")
				|| registryName.equals("minecraft:scaffolding");
	}
}
