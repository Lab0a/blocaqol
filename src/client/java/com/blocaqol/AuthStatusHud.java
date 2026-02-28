package com.blocaqol;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.List;

/**
 * Affiche le skin du joueur + "Connecté" et la liste des autres joueurs connectés au mod.
 */
public class AuthStatusHud {

	private static final Identifier HUD_ID = Identifier.of(BlocaQoL.MOD_ID, "auth_status");
	private static final int HEAD_SIZE = 16;
	private static final int PADDING = 4;
	private static final int ROW_HEIGHT = 18;

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

		// Liste : moi en premier, puis les autres (sans doublon)
		List<String> all = new java.util.ArrayList<>();
		all.add(username);
		for (String o : AuthManager.getConnectedPlayers()) {
			if (!o.equals(username) && !all.contains(o)) all.add(o);
		}

		int screenWidth = client.getWindow().getScaledWidth();
		int maxTextW = 0;
		for (String name : all) {
			int w = client.textRenderer.getWidth("✓ Connecté " + name);
			if (w > maxTextW) maxTextW = w;
		}
		int boxW = PADDING * 2 + HEAD_SIZE + 4 + maxTextW;
		int boxH = PADDING * 2 + all.size() * ROW_HEIGHT;
		int x = screenWidth - boxW - 6;
		int y = 6;

		context.fill(x - 2, y - 2, x + boxW + 2, y + boxH + 2, 0x80000000);

		int rowY = y + PADDING;
		for (String name : all) {
			SkinTextures textures = name.equals(username)
				? client.getSkinProvider().getSkinTextures(client.player.getGameProfile())
				: PlayerSkinCache.getSkin(client, name);
			PlayerSkinDrawer.draw(context, textures, x + PADDING, rowY, HEAD_SIZE);
			Text line = Text.literal("✓ Connecté ").formatted(Formatting.GREEN).append(Text.literal(name).formatted(Formatting.WHITE));
			context.drawText(client.textRenderer, line, x + PADDING + HEAD_SIZE + 4, rowY + 4, ColorHelper.getArgb(255, 255, 255, 255), true);
			rowY += ROW_HEIGHT;
		}
	}
}
