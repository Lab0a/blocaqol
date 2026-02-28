package com.blocaqol;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Auto-fish : vanilla + minijeu custom.
 * Minijeu : tracking fish_y / bar_y, error = fish_y - bar_y.
 * hold si error < -tolerance, release si error > tolerance, zone morte sinon.
 */
public class AutoFish {

	private static boolean enabled = false;
	private static int reelTicks = 0;
	private static int castDelayTicks = 0;
	private static int castTicks = 0;
	private static boolean hadBobber = false;


	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean e) {
		enabled = e;
	}

	public static void toggle() {
		enabled = !enabled;
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled || client.player == null || client.world == null) return;
			if (BlocaQoLClient.config != null && BlocaQoLClient.config.authEnabled) {
				if (!AuthManager.isAuthenticated() || !AuthManager.isAllowAutofish()) {
					enabled = false;
					return;
				}
			}

			var stack = client.player.getStackInHand(Hand.MAIN_HAND);
			if (!stack.isOf(Items.FISHING_ROD)) return;

			boolean allowScreen = BlocaQoLClient.config != null && BlocaQoLClient.config.allowScreenOpenForFishing;
			if (client.currentScreen != null && !allowScreen) return;

			FishingBobberEntity bobber = client.player.fishHook;

			if (bobber != null && BlocaQoLClient.config != null) {
				FishingZoneDetector.tick(client);
			}

			if (FishingZoneDetector.isMinigameVisible() && bobber != null) {
				tickMinigame(client);
				return;
			}

			FishingAdaptiveController.reset();

			boolean useAttack = BlocaQoLClient.config != null && BlocaQoLClient.config.useAttackKeyForMinigame;
			if (useAttack) {
				client.options.attackKey.setPressed(false);
			}

			if (reelTicks > 0) {
				reelTicks--;
				if (reelTicks == 0) {
					client.options.useKey.setPressed(false);
					castDelayTicks = 8;
				}
				return;
			}

			if (castDelayTicks > 0) {
				castDelayTicks--;
				if (castDelayTicks == 0) {
					castTicks = 10;
					client.options.useKey.setPressed(true);
				}
				return;
			}
			if (castTicks > 0) {
				castTicks--;
				if (castTicks == 0) {
					client.options.useKey.setPressed(false);
				}
				return;
			}

			if (bobber != null) {
				hadBobber = true;
				if (bobber.caughtFish) {
					client.options.useKey.setPressed(true);
					reelTicks = 2;
				}
			} else {
				if (castDelayTicks == 0 && castTicks == 0) {
					boolean justReeled = hadBobber;
					hadBobber = false;
					castDelayTicks = justReeled ? 8 : 10;
				}
			}
		});
	}

	private static boolean lastHold = false;

	private static void tickMinigame(MinecraftClient client) {
		Float fishY = FishingZoneDetector.getPredictedFishY();
		Float barY = FishingZoneDetector.getBarY();

		if (fishY == null || barY == null) {
			applyHold(client, false);
			return;
		}

		float error = fishY - barY;
		float tolerance = BlocaQoLClient.config != null && BlocaQoLClient.config.fishingAdaptive
			? FishingAdaptiveController.getTolerance() : (BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingTolerance : 0.12f);

		boolean wasInZone = Math.abs(error) < 0.12f;
		boolean shouldHold = error < -tolerance;

		if (BlocaQoLClient.config != null && BlocaQoLClient.config.fishingAdaptive) {
			FishingAdaptiveController.feed(error, wasInZone, shouldHold);
		}
		lastHold = shouldHold;

		applyHold(client, shouldHold);
	}

	private static void applyHold(MinecraftClient client, boolean shouldHold) {
		boolean useAttack = BlocaQoLClient.config != null && BlocaQoLClient.config.useAttackKeyForMinigame;
		if (useAttack) {
			client.options.attackKey.setPressed(shouldHold);
			client.options.useKey.setPressed(false);
		} else {
			client.options.useKey.setPressed(shouldHold);
			client.options.attackKey.setPressed(false);
		}
	}
}
