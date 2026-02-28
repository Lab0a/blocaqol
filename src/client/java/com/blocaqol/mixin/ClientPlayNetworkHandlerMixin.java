package com.blocaqol.mixin;

import com.blocaqol.BlocaQoLClient;
import com.blocaqol.FishingPacketTracker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.UnknownCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepte les paquets custom pour détecter les données du minijeu de pêche.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onCustomPayload", at = @At("HEAD"))
	private void blocaqol$onCustomPayload(CustomPayload payload, CallbackInfo ci) {
		if (BlocaQoLClient.config == null) return;
		if (payload instanceof UnknownCustomPayload unknown) {
			FishingPacketTracker.onUnknownPayload(unknown);
		}
		FishingPacketTracker.tryParseFishingPayload(payload);
	}
}
