package com.blocaqol;

/**
 * Analyse en temps réel et ajuste tolerance/prédiction selon les performances.
 * Poisson qui échappe souvent → plus de prédiction. Oscillations → zone morte plus large.
 */
public final class FishingAdaptiveController {

	private static final int WINDOW = 48;
	private static final int ADJUST_INTERVAL = 12;

	private static final float[] errors = new float[WINDOW];
	private static final boolean[] inZone = new boolean[WINDOW];
	private static final boolean[] held = new boolean[WINDOW];
	private static int head = 0;
	private static int count = 0;

	private static float effectiveTolerance = 0.12f;
	private static float effectivePredict = 0.4f;
	private static int adjustCounter = 0;

	public static float getTolerance() {
		return effectiveTolerance;
	}

	public static float getPredict() {
		return effectivePredict;
	}

	/** wasInZone = poisson dans la zone verte (|error| petit), didHold = action prise */
	public static void feed(float error, boolean wasInZone, boolean didHold) {
		errors[head] = error;
		inZone[head] = wasInZone;
		held[head] = didHold;
		head = (head + 1) % WINDOW;
		if (count < WINDOW) count++;
		adjustCounter++;

		if (adjustCounter >= ADJUST_INTERVAL && count >= ADJUST_INTERVAL) {
			adjustCounter = 0;
			adjust();
		}
	}

	public static void reset() {
		count = 0;
		head = 0;
		effectiveTolerance = BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingTolerance : 0.12f;
		effectivePredict = BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingPredict : 0.4f;
	}

	private static void adjust() {
		float baseTolerance = BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingTolerance : 0.12f;
		float basePredict = BlocaQoLClient.config != null ? BlocaQoLClient.config.fishingPredict : 0.4f;

		float timeInZone = 0;
		int switches = 0;
		float avgError = 0;
		float maxAbsError = 0;

		for (int i = 0; i < count; i++) {
			int idx = Math.floorMod(head - 1 - i, WINDOW);
			if (inZone[idx]) timeInZone++;
			avgError += errors[idx];
			float ae = Math.abs(errors[idx]);
			if (ae > maxAbsError) maxAbsError = ae;
			if (i > 0) {
				int prevIdx = Math.floorMod(head - 2 - i, WINDOW);
				if (held[idx] != held[prevIdx]) switches++;
			}
		}
		timeInZone /= count;
		avgError /= count;
		float switchRate = count > 1 ? (float) switches / (count - 1) : 0;

		float velocity = Math.abs(FishingZoneDetector.getFishVelocity());
		boolean fastFish = velocity > 2f;

		if (timeInZone < 0.4f || fastFish) {
			effectivePredict = Math.min(0.8f, basePredict + 0.15f);
		} else if (timeInZone > 0.7f) {
			effectivePredict = Math.max(0.2f, basePredict - 0.05f);
		} else {
			effectivePredict = basePredict;
		}

		if (switchRate > 0.25f) {
			effectiveTolerance = Math.min(0.2f, baseTolerance + 0.04f);
		} else if (timeInZone < 0.35f && maxAbsError > 0.15f) {
			effectiveTolerance = Math.max(0.06f, baseTolerance - 0.02f);
		} else {
			effectiveTolerance = baseTolerance;
		}
	}
}
