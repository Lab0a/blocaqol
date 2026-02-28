package com.blocaqol;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.UnknownCustomPayload;

/**
 * Tracker basé sur les paquets custom du serveur.
 * En mode debug, log les IDs des paquets reçus pour identifier le channel du minijeu.
 */
public final class FishingPacketTracker {

	private static volatile Boolean fishInZoneFromPacket = null;
	private static long lastPacketTime = 0;
	private static final long PACKET_TIMEOUT_MS = 500;

	public static Boolean getFishInZone() {
		if (fishInZoneFromPacket != null && System.currentTimeMillis() - lastPacketTime < PACKET_TIMEOUT_MS) {
			return fishInZoneFromPacket;
		}
		return null;
	}

	public static void setFishInZone(boolean inZone) {
		fishInZoneFromPacket = inZone;
		lastPacketTime = System.currentTimeMillis();
	}

	public static void onUnknownPayload(UnknownCustomPayload payload) {
	}

	public static void tryParseFishingPayload(CustomPayload payload) {
	}

	public static void reset() {
		fishInZoneFromPacket = null;
	}
}
