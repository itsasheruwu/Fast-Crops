package io.github.ash.fastcrops.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class GrowthMathTest {
    @Test
    void multiplierUsesConfiguredRatio() {
        assertEquals(100.0D / 3.0D, GrowthMath.multiplier(100.0D, 3), 1.0e-9);
        assertEquals(1.0D, GrowthMath.multiplier(100.0D, 0), 1.0e-9);
    }

    @Test
    void extraAttemptsForDefaultSettingsMatchesRandomTickMath() {
        int attempts = GrowthMath.extraAttemptsPerRun(100.0D, 3, 2, new Random(7L));
        assertTrue(attempts == 0 || attempts == 1);
    }

    @Test
    void noExtraAttemptsWhenTargetAtOrBelowVanilla() {
        assertEquals(0, GrowthMath.extraAttemptsPerRun(3.0D, 3, 2, new Random(1L)));
        assertEquals(0, GrowthMath.extraAttemptsPerRun(1.0D, 3, 2, new Random(1L)));
    }

    @Test
    void veryHighTargetCanProduceMultipleAttempts() {
        int attempts = GrowthMath.extraAttemptsPerRun(10000.0D, 3, 2, new Random(2L));
        assertTrue(attempts >= 4);
    }

    @Test
    void coverageCompensationIncreasesAttemptsForSparseProcessing() {
        int base = GrowthMath.extraAttemptsPerProcessedBlock(1000.0D, 3, 2.0D, new Random(10L));
        int compensated = GrowthMath.extraAttemptsPerProcessedBlock(1000.0D, 3, 20.0D, new Random(10L));
        assertTrue(compensated >= base);
    }
}
