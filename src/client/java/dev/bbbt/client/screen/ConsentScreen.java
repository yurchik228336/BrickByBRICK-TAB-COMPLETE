package dev.bbbt.client.screen;

import dev.bbbt.config.BbbtConfig;
import dev.bbbt.config.BbbtConfig.ConsentState;
import dev.bbbt.core.BbbtRuntime;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ConsentScreen extends Screen {

	private static final int CONTENT_WIDTH = 340;

	private final Screen parent;
	private final BbbtConfig config;

	private ConsentState placement;
	private ConsentState caption;

	private Button placementYes;
	private Button placementNo;
	private Button captionYes;
	private Button captionNo;

	public ConsentScreen(Screen parent) {
		super(Component.translatable("bbbt.consent.title"));
		this.parent = parent;
		this.config = BbbtRuntime.get().config();
		this.placement = config.placementConsent;
		this.caption = config.captionConsent;
	}

	@Override
	protected void init() {
		LinearLayout layout = LinearLayout.vertical().spacing(6);
		layout.addChild(new StringWidget(title, font));
		layout.addChild(paragraph("bbbt.consent.intro"));

		layout.addChild(new StringWidget(
				Component.translatable("bbbt.consent.placement.title"), font));
		layout.addChild(paragraph("bbbt.consent.placement.body"));
		LinearLayout placementRow = layout.addChild(LinearLayout.horizontal().spacing(8));
		placementYes = placementRow.addChild(choice("bbbt.consent.yes",
				() -> setPlacement(ConsentState.GRANTED)));
		placementNo = placementRow.addChild(choice("bbbt.consent.no",
				() -> setPlacement(ConsentState.DENIED)));

		layout.addChild(new StringWidget(
				Component.translatable("bbbt.consent.caption.title"), font));
		layout.addChild(paragraph("bbbt.consent.caption.body"));
		LinearLayout captionRow = layout.addChild(LinearLayout.horizontal().spacing(8));
		captionYes = captionRow.addChild(choice("bbbt.consent.yes",
				() -> setCaption(ConsentState.GRANTED)));
		captionNo = captionRow.addChild(choice("bbbt.consent.no",
				() -> setCaption(ConsentState.DENIED)));

		layout.addChild(paragraph("bbbt.consent.independent"));
		layout.addChild(paragraph("bbbt.consent.reversible"));

		LinearLayout footer = layout.addChild(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("bbbt.consent.confirm"),
				button -> save()).width(150).build());
		footer.addChild(Button.builder(Component.translatable("bbbt.consent.later"),
				button -> onClose()).width(150).build());

		layout.arrangeElements();
		layout.setX((width - layout.getWidth()) / 2);
		layout.setY(Math.max(8, (height - layout.getHeight()) / 2));
		layout.visitWidgets(this::addRenderableWidget);

		refreshChoiceLabels();
	}

	private Button choice(String key, Runnable action) {
		return Button.builder(Component.translatable(key), button -> action.run())
				.width(150)
				.build();
	}

	private MultiLineTextWidget paragraph(String key) {
		return new MultiLineTextWidget(Component.translatable(key), font)
				.setMaxWidth(CONTENT_WIDTH);
	}

	private void setPlacement(ConsentState state) {
		placement = state;
		refreshChoiceLabels();
	}

	private void setCaption(ConsentState state) {
		caption = state;
		refreshChoiceLabels();
	}

	private void refreshChoiceLabels() {
		placementYes.active = placement != ConsentState.GRANTED;
		placementNo.active = placement != ConsentState.DENIED;
		captionYes.active = caption != ConsentState.GRANTED;
		captionNo.active = caption != ConsentState.DENIED;
	}

	private void save() {
		boolean captionWithdrawn = config.captionConsent == ConsentState.GRANTED
				&& caption != ConsentState.GRANTED;
		boolean placementWithdrawn = config.placementConsent == ConsentState.GRANTED
				&& placement != ConsentState.GRANTED;

		config.placementConsent = placement;
		config.captionConsent = caption;
		BbbtRuntime.get().saveConfig();

		if (captionWithdrawn) {
			BbbtRuntime.get().captions().forgetEverything();
			BbbtRuntime.get().workerDeleteCaptions();
		}
		if (placementWithdrawn) {
			BbbtRuntime.get().workerDeletePlacements();
		}
		onClose();
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent);
		}
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(title,
				Component.translatable("bbbt.consent.intro"));
	}
}
