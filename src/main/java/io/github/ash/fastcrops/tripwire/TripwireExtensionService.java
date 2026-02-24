package io.github.ash.fastcrops.tripwire;

import io.github.ash.fastcrops.FastCropsPlugin;
import io.github.ash.fastcrops.config.FastCropsConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.block.data.type.TripwireHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class TripwireExtensionService implements Listener {
    private enum Axis {
        X,
        Z
    }

    private static final BlockFace[] HORIZONTAL_FACES = new BlockFace[] {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };
    private static final Set<Material> TRIPWIRE_RELATED_MATERIALS =
            Collections.unmodifiableSet(EnumSet.of(Material.TRIPWIRE, Material.TRIPWIRE_HOOK));

    private final FastCropsPlugin plugin;
    private final FastCropsConfig config;

    private final Map<UUID, Map<LineKey, Long>> activeUntilByWorld = new HashMap<>();
    private final Map<UUID, Set<LineKey>> managedLinesByWorld = new HashMap<>();

    private int internalUpdates;

    public TripwireExtensionService(FastCropsPlugin plugin, FastCropsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        refreshAround(event.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        refreshAround(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        refreshAround(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        refreshAround(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        refreshAround(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.TRIPWIRE) {
            return;
        }

        triggerFromBlock(clicked);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.TRIPWIRE) {
            return;
        }

        triggerFromBlock(block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Block hitBlock = event.getHitBlock();
        if (hitBlock == null || hitBlock.getType() != Material.TRIPWIRE) {
            return;
        }

        triggerFromBlock(hitBlock);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();

        Set<LineKey> managed = managedLinesByWorld.get(worldId);
        if (managed != null) {
            managed.removeIf(key -> key.intersectsChunk(chunkX, chunkZ));
            if (managed.isEmpty()) {
                managedLinesByWorld.remove(worldId);
            }
        }

        Map<LineKey, Long> active = activeUntilByWorld.get(worldId);
        if (active != null) {
            active.entrySet().removeIf(entry -> entry.getKey().intersectsChunk(chunkX, chunkZ));
            if (active.isEmpty()) {
                activeUntilByWorld.remove(worldId);
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        managedLinesByWorld.remove(worldId);
        activeUntilByWorld.remove(worldId);
    }

    private void triggerFromBlock(Block tripwireBlock) {
        if (!config.isEnabled() || internalUpdates > 0) {
            return;
        }

        Set<TripwireLine> lines = resolveExtendedLinesThrough(tripwireBlock);
        if (lines.isEmpty()) {
            return;
        }

        for (TripwireLine line : lines) {
            triggerLine(line);
        }
    }

    private void refreshAround(Block changedBlock) {
        if (!config.isEnabled() || internalUpdates > 0) {
            return;
        }

        Material changedType = changedBlock.getType();
        if (!TRIPWIRE_RELATED_MATERIALS.contains(changedType) && !touchesTripwireNeighbor(changedBlock)) {
            return;
        }

        World world = changedBlock.getWorld();
        UUID worldId = world.getUID();

        Set<TripwireLine> resolvedLines = new HashSet<>();
        for (Block candidate : collectNearbyCandidates(changedBlock)) {
            resolvedLines.addAll(resolveExtendedLinesThrough(candidate));
        }

        Set<LineKey> resolvedKeys = new HashSet<>();
        Set<LineKey> managed = managedLinesByWorld.computeIfAbsent(worldId, ignored -> new HashSet<>());
        for (TripwireLine line : resolvedLines) {
            resolvedKeys.add(line.key());
            managed.add(line.key());
            boolean powered = isCurrentlyPowered(worldId, line.key());
            applyLineState(world, line.key(), true, powered);
        }

        removeInvalidManagedLines(world, changedBlock, resolvedKeys);
        pruneWorldState(worldId);
    }

    private boolean touchesTripwireNeighbor(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (TRIPWIRE_RELATED_MATERIALS.contains(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private List<Block> collectNearbyCandidates(Block origin) {
        List<Block> candidates = new ArrayList<>(1 + HORIZONTAL_FACES.length);
        candidates.add(origin);
        for (BlockFace face : HORIZONTAL_FACES) {
            candidates.add(origin.getRelative(face));
        }
        return candidates;
    }

    private Set<TripwireLine> resolveExtendedLinesThrough(Block origin) {
        Material type = origin.getType();
        if (!TRIPWIRE_RELATED_MATERIALS.contains(type)) {
            return Collections.emptySet();
        }

        int maxLength = config.getTripwireMaxLength();
        Set<TripwireLine> lines = new HashSet<>(2);
        TripwireLine xAxis = resolveLineOnAxis(origin, BlockFace.WEST, BlockFace.EAST, Axis.X, maxLength);
        TripwireLine zAxis = resolveLineOnAxis(origin, BlockFace.NORTH, BlockFace.SOUTH, Axis.Z, maxLength);

        if (xAxis != null) {
            lines.add(xAxis);
        }
        if (zAxis != null) {
            lines.add(zAxis);
        }

        return lines;
    }

    private TripwireLine resolveLineOnAxis(
            Block origin,
            BlockFace negative,
            BlockFace positive,
            Axis axis,
            int maxLength
    ) {
        HookSearch left = searchForHook(origin, negative, maxLength);
        HookSearch right = searchForHook(origin, positive, maxLength);
        if (!left.found || !right.found) {
            return null;
        }
        if (left.distance == 0 && right.distance == 0) {
            return null;
        }

        return resolveBetweenHooks(origin.getWorld(), left.hookX, left.hookY, left.hookZ, right.hookX, right.hookY, right.hookZ, axis);
    }

    private HookSearch searchForHook(Block origin, BlockFace direction, int maxLength) {
        for (int step = 0; step <= maxLength; step++) {
            Block cursor = origin.getRelative(direction, step);
            Material type = cursor.getType();

            if (step == 0) {
                if (type == Material.TRIPWIRE_HOOK) {
                    return HookSearch.found(cursor, 0);
                }
                if (type != Material.TRIPWIRE) {
                    return HookSearch.notFound();
                }
                continue;
            }

            if (type == Material.TRIPWIRE) {
                continue;
            }

            if (type == Material.TRIPWIRE_HOOK) {
                return HookSearch.found(cursor, step);
            }

            return HookSearch.notFound();
        }

        return HookSearch.notFound();
    }

    private TripwireLine resolveBetweenHooks(
            World world,
            int hookAX,
            int hookAY,
            int hookAZ,
            int hookBX,
            int hookBY,
            int hookBZ,
            Axis axis
    ) {
        if (hookAY != hookBY) {
            return null;
        }

        int xDistance = Math.abs(hookAX - hookBX);
        int zDistance = Math.abs(hookAZ - hookBZ);
        if ((axis == Axis.X && zDistance != 0) || (axis == Axis.Z && xDistance != 0)) {
            return null;
        }

        int distance = xDistance + zDistance;
        if (!TripwireRules.isExtendedDistanceSupported(distance, config.getTripwireMaxLength())) {
            return null;
        }

        if (!world.isChunkLoaded(hookAX >> 4, hookAZ >> 4) || !world.isChunkLoaded(hookBX >> 4, hookBZ >> 4)) {
            return null;
        }

        Block hookA = world.getBlockAt(hookAX, hookAY, hookAZ);
        Block hookB = world.getBlockAt(hookBX, hookBY, hookBZ);
        if (hookA.getType() != Material.TRIPWIRE_HOOK || hookB.getType() != Material.TRIPWIRE_HOOK) {
            return null;
        }

        BlockFace stepFace = stepFace(hookAX, hookAZ, hookBX, hookBZ);
        for (int step = 1; step < distance; step++) {
            Block wire = hookA.getRelative(stepFace, step);
            if (wire.getType() != Material.TRIPWIRE) {
                return null;
            }
        }

        return TripwireLine.of(world, axis, hookAX, hookAY, hookAZ, hookBX, hookBY, hookBZ, distance);
    }

    private void triggerLine(TripwireLine line) {
        UUID worldId = line.world().getUID();
        LineKey key = line.key();

        long now = plugin.getServer().getCurrentTick();
        long requestedExpiry = now + TripwireRules.POWER_DURATION_TICKS;

        Map<LineKey, Long> active = activeUntilByWorld.computeIfAbsent(worldId, ignored -> new HashMap<>());
        long mergedExpiry = TripwireRules.extendPowerExpiry(active.getOrDefault(key, Long.MIN_VALUE), requestedExpiry);
        active.put(key, mergedExpiry);

        managedLinesByWorld.computeIfAbsent(worldId, ignored -> new HashSet<>()).add(key);
        applyLineState(line.world(), key, true, true);

        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> depowerIfExpired(worldId, key, mergedExpiry),
                TripwireRules.POWER_DURATION_TICKS
        );
    }

    private void depowerIfExpired(UUID worldId, LineKey key, long expectedExpiry) {
        Map<LineKey, Long> active = activeUntilByWorld.get(worldId);
        if (active == null) {
            return;
        }

        Long actualExpiry = active.get(key);
        if (actualExpiry == null || actualExpiry.longValue() != expectedExpiry) {
            return;
        }

        long now = plugin.getServer().getCurrentTick();
        if (actualExpiry > now) {
            return;
        }

        active.remove(key);
        if (active.isEmpty()) {
            activeUntilByWorld.remove(worldId);
        }

        World world = plugin.getServer().getWorld(worldId);
        if (world == null) {
            managedLinesByWorld.remove(worldId);
            return;
        }

        TripwireLine line = resolveFromKey(world, key);
        if (line == null) {
            detachLine(world, key);
            removeManagedLine(worldId, key);
            return;
        }

        applyLineState(world, key, true, false);
    }

    private void removeInvalidManagedLines(World world, Block changedBlock, Set<LineKey> resolvedKeys) {
        UUID worldId = world.getUID();
        Set<LineKey> managed = managedLinesByWorld.get(worldId);
        if (managed == null || managed.isEmpty()) {
            return;
        }

        int x = changedBlock.getX();
        int y = changedBlock.getY();
        int z = changedBlock.getZ();

        List<LineKey> keys = new ArrayList<>(managed);
        for (LineKey key : keys) {
            if (!key.containsBlock(x, y, z)) {
                continue;
            }
            if (resolvedKeys.contains(key)) {
                continue;
            }

            TripwireLine stillValid = resolveFromKey(world, key);
            if (stillValid != null) {
                boolean powered = isCurrentlyPowered(worldId, key);
                applyLineState(world, key, true, powered);
                continue;
            }

            detachLine(world, key);
            removeActiveLine(worldId, key);
            managed.remove(key);
        }
    }

    private void applyLineState(World world, LineKey key, boolean attached, boolean powered) {
        if (!TripwireRules.isExtendedDistanceSupported(key.distance(), config.getTripwireMaxLength())) {
            return;
        }

        runInternalUpdate(() -> {
            Block hookA = getLoadedBlock(world, key.hookAX(), key.hookAY(), key.hookAZ());
            Block hookB = getLoadedBlock(world, key.hookBX(), key.hookBY(), key.hookBZ());
            if (hookA == null || hookB == null) {
                return;
            }
            if (hookA.getType() != Material.TRIPWIRE_HOOK || hookB.getType() != Material.TRIPWIRE_HOOK) {
                return;
            }

            BlockFace fromAToB = stepFace(key.hookAX(), key.hookAZ(), key.hookBX(), key.hookBZ());
            BlockFace fromBToA = fromAToB.getOppositeFace();

            applyHookState(hookA, fromAToB, attached, powered);
            applyHookState(hookB, fromBToA, attached, powered);

            for (int step = 1; step < key.distance(); step++) {
                int wireX = key.hookAX() + fromAToB.getModX() * step;
                int wireY = key.hookAY();
                int wireZ = key.hookAZ() + fromAToB.getModZ() * step;
                Block wire = getLoadedBlock(world, wireX, wireY, wireZ);
                if (wire == null || wire.getType() != Material.TRIPWIRE) {
                    continue;
                }
                applyTripwireState(wire, attached, powered);
            }

        });
    }

    private void detachLine(World world, LineKey key) {
        runInternalUpdate(() -> {
            BlockFace stepFace = stepFace(key.hookAX(), key.hookAZ(), key.hookBX(), key.hookBZ());

            Block hookA = getLoadedBlock(world, key.hookAX(), key.hookAY(), key.hookAZ());
            if (hookA != null && hookA.getType() == Material.TRIPWIRE_HOOK) {
                applyHookState(hookA, currentFacingOr(stepFace, hookA), false, false);
            }

            Block hookB = getLoadedBlock(world, key.hookBX(), key.hookBY(), key.hookBZ());
            if (hookB != null && hookB.getType() == Material.TRIPWIRE_HOOK) {
                applyHookState(hookB, currentFacingOr(stepFace.getOppositeFace(), hookB), false, false);
            }

            for (int step = 1; step < key.distance(); step++) {
                int wireX = key.hookAX() + stepFace.getModX() * step;
                int wireY = key.hookAY();
                int wireZ = key.hookAZ() + stepFace.getModZ() * step;
                Block wire = getLoadedBlock(world, wireX, wireY, wireZ);
                if (wire == null || wire.getType() != Material.TRIPWIRE) {
                    continue;
                }
                applyTripwireState(wire, false, false);
            }
        });
    }

    private BlockFace currentFacingOr(BlockFace fallback, Block hookBlock) {
        BlockData data = hookBlock.getBlockData();
        if (data instanceof Directional directional) {
            return directional.getFacing();
        }
        return fallback;
    }

    private void applyHookState(Block hookBlock, BlockFace facing, boolean attached, boolean powered) {
        BlockData data = hookBlock.getBlockData();
        if (!(data instanceof TripwireHook tripwireHook)) {
            return;
        }

        if (!(tripwireHook instanceof Powerable powerable)) {
            return;
        }

        boolean changed = false;
        if (tripwireHook.getFacing() != facing) {
            tripwireHook.setFacing(facing);
            changed = true;
        }
        if (tripwireHook.isAttached() != attached) {
            tripwireHook.setAttached(attached);
            changed = true;
        }
        if (powerable.isPowered() != powered) {
            powerable.setPowered(powered);
            changed = true;
        }

        if (changed) {
            hookBlock.setBlockData(tripwireHook, true);
        }
    }

    private void applyTripwireState(Block tripwireBlock, boolean attached, boolean powered) {
        BlockData data = tripwireBlock.getBlockData();
        if (!(data instanceof Tripwire tripwire)) {
            return;
        }

        if (!(tripwire instanceof Powerable powerable)) {
            return;
        }

        boolean changed = false;
        if (tripwire.isAttached() != attached) {
            tripwire.setAttached(attached);
            changed = true;
        }
        if (powerable.isPowered() != powered) {
            powerable.setPowered(powered);
            changed = true;
        }

        if (changed) {
            tripwireBlock.setBlockData(tripwire, true);
        }
    }

    private TripwireLine resolveFromKey(World world, LineKey key) {
        return resolveBetweenHooks(
                world,
                key.hookAX(),
                key.hookAY(),
                key.hookAZ(),
                key.hookBX(),
                key.hookBY(),
                key.hookBZ(),
                key.axis()
        );
    }

    private Block getLoadedBlock(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }

    private boolean isCurrentlyPowered(UUID worldId, LineKey key) {
        Map<LineKey, Long> active = activeUntilByWorld.get(worldId);
        if (active == null) {
            return false;
        }

        Long expiry = active.get(key);
        if (expiry == null) {
            return false;
        }

        long now = plugin.getServer().getCurrentTick();
        if (expiry <= now) {
            active.remove(key);
            if (active.isEmpty()) {
                activeUntilByWorld.remove(worldId);
            }
            return false;
        }

        return true;
    }

    private void removeManagedLine(UUID worldId, LineKey key) {
        Set<LineKey> managed = managedLinesByWorld.get(worldId);
        if (managed == null) {
            return;
        }
        managed.remove(key);
        if (managed.isEmpty()) {
            managedLinesByWorld.remove(worldId);
        }
    }

    private void removeActiveLine(UUID worldId, LineKey key) {
        Map<LineKey, Long> active = activeUntilByWorld.get(worldId);
        if (active == null) {
            return;
        }
        active.remove(key);
        if (active.isEmpty()) {
            activeUntilByWorld.remove(worldId);
        }
    }

    private void pruneWorldState(UUID worldId) {
        Set<LineKey> managed = managedLinesByWorld.get(worldId);
        if (managed != null && managed.isEmpty()) {
            managedLinesByWorld.remove(worldId);
        }

        Map<LineKey, Long> active = activeUntilByWorld.get(worldId);
        if (active != null && active.isEmpty()) {
            activeUntilByWorld.remove(worldId);
        }
    }

    private void runInternalUpdate(Runnable action) {
        internalUpdates++;
        try {
            action.run();
        } finally {
            internalUpdates--;
        }
    }

    private BlockFace stepFace(int hookAX, int hookAZ, int hookBX, int hookBZ) {
        if (hookAX != hookBX) {
            return hookBX > hookAX ? BlockFace.EAST : BlockFace.WEST;
        }
        return hookBZ > hookAZ ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private static final class HookSearch {
        private final boolean found;
        private final int hookX;
        private final int hookY;
        private final int hookZ;
        private final int distance;

        private HookSearch(boolean found, int hookX, int hookY, int hookZ, int distance) {
            this.found = found;
            this.hookX = hookX;
            this.hookY = hookY;
            this.hookZ = hookZ;
            this.distance = distance;
        }

        static HookSearch found(Block hook, int distance) {
            return new HookSearch(true, hook.getX(), hook.getY(), hook.getZ(), distance);
        }

        static HookSearch notFound() {
            return new HookSearch(false, 0, 0, 0, -1);
        }
    }

    private record TripwireLine(World world, LineKey key) {
        static TripwireLine of(
                World world,
                Axis axis,
                int hookAX,
                int hookAY,
                int hookAZ,
                int hookBX,
                int hookBY,
                int hookBZ,
                int distance
        ) {
            return new TripwireLine(world, LineKey.of(axis, hookAX, hookAY, hookAZ, hookBX, hookBY, hookBZ, distance));
        }
    }

    private record LineKey(
            Axis axis,
            int hookAX,
            int hookAY,
            int hookAZ,
            int hookBX,
            int hookBY,
            int hookBZ,
            int distance
    ) {
        static LineKey of(
                Axis axis,
                int hookAX,
                int hookAY,
                int hookAZ,
                int hookBX,
                int hookBY,
                int hookBZ,
                int distance
        ) {
            if (axis == Axis.X) {
                if (hookAX <= hookBX) {
                    return new LineKey(axis, hookAX, hookAY, hookAZ, hookBX, hookBY, hookBZ, distance);
                }
                return new LineKey(axis, hookBX, hookBY, hookBZ, hookAX, hookAY, hookAZ, distance);
            }

            if (hookAZ <= hookBZ) {
                return new LineKey(axis, hookAX, hookAY, hookAZ, hookBX, hookBY, hookBZ, distance);
            }
            return new LineKey(axis, hookBX, hookBY, hookBZ, hookAX, hookAY, hookAZ, distance);
        }

        boolean containsBlock(int x, int y, int z) {
            if (y != hookAY) {
                return false;
            }
            if (axis == Axis.X) {
                return z == hookAZ && x >= hookAX && x <= hookBX;
            }
            return x == hookAX && z >= hookAZ && z <= hookBZ;
        }

        boolean intersectsChunk(int chunkX, int chunkZ) {
            int minChunkX = Math.min(hookAX, hookBX) >> 4;
            int maxChunkX = Math.max(hookAX, hookBX) >> 4;
            int minChunkZ = Math.min(hookAZ, hookBZ) >> 4;
            int maxChunkZ = Math.max(hookAZ, hookBZ) >> 4;
            return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }

        LineKey {
            Objects.requireNonNull(axis, "axis");
        }
    }
}
