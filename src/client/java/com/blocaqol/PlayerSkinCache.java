package com.blocaqol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.SkinTextures;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cache les skins des joueurs en récupérant l'UUID via l'API Mojang.
 */
public class PlayerSkinCache {

	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
	private static final Map<String, SkinTextures> cache = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	public static SkinTextures getSkin(MinecraftClient client, String username) {
		SkinTextures cached = cache.get(username);
		if (cached != null) return cached;

		var skinProvider = client.getSkinProvider();
		SkinTextures defaultSkin = getDefaultSkin(skinProvider, username);
		EXECUTOR.execute(() -> fetchAndCache(skinProvider, username));
		return defaultSkin;
	}

	private static SkinTextures getDefaultSkin(net.minecraft.client.texture.PlayerSkinProvider skinProvider, String username) {
		com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(
			UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)), username);
		return skinProvider.getSkinTextures(profile);
	}

	private static void fetchAndCache(net.minecraft.client.texture.PlayerSkinProvider skinProvider, String username) {
		try {
			String url = "https://api.mojang.com/users/profiles/minecraft/" + username;
			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) return;

			JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
			String id = json.has("id") ? json.get("id").getAsString() : null;
			if (id == null) return;

			UUID uuid = parseUUID(id);
			if (uuid == null) return;

			com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, username);
			CompletableFuture<java.util.Optional<SkinTextures>> future = skinProvider.fetchSkinTextures(profile);
			future.thenAccept(opt -> opt.ifPresent(skin -> cache.put(username, skin)));
		} catch (Exception e) {
			BlocaQoL.LOGGER.debug("Skin fetch for {}: {}", username, e.getMessage());
		}
	}

	private static UUID parseUUID(String id) {
		if (id == null || id.length() != 32) return null;
		try {
			return new UUID(
				Long.parseUnsignedLong(id.substring(0, 16), 16),
				Long.parseUnsignedLong(id.substring(16, 32), 16)
			);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static void clear() {
		cache.clear();
	}
}
