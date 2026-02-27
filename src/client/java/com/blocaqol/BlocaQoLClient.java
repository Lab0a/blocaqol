package com.blocaqol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class BlocaQoLClient implements ClientModInitializer {

	public static BlocaQoLConfig config;
	private static KeyBinding loginKey;

	@Override
	public void onInitializeClient() {
		config = BlocaQoLConfig.load(BlocaQoLConfig.getConfigPath());

		DurabilityHud.register();
		AuthStatusHud.register();

		loginKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blocaqol.login",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_B,
			"category.blocaqol"
		));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (config.authEnabled && !AuthManager.isAuthenticated()) {
				client.execute(() -> client.setScreen(new LoginScreen(config)));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (loginKey.wasPressed() && client.currentScreen == null) {
				client.setScreen(new LoginScreen(config));
			}
		});
	}
}
