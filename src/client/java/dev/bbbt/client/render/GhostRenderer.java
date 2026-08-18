package dev.bbbt.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.bbbt.BrickByBrickTab;
import dev.bbbt.client.InventorySupport;
import dev.bbbt.client.LookTarget;
import dev.bbbt.client.SuggestionController;
import dev.bbbt.config.BbbtConfig;
import dev.bbbt.core.BbbtRuntime;
import dev.bbbt.palette.BlockStateCodec;
import dev.bbbt.suggest.FocusBox;
import dev.bbbt.suggest.Suggestion;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GhostRenderer {

	private static final float OUTLINE_WIDTH = 2.5f;

	private static final int FULL_BRIGHT = 0x00F000F0;
	private static final int RGB_HAVE = 0x66FF99;
	private static final int RGB_HAVE_DIM = 0x338866;
	private static final int RGB_MISSING = 0xFF5555;
	private static final int RGB_MISSING_DIM = 0xAA3333;
	private static final int RGB_FOCUS = 0x66AAFF;

	private final BbbtRuntime runtime;
	private final SuggestionController controller;
	private final List<BlockStateModelPart> parts = new ArrayList<>();
	private final RandomSource random = RandomSource.create();

	private boolean modelDrawDisabled;

	public GhostRenderer(BbbtRuntime runtime, SuggestionController controller) {
		this.runtime = runtime;
		this.controller = controller;
	}

	public void render(LevelRenderContext context) {
		BbbtConfig config = runtime.config();
		if (!config.enabled) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}

		Vec3 camera = context.levelState().cameraRenderState.pos;
		SubmitNodeCollector collector = context.submitNodeCollector();

		FocusBox focus = controller.focusBox();
		if (focus != null) {
			drawFocus(collector, camera, focus);
		}

		if (!config.showGhostBlocks) {
			return;
		}

		Suggestion selected = controller.selected();
		if (selected == null) {
			return;
		}

		BlockPos lookCell = LookTarget.placementCell(client);
		if (config.ghostLookOnly) {
			if (lookCell == null || !sameCell(selected, lookCell)) {
				return;
			}
		}

		LocalPlayer player = client.player;
		draw(client, level, collector, camera, selected, config.ghostOpacity, config.showOutline,
				true, isMissing(player, selected));

		if (!config.ghostLookOnly) {
			float previewAlpha = config.ghostOpacity * 0.28f;
			for (Suggestion suggestion : controller.preview()) {
				if (sameCell(suggestion, selected)) {
					continue;
				}
				draw(client, level, collector, camera, suggestion, previewAlpha, false, false,
						isMissing(player, suggestion));
				previewAlpha *= 0.7f;
			}
		}
	}

	private static boolean sameCell(Suggestion suggestion, BlockPos pos) {
		return suggestion.x() == pos.getX() && suggestion.y() == pos.getY()
				&& suggestion.z() == pos.getZ();
	}

	private static boolean sameCell(Suggestion a, Suggestion b) {
		return a.x() == b.x() && a.y() == b.y() && a.z() == b.z();
	}

	private void draw(Minecraft client, ClientLevel level, SubmitNodeCollector collector,
			Vec3 camera, Suggestion suggestion, float alpha, boolean outline, boolean selected,
			boolean missing) {
		if (alpha <= 0.02f) {
			return;
		}

		BlockState state = BlockStateCodec.blockByName(suggestion.blockName())
				.map(block -> BlockStateCodec.withOrientation(block.defaultBlockState(),
						suggestion.orientation()))
				.orElse(null);
		if (state == null || state.isAir()) {
			return;
		}

		BlockPos pos = new BlockPos(suggestion.x(), suggestion.y(), suggestion.z());
		BlockState existing = level.getBlockState(pos);
		if (!(existing.isAir() || existing.canBeReplaced())) {
			return;
		}

		PoseStack pose = new PoseStack();
		pose.translate(suggestion.x() - camera.x, suggestion.y() - camera.y,
				suggestion.z() - camera.z);
		try {
			if (!modelDrawDisabled) {
				submitGhostModel(client, level, pose, collector, state, pos, alpha, missing,
						(float) (camera.x - suggestion.x()),
						(float) (camera.y - suggestion.y()),
						(float) (camera.z - suggestion.z()));
			}
			if (outline) {
				VoxelShape shape = state.getShape(level, pos);
				int outlineColor = ARGB.color((int) (Math.min(1f, alpha * 1.6f) * 255f),
						outlineRgb(selected, missing));
				collector.submitShapeOutline(pose, shape, RenderTypes.lines(), outlineColor,
						OUTLINE_WIDTH, true);
			}
		} catch (RuntimeException e) {
			modelDrawDisabled = true;
			BrickByBrickTab.LOG.error("Ghost block model failed, outlines only", e);
		}
	}

	private void drawFocus(SubmitNodeCollector collector, Vec3 camera, FocusBox focus) {
		PoseStack pose = new PoseStack();
		pose.translate(focus.minX() - camera.x, focus.minY() - camera.y, focus.minZ() - camera.z);
		VoxelShape shape = Shapes.box(0, 0, 0, focus.sizeX(), focus.sizeY(), focus.sizeZ());
		int color = ARGB.color(180, RGB_FOCUS);
		collector.submitShapeOutline(pose, shape, RenderTypes.lines(), color, 2.0f, true);
	}

	private void submitGhostModel(Minecraft client, ClientLevel level, PoseStack pose,
			SubmitNodeCollector collector, BlockState state, BlockPos pos, float alpha,
			boolean missing, float camX, float camY, float camZ) {
		BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(state);
		parts.clear();
		random.setSeed(pos.asLong());
		model.collectParts(random, parts);
		if (parts.isEmpty()) {
			return;
		}

		List<BlockStateModelPart> snapshot = List.copyOf(parts);
		int[] tints = tints(client, level, state, pos);
		int packedAlpha = ARGB.as8BitChannel(alpha);
		int missingTint = missing ? RGB_MISSING : -1;
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
				(submittedPose, buffer) -> emitParts(submittedPose, buffer, snapshot, tints,
						packedAlpha, missingTint, camX, camY, camZ));
	}

	private static void emitParts(PoseStack.Pose pose, VertexConsumer buffer,
			List<BlockStateModelPart> modelParts, int[] tints, int packedAlpha, int missingTint,
			float camX, float camY, float camZ) {
		QuadInstance instance = new QuadInstance();
		instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
		instance.setLightCoords(FULL_BRIGHT);
		for (BlockStateModelPart part : modelParts) {
			for (Direction direction : Direction.values()) {
				emitQuads(pose, buffer, instance, part.getQuads(direction), tints, packedAlpha,
						missingTint, camX, camY, camZ);
			}
		}
	}

	private static boolean quadFacesCamera(BakedQuad quad, float camX, float camY, float camZ) {
		Direction direction = quad.direction();
		var point = quad.position0();
		float dx = camX - point.x();
		float dy = camY - point.y();
		float dz = camZ - point.z();
		return dx * direction.getStepX() + dy * direction.getStepY() + dz * direction.getStepZ() > 0.001f;
	}

	private static void emitQuads(PoseStack.Pose pose, VertexConsumer buffer, QuadInstance instance,
			List<BakedQuad> quads, int[] tints, int packedAlpha, int missingTint,
			float camX, float camY, float camZ) {
		for (BakedQuad quad : quads) {
			if (!quadFacesCamera(quad, camX, camY, camZ)) {
				continue;
			}
			BakedQuad.MaterialInfo info = quad.materialInfo();
			int rgb = 0xFFFFFF;
			if (info.isTinted()) {
				int index = info.tintIndex();
				if (index >= 0 && index < tints.length) {
					rgb = tints[index];
				}
			}
			if (missingTint >= 0) {
				rgb = mixRgb(rgb, missingTint, 0.55f);
			}
			instance.setColor(ARGB.color(packedAlpha, rgb));
			buffer.putBakedQuad(pose, quad, instance);
		}
	}

	private static int mixRgb(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		int r = (int) (ar + (br - ar) * t);
		int g = (int) (ag + (bg - ag) * t);
		int bl = (int) (ab + (bb - ab) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int[] tints(Minecraft client, ClientLevel level, BlockState state, BlockPos pos) {
		int[] tints = {0xFFFFFF, 0xFFFFFF};
		var colors = client.getBlockColors();
		for (int i = 0; i < tints.length; i++) {
			var source = colors.getTintSource(state, i);
			if (source != null) {
				tints[i] = source.color(state) & 0xFFFFFF;
			}
		}
		return tints;
	}

	private static int outlineRgb(boolean selected, boolean missing) {
		if (missing) {
			return selected ? RGB_MISSING : RGB_MISSING_DIM;
		}
		return selected ? RGB_HAVE : RGB_HAVE_DIM;
	}

	private static boolean isMissing(LocalPlayer player, Suggestion suggestion) {
		if (player == null) {
			return true;
		}
		Block block = BlockStateCodec.blockByName(suggestion.blockName()).orElse(null);
		return block == null || !InventorySupport.has(player, block);
	}
}
