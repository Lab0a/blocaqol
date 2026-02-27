package com.blocaqol;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DurabilityHud {

	private static final Identifier HUD_ID = Identifier.of(BlocaQoL.MOD_ID, "durability_hud");
	private static final int CACHE_TICKS = 20; // Mise à jour toutes les 20 ticks (~1 sec) pour éviter le lag
	// Pattern pour "X durabilités restantes" (X peut contenir espaces/séparateurs: 17 851, 17,851)
	private static final Pattern DURABILITE_RESTANTES_PATTERN = Pattern.compile("([\\d\\s,]+)\\s*durabilités?\\s*restantes?");

	// Cache pour éviter getTooltip() à chaque frame (très coûteux)
	private static String cachedText;
	private static int cachedColor;
	private static long lastCacheTick = -1;
	private static ItemStack lastCachedStack = ItemStack.EMPTY;

	public static void register() {
		HudElementRegistry.attachElementBefore(
			net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
			HUD_ID,
			DurabilityHud::render
		);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) return;

		// Ne pas afficher si auth activée et non connecté
		if (BlocaQoLClient.config != null && BlocaQoLClient.config.authEnabled && !AuthManager.isAuthenticated()) {
			return;
		}

		ItemStack stack = client.player.getMainHandStack();
		if (stack.isEmpty()) {
			cachedText = null;
			lastCachedStack = ItemStack.EMPTY;
			return;
		}

		long currentTick = client.world != null ? client.world.getTime() : 0;
		boolean cacheValid = lastCachedStack == stack && currentTick - lastCacheTick < CACHE_TICKS;

		if (!cacheValid) {
			String durabilityText = getDurabilityText(stack);
			if (durabilityText == null) {
				cachedText = null;
				return;
			}
			cachedText = durabilityText;
			cachedColor = getDurabilityColor(stack);
			lastCachedStack = stack;
			lastCacheTick = currentTick;
		}

		if (cachedText == null) return;

		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();
		int x = screenWidth / 2 - 20;
		int y = screenHeight - 50;

		// Fond semi-transparent
		int textWidth = client.textRenderer.getWidth(cachedText);
		context.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0x80000000);

		// Texte (ARGB pour 1.21.6+)
		context.drawText(client.textRenderer, cachedText, x, y, cachedColor, true);
	}

	private static String getDurabilityText(ItemStack stack) {
		// PRIORITÉ 1 : Outils custom avec "X durabilités restantes" dans le tooltip
		// (les items custom peuvent être basés sur netherite et avoir les deux)
		Integer customRemaining = getCustomDurability(stack);
		if (customRemaining != null) {
			return customRemaining + " durabilités restantes";
		}

		// PRIORITÉ 2 : Outils vanilla avec durabilité
		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null && maxDamage > 0) {
			int damage = stack.getOrDefault(DataComponentTypes.DAMAGE, 0);
			int remaining = maxDamage - damage;
			return remaining + " / " + maxDamage;
		}

		return null;
	}

	/** Lit la durabilité des outils custom : lore/tooltip (pattern "X durabilités restantes") ou CUSTOM_DATA. */
	private static Integer getCustomDurability(ItemStack stack) {
		// 1. Chercher dans CUSTOM_DATA (NBT) - clés courantes des mods/serveurs
		Integer fromNbt = getDurabilityFromCustomData(stack);
		if (fromNbt != null) return fromNbt;

		// 2. Parser le lore/tooltip pour "X durabilités restantes"
		return getDurabilityFromLore(stack);
	}

	private static Integer getDurabilityFromCustomData(ItemStack stack) {
		var customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null) return null;

		var nbt = customData.copyNbt();
		// Clés courantes pour la durabilité dans les mods/serveurs
		String[] keys = {"Durability", "durability", "RemainingDurability", "durabilite", "Durabilite", "remaining"};
		for (String key : keys) {
			if (nbt.contains(key)) {
				int value = nbt.getInt(key, 0);
				return value >= 0 ? value : null;
			}
		}
		return null;
	}

	private static Integer getDurabilityFromLore(ItemStack stack) {
		// 1. LORE component (rapide)
		LoreComponent lore = stack.get(DataComponentTypes.LORE);
		if (lore != null) {
			for (Text line : lore.lines()) {
				Integer parsed = parseDurabiliteRestantes(line.getString());
				if (parsed != null) return parsed;
			}
		}

		// 2. Tooltip complet (coûteux - appelé seulement si LORE n'a rien)
		return getDurabilityFromTooltip(stack);
	}

	/** Récupère le tooltip tel qu'affiché (inclut les lignes générées par appendTooltip). */
	private static Integer getDurabilityFromTooltip(ItemStack stack) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return null;

		var lines = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
		for (Text line : lines) {
			Integer parsed = parseDurabiliteRestantes(line.getString());
			if (parsed != null) return parsed;
		}
		return null;
	}

	private static Integer parseDurabiliteRestantes(String text) {
		Matcher matcher = DURABILITE_RESTANTES_PATTERN.matcher(text);
		if (matcher.find()) {
			try {
				String numStr = matcher.group(1).replaceAll("[\\s,]", "");
				return Integer.parseInt(numStr);
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}

	private static int getDurabilityColor(ItemStack stack) {
		double ratio = getDurabilityRatio(stack);
		if (ratio > 0.5) return ColorHelper.getArgb(255, 85, 255, 85);   // Vert
		if (ratio > 0.25) return ColorHelper.getArgb(255, 255, 255, 85); // Jaune
		return ColorHelper.getArgb(255, 255, 85, 85);                     // Rouge
	}

	private static double getDurabilityRatio(ItemStack stack) {
		Integer maxDamage = stack.get(DataComponentTypes.MAX_DAMAGE);
		if (maxDamage != null && maxDamage > 0) {
			int damage = stack.getOrDefault(DataComponentTypes.DAMAGE, 0);
			return 1.0 - (double) damage / maxDamage;
		}

		Integer remaining = getCustomDurability(stack);
		if (remaining != null) {
			return remaining > 0 ? 1.0 : 0.0;
		}

		return 1.0;
	}
}
