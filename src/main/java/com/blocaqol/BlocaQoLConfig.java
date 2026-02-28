package com.blocaqol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration du mod - URL de l'API d'auth.
 */
public class BlocaQoLConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String authApiUrl = "http://62.210.244.167:3000";
	public boolean authEnabled = true;

	public boolean allowScreenOpenForFishing = true;
	public int trackerZoneX = -1;
	public int trackerZoneY = -1;
	public int trackerZoneW = 20;
	public int trackerZoneH = 80;
	public boolean invertZoneLogic = false;
	public boolean useAttackKeyForMinigame = false;
	public float fishingTolerance = 0.12f;
	public int fishingSampleMs = 16;
	public float fishingPredict = 0.3f;
	public boolean fishingAdaptive = true;

	public static BlocaQoLConfig load(Path path) {
		try {
			if (Files.exists(path)) {
				String json = Files.readString(path);
				return GSON.fromJson(json, BlocaQoLConfig.class);
			}
		} catch (IOException e) {
			BlocaQoL.LOGGER.warn("Erreur chargement config: {}", e.getMessage());
		}
		BlocaQoLConfig config = new BlocaQoLConfig();
		config.save(path);
		return config;
	}

	public boolean save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
			return true;
		} catch (IOException e) {
			BlocaQoL.LOGGER.warn("[BlocaQoL] Erreur sauvegarde config: {} - {}", path.toAbsolutePath(), e.getMessage());
			return false;
		}
	}

	public static Path getConfigPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(BlocaQoL.MOD_ID).resolve("config.json");
	}
}
