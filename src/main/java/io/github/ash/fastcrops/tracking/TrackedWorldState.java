package io.github.ash.fastcrops.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;

public final class TrackedWorldState {
    private static final long X_MASK = 0x3ffffffL;
    private static final long Y_MASK = 0xfffL;
    private static final long Z_MASK = 0x3ffffffL;

    private final ArrayList<Long> orderedPositions = new ArrayList<>();
    private final HashMap<Long, Integer> indexByPosition = new HashMap<>();
    private final HashMap<Long, HashSet<Long>> positionsByChunk = new HashMap<>();

    private int cursor = 0;

    public int size() {
        return orderedPositions.size();
    }

    public boolean add(int x, int y, int z) {
        long position = packPosition(x, y, z);
        if (indexByPosition.containsKey(position)) {
            return false;
        }

        int index = orderedPositions.size();
        orderedPositions.add(position);
        indexByPosition.put(position, index);

        long chunkKey = packChunk(x >> 4, z >> 4);
        positionsByChunk.computeIfAbsent(chunkKey, key -> new HashSet<>()).add(position);
        return true;
    }

    public boolean remove(int x, int y, int z) {
        return removePosition(packPosition(x, y, z));
    }

    public boolean removePosition(long position) {
        Integer index = indexByPosition.remove(position);
        if (index == null) {
            return false;
        }

        removeAtIndex(index);

        int blockX = unpackX(position);
        int blockZ = unpackZ(position);
        long chunkKey = packChunk(blockX >> 4, blockZ >> 4);
        Set<Long> positions = positionsByChunk.get(chunkKey);
        if (positions != null) {
            positions.remove(position);
            if (positions.isEmpty()) {
                positionsByChunk.remove(chunkKey);
            }
        }

        return true;
    }

    public void removeChunk(int chunkX, int chunkZ) {
        long chunkKey = packChunk(chunkX, chunkZ);
        Set<Long> positions = positionsByChunk.remove(chunkKey);
        if (positions == null || positions.isEmpty()) {
            return;
        }

        for (long position : positions) {
            Integer index = indexByPosition.remove(position);
            if (index != null) {
                removeAtIndex(index);
            }
        }
    }

    private void removeAtIndex(int index) {
        int lastIndex = orderedPositions.size() - 1;
        long lastPosition = orderedPositions.get(lastIndex);

        if (index != lastIndex) {
            orderedPositions.set(index, lastPosition);
            indexByPosition.put(lastPosition, index);
        }

        orderedPositions.remove(lastIndex);

        if (orderedPositions.isEmpty()) {
            cursor = 0;
            return;
        }

        if (index < cursor) {
            cursor--;
        }

        if (cursor >= orderedPositions.size()) {
            cursor = 0;
        }
    }

    public OptionalLong nextPosition() {
        if (orderedPositions.isEmpty()) {
            return OptionalLong.empty();
        }

        if (cursor >= orderedPositions.size()) {
            cursor = 0;
        }

        long position = orderedPositions.get(cursor);
        cursor++;
        if (cursor >= orderedPositions.size()) {
            cursor = 0;
        }

        return OptionalLong.of(position);
    }

    public void clear() {
        orderedPositions.clear();
        indexByPosition.clear();
        positionsByChunk.clear();
        cursor = 0;
    }

    public static long packPosition(int x, int y, int z) {
        return ((x & X_MASK) << 38) | ((z & Z_MASK) << 12) | (y & Y_MASK);
    }

    public static int unpackX(long packed) {
        int value = (int) (packed >> 38);
        if ((value & 0x2000000) != 0) {
            value |= ~0x3ffffff;
        }
        return value;
    }

    public static int unpackY(long packed) {
        int value = (int) (packed & Y_MASK);
        if ((value & 0x800) != 0) {
            value |= ~0xfff;
        }
        return value;
    }

    public static int unpackZ(long packed) {
        int value = (int) ((packed >> 12) & Z_MASK);
        if ((value & 0x2000000) != 0) {
            value |= ~0x3ffffff;
        }
        return value;
    }

    public static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
