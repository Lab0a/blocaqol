package com.blocaqol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

/**
 * Écran de calibration : capture l'écran (pause) et permet de déplacer un cadran
 * pour définir la zone d'analyse du minijeu de pêche.
 */
public class FishingCalibrationScreen extends Screen {

	private static final Identifier SCREENSHOT_ID = Identifier.of(BlocaQoL.MOD_ID, "calibration_screenshot");
	private static final int BORDER_THICKNESS = 3;
	private static final int MIN_SIZE = 10;
	private static final int RESIZE_HANDLE = 8;

	private final BlocaQoLConfig config;
	private NativeImageBackedTexture screenshotTexture;
	private int texWidth, texHeight;

	// Cadran : position et taille en coordonnées écran (scaled)
	private int frameX, frameY, frameW, frameH;
	private boolean dragging;
	private int dragOffsetX, dragOffsetY;
	private int resizeMode; // 0=none, 1-8=corner/edge

	public FishingCalibrationScreen(BlocaQoLConfig config) {
		super(Text.literal("Calibration zone minijeu"));
		this.config = config;
		this.frameX = config.trackerZoneX >= 0 ? config.trackerZoneX : -1;
		this.frameY = config.trackerZoneY >= 0 ? config.trackerZoneY : -1;
		this.frameW = config.trackerZoneW > 0 ? config.trackerZoneW : 20;
		this.frameH = config.trackerZoneH > 0 ? config.trackerZoneH : 80;
	}

