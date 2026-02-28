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

		SkinTextures textures = client.getSkinProvider().getSkinTextures(client.player.getGameProfile());
		Text connecteText = Text.literal("✓ Connecté").formatted(Formatting.GREEN);

		int screenWidth = client.getWindow().getScaledWidth();
		int textW = client.textRenderer.getWidth(connecteText);
		int boxW = PADDING * 2 + HEAD_SIZE + 4 + textW;
		int boxH = PADDING * 2 + HEAD_SIZE;

		List<String> others = AuthManager.getConnectedPlayers();
		if (!others.isEmpty()) {
			boxH += 4 + others.size() * ROW_HEIGHT;
		}

		int x = screenWidth - boxW - 6;
		int y = 6;

		// Fond semi-transparent
		context.fill(x - 2, y - 2, x + boxW + 2, y + boxH + 2, 0x80000000);

		// Skin du joueur + "Connecté"
		PlayerSkinDrawer.draw(context, textures, x + PADDING, y + PADDING, HEAD_SIZE);
		context.drawText(client.textRenderer, connecteText, x + PADDING + HEAD_SIZE + 4, y + PADDING + 4, ColorHelper.getArgb(255, 255, 255, 255), true);

		// Autres joueurs connectés
		int rowY = y + PADDING + HEAD_SIZE + 4;
		for (String other : others) {
			if (other.equals(username)) continue;
			SkinTextures otherTextures = getSkinForUsername(client, other);
			PlayerSkinDrawer.draw(context, otherTextures, x + PADDING, rowY, 14);
			context.drawText(client.textRenderer, Text.literal(other).formatted(Formatting.GRAY), x + PADDING + 16, rowY + 3, 0xAAAAAA, true);
			rowY += ROW_HEIGHT;
		}
	}

	private static SkinTextures getSkinForUsername(MinecraftClient client, String name) {
		com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)), name);
		return client.getSkinProvider().getSkinTextures(profile);
	}
}
