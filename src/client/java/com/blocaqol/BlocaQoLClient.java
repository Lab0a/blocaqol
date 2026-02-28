package com.blocaqol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
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

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (config.authEnabled && !AuthManager.isAuthenticated()) {
				client.execute(() -> client.setScreen(new LoginScreen(config)));
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (config != null && config.authEnabled) {
				AuthManager.notifyServerOnShutdown(config.authApiUrl);
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			if (config != null && config.authEnabled) {
				AuthManager.notifyServerOnShutdown(config.authApiUrl);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && config != null && config.authEnabled && AuthManager.isAuthenticated()) {
				if (++connectedRefreshTicks >= 1200) {
					connectedRefreshTicks = 0;
					ASYNC.execute(() -> {
						var list = AuthManager.fetchConnectedPlayers(config.authApiUrl);
						Util.getMainWorkerExecutor().execute(() -> AuthManager.setConnectedPlayers(list));
					});
				}
			} else {
				connectedRefreshTicks = 0;
			}
			while (loginKey.wasPressed() && client.currentScreen == null) {
				client.setScreen(new LoginScreen(config));
			}
			boolean canUseFishing = config == null || !config.authEnabled || (AuthManager.isAuthenticated() && AuthManager.isAllowAutofish());
			while (autoFishKey.wasPressed() && client.currentScreen == null) {
				if (canUseFishing) {
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
			while (calibrationKey.wasPressed() && client.currentScreen == null && config != null) {
				if (canUseFishing) {
					FishingCalibrationScreen.openWithScreenshot(config);
				}
			}
		});
	}
}
