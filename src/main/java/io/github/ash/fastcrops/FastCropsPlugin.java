package io.github.ash.fastcrops;

import io.github.ash.fastcrops.command.FastCropsCommand;
import io.github.ash.fastcrops.config.FastCropsConfig;
import io.github.ash.fastcrops.growth.GrowthEngine;
import io.github.ash.fastcrops.tracking.GrowableTracker;
import io.github.ash.fastcrops.tripwire.TripwireExtensionService;
import io.github.ash.fastcrops.update.AutoUpdater;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FastCropsPlugin extends JavaPlugin {
    private FastCropsConfig fastCropsConfig;
    private GrowableTracker growableTracker;
    private GrowthEngine growthEngine;
    private TripwireExtensionService tripwireExtensionService;
    private AutoUpdater autoUpdater;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.fastCropsConfig = new FastCropsConfig(this);
        this.fastCropsConfig.reload();

        this.growableTracker = new GrowableTracker(this, fastCropsConfig);
        getServer().getPluginManager().registerEvents(growableTracker, this);
        this.growableTracker.rebuildFromLoadedChunks();

        this.tripwireExtensionService = new TripwireExtensionService(this, fastCropsConfig);
        getServer().getPluginManager().registerEvents(tripwireExtensionService, this);

        this.growthEngine = new GrowthEngine(this, fastCropsConfig, growableTracker);
        this.growthEngine.start();
        this.autoUpdater = new AutoUpdater(this, fastCropsConfig);
        this.autoUpdater.checkAndUpdateAsync();

        registerCommands();
        getLogger().info("Command hint: use /fastcrops or /fcrops. If there is a command conflict, use /fastcrops:fastcrops.");
        getLogger().info("FastCrops enabled.");
    }

    @Override
    public void onDisable() {
        if (growthEngine != null) {
            growthEngine.stop();
        }
        if (growableTracker != null) {
            growableTracker.clear();
        }
        getLogger().info("FastCrops disabled.");
    }

    public void reloadPluginState() {
        reloadConfig();
        fastCropsConfig.reload();
        growableTracker.rebuildFromLoadedChunks();
        growthEngine.restart();
    }

    public FastCropsConfig getFastCropsConfig() {
        return fastCropsConfig;
    }

    public GrowableTracker getGrowableTracker() {
        return growableTracker;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("fastcrops");
        if (command == null) {
            getLogger().severe("Command 'fastcrops' is missing from plugin.yml.");
            return;
        }

        FastCropsCommand handler = new FastCropsCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }
}