	/** Ouvre l'écran après capture. Appelé depuis le callback de takeScreenshot. */
	public static void openWithScreenshot(BlocaQoLConfig config) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), img -> {
				client.execute(() -> client.setScreen(new FishingCalibrationScreen(config, img)));
			});
		});
	}

	private FishingCalibrationScreen(BlocaQoLConfig config, NativeImage img) {
		this(config);
		this.screenshotTexture = new NativeImageBackedTexture(() -> "blocaqol_calibration", img);
		this.texWidth = img.getWidth();
		this.texHeight = img.getHeight();
		MinecraftClient.getInstance().getTextureManager().registerTexture(SCREENSHOT_ID, screenshotTexture);
	}

	@Override
	protected void init() {
		super.init();
		if (frameX < 0 || frameY < 0) {
			frameX = Math.max(0, width / 2 - frameW / 2);
			frameY = Math.max(0, height / 2 - frameH / 2);
		}
		// Bouton Sauvegarder
		addDrawableChild(ButtonWidget.builder(Text.literal("Sauvegarder"), btn -> saveAndClose())
			.dimensions(width / 2 - 50, height - 30, 100, 20)
			.build());
	}

	@Override
	public void close() {
		if (screenshotTexture != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(SCREENSHOT_ID);
			try {
				screenshotTexture.close();
			} catch (Exception ignored) {}
		}
		super.close();
	}

	private void saveAndClose() {
		config.trackerZoneX = Math.max(0, frameX);
		config.trackerZoneY = Math.max(0, frameY);
		config.trackerZoneW = Math.max(MIN_SIZE, frameW);
		config.trackerZoneH = Math.max(MIN_SIZE, frameH);
		Path path = BlocaQoLConfig.getConfigPath();
		boolean ok = config.save(path);
		if (client != null && client.player != null) {
			String msg = ok
				? "Zone sauvegardée: " + frameW + "x" + frameH + " à " + frameX + "," + frameY
				: "Erreur sauvegarde - voir logs";
			client.inGameHud.setOverlayMessage(Text.literal(msg), false);
		}
		close();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (screenshotTexture != null) {
			// Fond : capture d'écran mise à l'échelle (u,v en 0-1)
			context.drawTexturedQuad(SCREENSHOT_ID, 0, 0, width, height, 0, 1, 0, 1);
			// Assombrir légèrement pour mieux voir le cadran
			context.fill(0, 0, width, height, 0x40000000);
		} else {
			context.fill(0, 0, width, height, 0xFF101010);
		}

		// Cadran (bordure verte)
		drawFrame(context);

		// Instructions
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Déplacez le cadran sur la barre du minijeu • Molette pour redimensionner • Clic pour déplacer"),
			width / 2, 10, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.literal("Zone: " + frameW + " x " + frameH + "  |  Position: " + frameX + ", " + frameY),
			width / 2, 22, 0xAAAAAA);

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawFrame(DrawContext context) {
		// Bordure verte épaisse
		int c = 0xFF00FF00;
		context.fill(frameX - BORDER_THICKNESS, frameY - BORDER_THICKNESS,
			frameX + frameW + BORDER_THICKNESS, frameY, c);
		context.fill(frameX - BORDER_THICKNESS, frameY + frameH,
			frameX + frameW + BORDER_THICKNESS, frameY + frameH + BORDER_THICKNESS, c);
		context.fill(frameX - BORDER_THICKNESS, frameY, frameX, frameY + frameH, c);
		context.fill(frameX + frameW, frameY, frameX + frameW + BORDER_THICKNESS, frameY + frameH, c);
		// Intérieur semi-transparent
		context.fill(frameX, frameY, frameX + frameW, frameY + frameH, 0x2000FF00);
	}

	private int getResizeMode(int mouseX, int mouseY) {
		int left = frameX, right = frameX + frameW, top = frameY, bottom = frameY + frameH;
		boolean nearLeft = Math.abs(mouseX - left) <= RESIZE_HANDLE;
		boolean nearRight = Math.abs(mouseX - right) <= RESIZE_HANDLE;
		boolean nearTop = Math.abs(mouseY - top) <= RESIZE_HANDLE;
		boolean nearBottom = Math.abs(mouseY - bottom) <= RESIZE_HANDLE;
		boolean inside = mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;

		if (inside && !nearLeft && !nearRight && !nearTop && !nearBottom) return 0; // move
		if (nearTop && nearLeft) return 1;
		if (nearTop && nearRight) return 2;
		if (nearBottom && nearLeft) return 3;
		if (nearBottom && nearRight) return 4;
		if (nearTop) return 5;
		if (nearBottom) return 6;
		if (nearLeft) return 7;
		if (nearRight) return 8;
		if (inside) return 0;
		return -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// IMPORTANT: appeler super d'abord pour que le bouton Sauvegarder reçoive le clic
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (button != 0) return false;
		int mx = (int) mouseX, my = (int) mouseY;
		int mode = getResizeMode(mx, my);
		if (mode == 0) {
			dragging = true;
			dragOffsetX = mx - frameX;
			dragOffsetY = my - frameY;
		} else if (mode >= 1) {
			resizeMode = mode;
		}
		return mode >= 0;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			dragging = false;
			resizeMode = 0;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button != 0) return false;
		int mx = (int) mouseX, my = (int) mouseY;

		if (dragging) {
			frameX = mx - dragOffsetX;
			frameY = my - dragOffsetY;
			clampFrame();
		} else if (resizeMode > 0) {
			applyResize(mx, my);
		}
		return true;
	}

	private void applyResize(int mouseX, int mouseY) {
		switch (resizeMode) {
			case 1 -> { frameW += frameX - mouseX; frameH += frameY - mouseY; frameX = mouseX; frameY = mouseY; }
			case 2 -> { frameW = mouseX - frameX; frameH += frameY - mouseY; frameY = mouseY; }
			case 3 -> { frameW += frameX - mouseX; frameH = mouseY - frameY; frameX = mouseX; }
			case 4 -> { frameW = mouseX - frameX; frameH = mouseY - frameY; }
			case 5 -> { frameH += frameY - mouseY; frameY = mouseY; }
			case 6 -> frameH = mouseY - frameY;
			case 7 -> { frameW += frameX - mouseX; frameX = mouseX; }
			case 8 -> frameW = mouseX - frameX;
		}
		if (frameW < MIN_SIZE) { frameX -= (MIN_SIZE - frameW); frameW = MIN_SIZE; }
		if (frameH < MIN_SIZE) { frameY -= (MIN_SIZE - frameH); frameH = MIN_SIZE; }
		clampFrame();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount == 0) return false;
		int mx = (int) mouseX, my = (int) mouseY;
		if (mx >= frameX && mx <= frameX + frameW && my >= frameY && my <= frameY + frameH) {
			int delta = verticalAmount > 0 ? 4 : -4;
			frameW = Math.max(MIN_SIZE, Math.min(200, frameW + delta));
			frameH = Math.max(MIN_SIZE, Math.min(200, frameH + delta));
			// Garder le centre
			frameX -= delta / 2;
			frameY -= delta / 2;
			clampFrame();
		}
		return true;
	}

	private void clampFrame() {
		frameX = Math.max(0, Math.min(width - frameW, frameX));
		frameY = Math.max(0, Math.min(height - frameH, frameY));
	}

	@Override
	public boolean shouldPause() {
		return true;
	}
}
