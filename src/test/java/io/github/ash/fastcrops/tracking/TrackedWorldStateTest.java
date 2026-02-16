package io.github.ash.fastcrops.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TrackedWorldStateTest {
    @Test
    void packedPositionRoundTripsSignedCoordinates() {
        long packed = TrackedWorldState.packPosition(-12345, -64, 98765);

        assertEquals(-12345, TrackedWorldState.unpackX(packed));
        assertEquals(-64, TrackedWorldState.unpackY(packed));
        assertEquals(98765, TrackedWorldState.unpackZ(packed));
    }

    @Test
    void addIsDedupedAndRemoveWorks() {
        TrackedWorldState state = new TrackedWorldState();

        assertTrue(state.add(1, 70, 1));
        assertFalse(state.add(1, 70, 1));
        assertEquals(1, state.size());

        assertTrue(state.remove(1, 70, 1));
        assertFalse(state.remove(1, 70, 1));
        assertEquals(0, state.size());
    }

    @Test
    void chunkRemovalDropsOnlyMatchingChunkEntries() {
        TrackedWorldState state = new TrackedWorldState();
        state.add(1, 65, 1);      // chunk 0,0
        state.add(17, 65, 1);     // chunk 1,0
        state.add(2, 70, 2);      // chunk 0,0

        state.removeChunk(0, 0);

        assertEquals(1, state.size());
        OptionalLong next = state.nextPosition();
        assertTrue(next.isPresent());
        assertEquals(17, TrackedWorldState.unpackX(next.getAsLong()));
    }
}
