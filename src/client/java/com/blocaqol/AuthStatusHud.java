package com.blocaqol;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ColorHelper;

/**
 * Affiche "Connecté : username" en jeu quand l'utilisateur est authentifié.
 */
public class AuthStatusHud {

	private static final Identifier HUD_ID = Identifier.of(BlocaQoL.MOD_ID, "auth_status");

	public static void register() {
		HudElementRegistry.attachElementBefore(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
			HUD_ID,
			AuthStatusHud::render
		);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) return;

		if (BlocaQoLClient.config == null || !BlocaQoLClient.config.authEnabled) return;
		if (!AuthManager.isAuthenticated()) return;

		String username = AuthManager.getUsername();
		if (username == null) return;

		Text displayText = Text.literal("✓ Connecté : ").formatted(Formatting.GREEN)
			.append(Text.literal(username).formatted(Formatting.WHITE));

		int screenWidth = client.getWindow().getScaledWidth();
		int w = client.textRenderer.getWidth(displayText);
		int x = screenWidth - w - 6;
		int y = 6;

		// Fond semi-transparent
		context.fill(x - 2, y - 2, x + w + 2, y + 10, 0x80000000);

		context.drawText(client.textRenderer, displayText, x, y, ColorHelper.getArgb(255, 255, 255, 255), true);
	}
}
