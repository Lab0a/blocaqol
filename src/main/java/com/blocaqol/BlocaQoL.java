package com.blocaqol;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlocaQoL implements ModInitializer {
	public static final String MOD_ID = "blocaqol";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("BlocaQoL initialisé ! Prêt pour Minecraft 1.21.8");
	}
}
