package dev.bbbt.client.screen;

import java.util.Locale;

import dev.bbbt.core.BbbtRuntime;
import dev.bbbt.text.CaptionService;
import dev.bbbt.text.TextSanitizer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AnnotateScreen extends Screen {

	private static final int CONTENT_WIDTH = 320;

	private final Screen parent;
	private final BbbtRuntime runtime;

	private CaptionService.Described described;
	private EditBox input;

	public AnnotateScreen(Screen parent) {
		super(Component.translatable("bbbt.annotate.title"));
		this.parent = parent;
		this.runtime = BbbtRuntime.get();
	}

	@Override
	protected void init() {
		LinearLayout layout = LinearLayout.vertical().spacing(6);
		layout.addChild(new StringWidget(title, font));

		if (!runtime.config().mayStoreCaptions()) {
			layout.addChild(paragraph("bbbt.annotate.disabled"));
			layout.addChild(Button.builder(
					Component.translatable("bbbt.consent.reopen"),
					button -> minecraft.setScreenAndShow(new ConsentScreen(parent)))
					.width(200).build());
			finishLayout(layout);
			return;
		}

		described = runtime.captions().describeCurrent(
				runtime.tracker().journal().snapshot(), Locale.ENGLISH);
		if (described == null) {
			layout.addChild(paragraph("bbbt.annotate.nothing"));
			finishLayout(layout);
			return;
		}

		layout.addChild(new StringWidget(Component.translatable("bbbt.annotate.stats",
				described.segment().size(), described.attributes().width(),
				described.attributes().height(), described.attributes().depth()), font));
		layout.addChild(paragraph("bbbt.annotate.prompt"));

		input = new EditBox(font, CONTENT_WIDTH, 20, Component.translatable("bbbt.annotate.prompt"));
		input.setMaxLength(TextSanitizer.MAX_LENGTH);
		input.setHint(Component.translatable("bbbt.annotate.hint"));
		layout.addChild(input);

		layout.addChild(new MultiLineTextWidget(
				Component.translatable("bbbt.annotate.suggestion", described.suggestedCaption()),
				font).setMaxWidth(CONTENT_WIDTH));

		LinearLayout footer = layout.addChild(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("bbbt.annotate.save"),
				button -> save()).width(150).build());
		footer.addChild(Button.builder(Component.translatable("bbbt.annotate.skip"),
				button -> onClose()).width(150).build());

		finishLayout(layout);
		setInitialFocus(input);
	}

	private void finishLayout(LinearLayout layout) {
		layout.arrangeElements();
		layout.setX((width - layout.getWidth()) / 2);
		layout.setY(Math.max(8, (height - layout.getHeight()) / 2));
		layout.visitWidgets(this::addRenderableWidget);
	}

	private MultiLineTextWidget paragraph(String key) {
		return new MultiLineTextWidget(Component.translatable(key), font)
				.setMaxWidth(CONTENT_WIDTH);
	}

	private void save() {
		if (described == null || input == null) {
			onClose();
			return;
		}
		String text = input.getValue();
		boolean accepted = runtime.captions().annotate(
				runtime.tracker().journal().snapshot(), described.segment(), text,
				language());
		feedback(accepted ? "bbbt.annotate.saved" : "bbbt.annotate.rejected");
		onClose();
	}

	private String language() {
		if (minecraft == null) {
			return "en";
		}
		String selected = minecraft.getLanguageManager().getSelected();
		int underscore = selected.indexOf('_');
		return underscore > 0 ? selected.substring(0, underscore) : selected;
	}

	private void feedback(String key) {
		if (minecraft != null && minecraft.player != null) {
			minecraft.player.sendOverlayMessage(Component.translatable(key));
		}
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}
}
