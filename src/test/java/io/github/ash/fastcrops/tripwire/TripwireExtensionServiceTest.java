package io.github.ash.fastcrops.tripwire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TripwireExtensionServiceTest {
    @Test
    void extendedDistanceQualificationUsesVanillaBoundaryAndConfiguredCap() {
        assertFalse(TripwireRules.isExtendedDistanceSupported(40, 69));
        assertTrue(TripwireRules.isExtendedDistanceSupported(41, 69));
        assertTrue(TripwireRules.isExtendedDistanceSupported(69, 69));
        assertFalse(TripwireRules.isExtendedDistanceSupported(70, 69));
    }

    @Test
    void powerWindowExtensionKeepsLatestExpiry() {
        assertEquals(120L, TripwireRules.extendPowerExpiry(Long.MIN_VALUE, 120L));
        assertEquals(130L, TripwireRules.extendPowerExpiry(130L, 125L));
        assertEquals(145L, TripwireRules.extendPowerExpiry(130L, 145L));
    }
}
