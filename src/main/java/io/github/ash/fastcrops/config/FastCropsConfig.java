package io.github.ash.fastcrops.config;

import io.github.ash.fastcrops.FastCropsPlugin;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class FastCropsConfig {
    static final int DEFAULT_TRIPWIRE_MAX_LENGTH = 69;
    static final int MIN_TRIPWIRE_MAX_LENGTH = 40;
    static final int MAX_TRIPWIRE_MAX_LENGTH = 256;

    private final FastCropsPlugin plugin;

    private boolean enabled;
    private int vanillaRandomTickSpeed;
    private double defaultTargetTickSpeed;
    private int intervalTicks;
    private int maxBlocksPerTickPerWorld;
    private int tripwireMaxLength;
    private boolean includeCrops;
    private boolean includeSaplings;
    private boolean includeBamboo;
    private boolean includeSugarCane;
    private boolean includeCactus;
    private boolean debug;
    private boolean autoUpdateEnabled;
    private boolean autoUpdateCheckOnStartup;
    private boolean autoUpdateDownloadOnUpdate;
    private String autoUpdateRepositoryOwner;
    private String autoUpdateRepositoryName;
    private String autoUpdateChannel;

    private Map<String, WorldConfig> worldConfigs = Collections.emptyMap();

    public FastCropsConfig(FastCropsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("enabled", true);
        this.vanillaRandomTickSpeed = Math.max(1, plugin.getConfig().getInt("vanillaRandomTickSpeed", 3));
        this.defaultTargetTickSpeed = Math.max(0.0D, plugin.getConfig().getDouble("defaultTargetTickSpeed", 100.0D));
        this.intervalTicks = Math.max(1, plugin.getConfig().getInt("intervalTicks", 2));
        this.maxBlocksPerTickPerWorld = Math.max(1, plugin.getConfig().getInt("maxBlocksPerTickPerWorld", 2000));
        boolean hasTripwireMaxLength = plugin.getConfig().isInt("tripwire.maxLength");
        int rawTripwireMaxLength = plugin.getConfig().getInt("tripwire.maxLength", DEFAULT_TRIPWIRE_MAX_LENGTH);
        this.tripwireMaxLength = sanitizeTripwireMaxLength(hasTripwireMaxLength, rawTripwireMaxLength);

        this.includeCrops = plugin.getConfig().getBoolean("include.crops", true);
        this.includeSaplings = plugin.getConfig().getBoolean("include.saplings", true);
        this.includeBamboo = plugin.getConfig().getBoolean("include.bamboo", false);
        this.includeSugarCane = plugin.getConfig().getBoolean("include.sugarCane", false);
        this.includeCactus = plugin.getConfig().getBoolean("include.cactus", false);

        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.autoUpdateEnabled = plugin.getConfig().getBoolean("autoUpdate.enabled", true);
        this.autoUpdateCheckOnStartup = plugin.getConfig().getBoolean("autoUpdate.checkOnStartup", true);
        this.autoUpdateDownloadOnUpdate = plugin.getConfig().getBoolean("autoUpdate.downloadOnUpdate", true);
        this.autoUpdateRepositoryOwner = plugin.getConfig().getString("autoUpdate.repositoryOwner", "starboyash");
        this.autoUpdateRepositoryName = plugin.getConfig().getString("autoUpdate.repositoryName", "Fast-Crops");
        this.autoUpdateChannel = plugin.getConfig().getString("autoUpdate.channel", "latest");

        this.worldConfigs = parseWorldConfigs(plugin.getConfig().getConfigurationSection("worlds"));
    }

    private Map<String, WorldConfig> parseWorldConfigs(ConfigurationSection worldsSection) {
        if (worldsSection == null) {
            return Collections.emptyMap();
        }

        Map<String, WorldConfig> parsed = new HashMap<>();
        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection section = worldsSection.getConfigurationSection(worldName);
            if (section == null) {
                continue;
            }

            boolean worldEnabled = section.getBoolean("enabled", true);
            double targetTickSpeed = section.getDouble("targetTickSpeed", this.defaultTargetTickSpeed);
            if (targetTickSpeed < 0.0D) {
                targetTickSpeed = this.defaultTargetTickSpeed;
            }

            int maxBlocksPerTick = section.getInt("maxBlocksPerTick", this.maxBlocksPerTickPerWorld);
            if (maxBlocksPerTick <= 0) {
                maxBlocksPerTick = this.maxBlocksPerTickPerWorld;
            }

            parsed.put(worldName.toLowerCase(Locale.ROOT), new WorldConfig(worldEnabled, targetTickSpeed, maxBlocksPerTick));
        }

        return Collections.unmodifiableMap(parsed);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getVanillaRandomTickSpeed() {
        return vanillaRandomTickSpeed;
    }

    public double getDefaultTargetTickSpeed() {
        return defaultTargetTickSpeed;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    public int getMaxBlocksPerTickPerWorld() {
        return maxBlocksPerTickPerWorld;
    }

    public int getTripwireMaxLength() {
        return tripwireMaxLength;
    }

    public boolean isIncludeCrops() {
        return includeCrops;
    }

    public boolean isIncludeSaplings() {
        return includeSaplings;
    }

    public boolean isIncludeBamboo() {
        return includeBamboo;
    }

    public boolean isIncludeSugarCane() {
        return includeSugarCane;
    }

    public boolean isIncludeCactus() {
        return includeCactus;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }

    public boolean isAutoUpdateCheckOnStartup() {
        return autoUpdateCheckOnStartup;
    }

    public boolean isAutoUpdateDownloadOnUpdate() {
        return autoUpdateDownloadOnUpdate;
    }

    public String getAutoUpdateRepositoryOwner() {
        return autoUpdateRepositoryOwner;
    }

    public String getAutoUpdateRepositoryName() {
        return autoUpdateRepositoryName;
    }

    public String getAutoUpdateChannel() {
        return autoUpdateChannel;
    }

    public WorldConfig getWorldConfig(String worldName) {
        WorldConfig worldConfig = worldConfigs.get(worldName.toLowerCase(Locale.ROOT));
        if (worldConfig != null) {
            return worldConfig;
        }

        return new WorldConfig(true, defaultTargetTickSpeed, maxBlocksPerTickPerWorld);
    }

    public Map<String, WorldConfig> getWorldConfigs() {
        return worldConfigs;
    }

    public void setWorldTargetTickSpeed(String worldName, double targetTickSpeed) {
        double sanitized = Math.max(0.0D, targetTickSpeed);
        String path = "worlds." + worldName + ".targetTickSpeed";
        plugin.getConfig().set(path, sanitized);
        plugin.saveConfig();
    }

    public boolean toggleWorldEnabled(String worldName) {
        WorldConfig current = getWorldConfig(worldName);
        boolean next = !current.enabled();
        String path = "worlds." + worldName + ".enabled";
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        return next;
    }

    static int sanitizeTripwireMaxLength(boolean hasExplicitInteger, int configuredValue) {
        int resolvedValue = hasExplicitInteger ? configuredValue : DEFAULT_TRIPWIRE_MAX_LENGTH;
        if (resolvedValue < MIN_TRIPWIRE_MAX_LENGTH) {
            return MIN_TRIPWIRE_MAX_LENGTH;
        }
        return Math.min(resolvedValue, MAX_TRIPWIRE_MAX_LENGTH);
    }
}
