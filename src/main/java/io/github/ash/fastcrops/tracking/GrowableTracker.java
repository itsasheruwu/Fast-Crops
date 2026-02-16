package io.github.ash.fastcrops.tracking;

import io.github.ash.fastcrops.FastCropsPlugin;
import io.github.ash.fastcrops.config.FastCropsConfig;
import io.github.ash.fastcrops.config.WorldConfig;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class GrowableTracker implements Listener {
    private static final Set<Material> EXTRA_CROPS = Collections.unmodifiableSet(EnumSet.of(
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.MELON_STEM,
            Material.PUMPKIN_STEM,
            Material.ATTACHED_MELON_STEM,
            Material.ATTACHED_PUMPKIN_STEM
    ));

    private final FastCropsPlugin plugin;
    private final FastCropsConfig config;
    private final Map<UUID, TrackedWorldState> trackedByWorld = new HashMap<>();

    public GrowableTracker(FastCropsPlugin plugin, FastCropsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void rebuildFromLoadedChunks() {
        trackedByWorld.clear();
        if (!config.isEnabled()) {
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            WorldConfig worldConfig = config.getWorldConfig(world.getName());
            if (!worldConfig.enabled()) {
                continue;
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(world, chunk);
            }
        }
    }

    public void clear() {
        trackedByWorld.clear();
    }

    public void clearWorld(World world) {
        trackedByWorld.remove(world.getUID());
    }

    public TrackedWorldState getTrackedState(World world) {
        return trackedByWorld.get(world.getUID());
    }

    public Map<String, Integer> getTrackedCountsByWorldName() {
        Map<String, Integer> counts = new HashMap<>();
        for (World world : plugin.getServer().getWorlds()) {
            TrackedWorldState state = trackedByWorld.get(world.getUID());
            counts.put(world.getName(), state == null ? 0 : state.size());
        }
        return counts;
    }

    public int getTrackedCount(World world) {
        TrackedWorldState state = trackedByWorld.get(world.getUID());
        return state == null ? 0 : state.size();
    }

    public boolean removeIfTracked(World world, int x, int y, int z) {
        TrackedWorldState state = trackedByWorld.get(world.getUID());
        if (state == null) {
            return false;
        }
        return state.remove(x, y, z);
    }

    public TrackedBlockType classifyMaterial(Material material) {
        if (material == Material.AIR) {
            return TrackedBlockType.NONE;
        }

        if (config.isIncludeBamboo() && material == Material.BAMBOO) {
            return TrackedBlockType.BAMBOO;
        }

        if (config.isIncludeSugarCane() && material == Material.SUGAR_CANE) {
            return TrackedBlockType.SUGAR_CANE;
        }

        if (config.isIncludeCactus() && material == Material.CACTUS) {
            return TrackedBlockType.CACTUS;
        }

        if (config.isIncludeSaplings() && (Tag.SAPLINGS.isTagged(material) || material == Material.MANGROVE_PROPAGULE)) {
            return TrackedBlockType.SAPLING;
        }

        if (config.isIncludeCrops() && (Tag.CROPS.isTagged(material) || EXTRA_CROPS.contains(material))) {
            return TrackedBlockType.CROP;
        }

        return TrackedBlockType.NONE;
    }

    public boolean isGrowableMaterial(Material material) {
        return classifyMaterial(material) != TrackedBlockType.NONE;
    }

    private boolean isWorldTrackingEnabled(World world) {
        if (!config.isEnabled()) {
            return false;
        }

        WorldConfig worldConfig = config.getWorldConfig(world.getName());
        return worldConfig.enabled();
    }

    private void updateTrackedBlock(Block block, Material newType) {
        World world = block.getWorld();
        if (!isWorldTrackingEnabled(world)) {
            removeIfTracked(world, block.getX(), block.getY(), block.getZ());
            return;
        }

        if (isGrowableMaterial(newType)) {
            trackedByWorld
                    .computeIfAbsent(world.getUID(), ignored -> new TrackedWorldState())
                    .add(block.getX(), block.getY(), block.getZ());
        } else {
            removeIfTracked(world, block.getX(), block.getY(), block.getZ());
        }
    }

    private void scanChunk(World world, Chunk chunk) {
        if (!isWorldTrackingEnabled(world)) {
            return;
        }

        TrackedWorldState state = trackedByWorld.computeIfAbsent(world.getUID(), ignored -> new TrackedWorldState());
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(localX, y, localZ);
                    if (isGrowableMaterial(block.getType())) {
                        state.add(block.getX(), y, block.getZ());
                    }
                }
            }
        }

        if (config.isDebug()) {
            plugin.getLogger().info("[FastCrops] Scanned chunk " + chunk.getX() + "," + chunk.getZ()
                    + " in world " + world.getName().toLowerCase(Locale.ROOT));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        scanChunk(event.getWorld(), event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        TrackedWorldState state = trackedByWorld.get(event.getWorld().getUID());
        if (state != null) {
            state.removeChunk(event.getChunk().getX(), event.getChunk().getZ());
            if (state.size() == 0) {
                trackedByWorld.remove(event.getWorld().getUID());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        updateTrackedBlock(event.getBlockPlaced(), event.getBlockPlaced().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        removeIfTracked(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        removeIfTracked(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        updateTrackedBlock(event.getBlock(), event.getNewState().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        updateTrackedBlock(event.getBlock(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        updateTrackedBlock(event.getBlock(), event.getNewState().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        updateTrackedBlock(event.getBlock(), event.getNewState().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        updateTrackedBlock(event.getBlock(), event.getNewState().getType());
    }
}
