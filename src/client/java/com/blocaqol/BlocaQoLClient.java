package com.blocaqol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.blocaqol.BlocaQoL;
import net.minecraft.client.option.KeyBinding;

import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlocaQoLClient implements ClientModInitializer {

	private static final ExecutorService ASYNC = Executors.newSingleThreadExecutor();
	public static BlocaQoLConfig config;
	private static KeyBinding loginKey;
	private static KeyBinding autoFishKey;
	private static KeyBinding calibrationKey;
	private static boolean fishingKeysRegistered;
	private static int connectedRefreshTicks;

	@Override
	public void onInitializeClient() {
		config = BlocaQoLConfig.load(BlocaQoLConfig.getConfigPath());

		DurabilityHud.register();
		AuthStatusHud.register();
		AutoFish.register();

		loginKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blocaqol.login",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			"category.blocaqol"
		));
		if (config != null && !config.authEnabled) {
			registerFishingKeys();
		}

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (config.authEnabled && !AuthManager.isAuthenticated()) {
				client.execute(() -> client.setScreen(new LoginScreen(config)));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && config != null && config.authEnabled && AuthManager.isAuthenticated()) {
				registerFishingKeysIfAllowed();
				if (++connectedRefreshTicks >= 1200) {
					connectedRefreshTicks = 0;
					ASYNC.execute(() -> {
						var list = AuthManager.fetchConnectedPlayers(config.authApiUrl);
						Util.getMainWorkerExecutor().execute(() -> {
							AuthManager.setConnectedPlayers(list);
							registerFishingKeysIfAllowed();
						});
					});
				}
			} else {
				connectedRefreshTicks = 0;
			}
			while (loginKey.wasPressed() && client.currentScreen == null) {
				client.setScreen(new LoginScreen(config));
			}
			if (autoFishKey != null) {
				while (autoFishKey.wasPressed() && client.currentScreen == null) {
					AutoFish.toggle();
					if (client.player != null) {
						boolean hasZone = config != null && config.trackerZoneX >= 0 && config.trackerZoneY >= 0 && config.trackerZoneW > 0 && config.trackerZoneH > 0;
						var msg = AutoFish.isEnabled()
							? net.minecraft.text.Text.literal("Auto-pêche " + (hasZone ? "minijeu" : "vanilla") + " activé").formatted(net.minecraft.util.Formatting.GREEN)
							: net.minecraft.text.Text.literal("Auto-pêche désactivé").formatted(net.minecraft.util.Formatting.GRAY);
						client.inGameHud.setOverlayMessage(msg, false);
					}
				}
			}
			if (calibrationKey != null) {
				while (calibrationKey.wasPressed() && client.currentScreen == null && config != null) {
					FishingCalibrationScreen.openWithScreenshot(config);
				}
			}
		});
	}

	/** Enregistre F et J si l'utilisateur a la permission (auth désactivée ou allowAutofish). */
	public static void registerFishingKeysIfAllowed() {
		if (fishingKeysRegistered) return;
		boolean canRegister = config == null || !config.authEnabled || (AuthManager.isAuthenticated() && AuthManager.isAllowAutofish());
		if (!canRegister) return;
		registerFishingKeys();
	}

	private static void registerFishingKeys() {
		if (fishingKeysRegistered) return;
		autoFishKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blocaqol.autofish",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F,
			"category.blocaqol"
		));
		calibrationKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blocaqol.calibration",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_J,
			"category.blocaqol"
		));
		fishingKeysRegistered = true;
	}
}
