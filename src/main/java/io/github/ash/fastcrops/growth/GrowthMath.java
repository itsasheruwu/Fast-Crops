package io.github.ash.fastcrops.growth;

import java.util.random.RandomGenerator;

public final class GrowthMath {
    private static final int BLOCKS_PER_CHUNK_SECTION = 16 * 16 * 16;

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
        return extraAttemptsPerProcessedBlock(
                targetTickSpeed,
                vanillaRandomTickSpeed,
                Math.max(1, intervalTicks),
                random
        );
    }

    public static int extraAttemptsPerProcessedBlock(
            double targetTickSpeed,
            int vanillaRandomTickSpeed,
            double effectiveIntervalTicks,
            RandomGenerator random
    ) {
        double extraTickSpeed = Math.max(0.0D, targetTickSpeed - Math.max(0, vanillaRandomTickSpeed));
        double extraPerRun = (extraTickSpeed / BLOCKS_PER_CHUNK_SECTION) * Math.max(0.0D, effectiveIntervalTicks);

        int baseExtra = (int) Math.floor(extraPerRun);
        double fractional = extraPerRun - baseExtra;

        int attempts = baseExtra;
        if (fractional > 0.0D && random.nextDouble() < fractional) {
            attempts++;
        }

        return Math.max(0, attempts);
    }

    public static int blocksPerChunkSection() {
        return BLOCKS_PER_CHUNK_SECTION;
    }
}
