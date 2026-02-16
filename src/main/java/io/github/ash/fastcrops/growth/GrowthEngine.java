package io.github.ash.fastcrops.growth;

import io.github.ash.fastcrops.FastCropsPlugin;
import io.github.ash.fastcrops.config.FastCropsConfig;
import io.github.ash.fastcrops.config.WorldConfig;
import io.github.ash.fastcrops.tracking.GrowableTracker;
import io.github.ash.fastcrops.tracking.TrackedWorldState;
import java.util.OptionalLong;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.bukkit.World;

public final class GrowthEngine {
    private final FastCropsPlugin plugin;
    private final FastCropsConfig config;
    private final GrowableTracker tracker;
    private final GrowthLogic growthLogic;
    private final RandomGenerator random = new Random();

    private int taskId = -1;

    public GrowthEngine(FastCropsPlugin plugin, FastCropsConfig config, GrowableTracker tracker) {
        this.plugin = plugin;
        this.config = config;
        this.tracker = tracker;
        this.growthLogic = new GrowthLogic(plugin, tracker);
    }

    public void start() {
        stop();
        int interval = config.getIntervalTicks();
        this.taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, interval, interval);
    }

    public void restart() {
        start();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        if (!config.isEnabled()) {
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            WorldConfig worldConfig = config.getWorldConfig(world.getName());
            if (!worldConfig.enabled()) {
                continue;
            }

            TrackedWorldState state = tracker.getTrackedState(world);
            if (state == null || state.size() == 0) {
                continue;
            }

            int budget = Math.min(worldConfig.maxBlocksPerTick(), state.size());
            for (int i = 0; i < budget; i++) {
                OptionalLong packedOptional = state.nextPosition();
                if (packedOptional.isEmpty()) {
                    break;
                }

                long packed = packedOptional.getAsLong();
                int x = TrackedWorldState.unpackX(packed);
                int y = TrackedWorldState.unpackY(packed);
                int z = TrackedWorldState.unpackZ(packed);

                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    tracker.removeIfTracked(world, x, y, z);
                    continue;
                }

                var block = world.getBlockAt(x, y, z);
                if (!tracker.isGrowableMaterial(block.getType())) {
                    tracker.removeIfTracked(world, x, y, z);
                    continue;
                }

                int attempts = GrowthMath.extraAttemptsPerRun(
                        worldConfig.targetTickSpeed(),
                        config.getVanillaRandomTickSpeed(),
                        config.getIntervalTicks(),
                        random
                );

                for (int attempt = 0; attempt < attempts; attempt++) {
                    growthLogic.attemptGrowth(block, random);
                    if (!tracker.isGrowableMaterial(block.getType())) {
                        tracker.removeIfTracked(world, x, y, z);
                        break;
                    }
                }
            }
        }
    }
}
