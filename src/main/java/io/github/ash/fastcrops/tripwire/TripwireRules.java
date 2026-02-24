package io.github.ash.fastcrops.tripwire;

public final class TripwireRules {
    public static final int VANILLA_TRIPWIRE_MAX_LENGTH = 40;
    public static final int POWER_DURATION_TICKS = 10;

    private TripwireRules() {
    }

    public static boolean isExtendedDistanceSupported(int distance, int configuredMaxLength) {
        return distance > VANILLA_TRIPWIRE_MAX_LENGTH && distance <= configuredMaxLength;
    }

    public static long extendPowerExpiry(long existingExpiry, long requestedExpiry) {
        return Math.max(existingExpiry, requestedExpiry);
    }
}
