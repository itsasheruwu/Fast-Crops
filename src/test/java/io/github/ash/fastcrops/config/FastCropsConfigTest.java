package io.github.ash.fastcrops.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FastCropsConfigTest {
    @Test
    void tripwireMaxLengthFallsBackToDefaultWhenInvalidOrMissing() {
        assertEquals(69, FastCropsConfig.sanitizeTripwireMaxLength(false, 0));
        assertEquals(69, FastCropsConfig.sanitizeTripwireMaxLength(false, 999));
    }

    @Test
    void tripwireMaxLengthClampsToConfiguredBounds() {
        assertEquals(40, FastCropsConfig.sanitizeTripwireMaxLength(true, 20));
        assertEquals(69, FastCropsConfig.sanitizeTripwireMaxLength(true, 69));
        assertEquals(256, FastCropsConfig.sanitizeTripwireMaxLength(true, 999));
    }
}
