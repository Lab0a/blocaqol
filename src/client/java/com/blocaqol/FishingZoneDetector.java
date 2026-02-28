package com.blocaqol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Tracking visuel temps réel : fish_y, bar_y.
 * error = fish_y - bar_y (normalisé 0-1, 0=haut, 1=bas)
 * hold si error < -tolerance, release si error > tolerance
 */
public final class FishingZoneDetector {

	private static long lastSampleTime = 0;
	private static boolean screenshotPending = false;

	private static volatile Float fishY = null;
	private static volatile Float barY = null;
	private static volatile boolean minigameVisible = false;
	private static long sampleTime = 0;
	private static float fishVelocity = 0;
	private static long prevSampleTime = 0;

	/** Position normalisée 0-1 (0=haut). Null si minijeu non visible. */
	public static Float getFishY() {
		return minigameVisible && fishY != null ? fishY : null;
	}

	/** Poisson avec prédiction pour poissons rapides (épique/légendaire). */
	public static Float getPredictedFishY() {
		if (!minigameVisible || fishY == null) return null;
		float predict = BlocaQoLClient.config != null && BlocaQoLClient.config.fishingAdaptive
			? FishingAdaptiveController.getPredict() : (BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingPredict : 0.4f);
		int sampleMs = BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingSampleMs : 16;
		float pred = fishY + fishVelocity * (sampleMs / 1000f * predict);
		return Math.max(0, Math.min(1, pred));
	}

	public static Float getBarY() {
		return minigameVisible && barY != null ? barY : null;
	}

	public static float getFishVelocity() {
		return fishVelocity;
	}

	public static boolean isMinigameVisible() {
		return minigameVisible && System.currentTimeMillis() - sampleTime < 300;
	}

	public static void tick(MinecraftClient client) {
		if (BlocaQoLClient.config == null) return;
		if (BlocaQoLClient.config.trackerZoneX < 0 || BlocaQoLClient.config.trackerZoneY < 0) return;
		if (BlocaQoLClient.config.trackerZoneW <= 0 || BlocaQoLClient.config.trackerZoneH <= 0) return;

		int sampleMs = BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingSampleMs : 16;
		long now = System.currentTimeMillis();
		if (now - lastSampleTime < sampleMs) return;
		if (screenshotPending) return;

		Framebuffer fb = client.getFramebuffer();
		if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) return;

		int scale = (int) client.getWindow().getScaleFactor();
		int winW = client.getWindow().getFramebufferWidth();
		int winH = client.getWindow().getFramebufferHeight();
		int w = Math.min(BlocaQoLClient.config.trackerZoneW, 128);
		int h = Math.min(BlocaQoLClient.config.trackerZoneH, 256);
		int readW = w * scale;
		int readH = h * scale;
		int x = BlocaQoLClient.config.trackerZoneX * scale;
		int glY = winH - (BlocaQoLClient.config.trackerZoneY + h) * scale;
		x = Math.max(0, Math.min(x, winW - readW));
		glY = Math.max(0, Math.min(glY, winH - readH));

		final int fx = x, fgy = glY, fw = readW, fh = readH;
		screenshotPending = true;
		lastSampleTime = now;

		ScreenshotRecorder.takeScreenshot(fb, img -> {
			client.execute(() -> {
				screenshotPending = false;
				try {
					int imgY = img.getHeight() - fgy - fh;
					if (imgY < 0 || fx + fw > img.getWidth() || imgY + fh > img.getHeight()) return;
					TrackingResult r = analyzeZone(img, fx, imgY, fw, fh);
					minigameVisible = r.visible;
					if (r.fishY != null && fishY != null && prevSampleTime > 0) {
						float dt = (now - prevSampleTime) / 1000f;
						if (dt > 0.001f) fishVelocity = (r.fishY - fishY) / dt;
					} else {
						fishVelocity = 0;
					}
					fishY = r.fishY;
					barY = r.barY;
					prevSampleTime = sampleTime;
					sampleTime = now;
				} catch (Exception e) {
					minigameVisible = false;
					fishY = null;
					barY = null;
				} finally {
					img.close();
				}
			});
		});
	}

	private static class TrackingResult {
		boolean visible;
		Float fishY;
		Float barY;
	}

	private static TrackingResult analyzeZone(NativeImage img, int baseX, int baseY, int w, int h) {
		TrackingResult r = new TrackingResult();
		r.visible = false;
		r.fishY = null;
		r.barY = null;

		int brownCount = 0;
		long greenSumY = 0;
		int greenCount = 0;
		long fishSumY = 0;
		int fishCount = 0;

		for (int row = 1; row < h - 1; row++) {
			for (int col = 1; col < w - 1; col++) {
				int c = img.getColorArgb(baseX + col, baseY + row);
				if (isBrown(c)) brownCount++;
				if (isGreenBar(c)) {
					greenSumY += row;
					greenCount++;
				}
				if ((isFishInZoneColor(c) || isFishOutZoneColor(c)) && hasWhiteNeighbor(img, baseX, baseY, w, h, col, row)) {
					fishSumY += row;
					fishCount++;
				}
			}
		}

		boolean hasFrame = brownCount >= 20;
		boolean hasBar = greenCount >= 15;
		boolean hasFish = fishCount >= 2;

		r.visible = hasFrame && hasBar && hasFish;

		if (r.visible && fishCount > 0) {
			r.fishY = (float) fishSumY / fishCount / h;
		}
		if (r.visible && greenCount > 0) {
			r.barY = (float) greenSumY / greenCount / h;
		}
		return r;
	}

	private static boolean hasWhiteNeighbor(NativeImage img, int baseX, int baseY, int w, int h, int col, int row) {
		for (int dy = -2; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				if (dx == 0 && dy == 0) continue;
				int ny = row + dy, nx = col + dx;
				if (ny < 0 || ny >= h || nx < 0 || nx >= w) continue;
				if (isWhite(img.getColorArgb(baseX + nx, baseY + ny))) return true;
			}
		}
		return false;
	}

	private static boolean isWhite(int c) {
		int a = (c >> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		return a > 250 && r > 250 && g > 250 && b > 250;
	}

	private static boolean isBrown(int c) {
		int a = (c >> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		return a > 200 && r > 70 && g > 40 && b < 120 && r > b;
	}

	private static boolean isGreenBar(int c) {
		int a = (c >> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		return a > 200 && g > 120 && g > r && g > b && b < 130;
	}

	private static boolean isFishInZoneColor(int c) {
		int a = (c >> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		if (a < 180) return false;
		if (g > 180 && b < 130 && (g - b) > 50) return true;
		if (r > 220 && g > 200 && b < 150) return true;
		if (r < 60 && g > 75 && g < 155 && b > 80 && b < 155 && Math.abs(g - b) < 30) return true;
		return false;
	}

	private static boolean isFishOutZoneColor(int c) {
		int a = (c >> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
		if (a < 220) return false;
		if (b > 90 && r < 55 && g < 70) return true;
		if (g > 180 && b > 180 && Math.abs(g - b) < 35) return true;
		if (r > 170 && g > 220 && b > 200 && (b - r) > 15) return true;
		return false;
	}
}
