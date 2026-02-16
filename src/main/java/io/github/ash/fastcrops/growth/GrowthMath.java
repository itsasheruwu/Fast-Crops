package io.github.ash.fastcrops.growth;

import java.util.random.RandomGenerator;

public final class GrowthMath {
    private GrowthMath() {
    }

    public static double multiplier(double targetTickSpeed, int vanillaRandomTickSpeed) {
        if (vanillaRandomTickSpeed <= 0) {
            return 1.0D;
        }
        return targetTickSpeed / (double) vanillaRandomTickSpeed;
    }

    public static int extraAttemptsPerRun(
            double targetTickSpeed,
            int vanillaRandomTickSpeed,
            int intervalTicks,
            RandomGenerator random
    ) {
        int sanitizedInterval = Math.max(1, intervalTicks);
        double effectiveMultiplier = Math.max(0.0D, multiplier(targetTickSpeed, vanillaRandomTickSpeed));
        double extraPerRun = Math.max(0.0D, (effectiveMultiplier - 1.0D) * sanitizedInterval);

        int baseExtra = (int) Math.floor(extraPerRun);
        double fractional = extraPerRun - baseExtra;

        int attempts = baseExtra;
        if (fractional > 0.0D && random.nextDouble() < fractional) {
            attempts++;
        }

        return Math.max(0, attempts);
    }
}
