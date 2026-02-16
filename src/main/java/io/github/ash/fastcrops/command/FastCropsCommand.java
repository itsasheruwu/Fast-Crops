package io.github.ash.fastcrops.command;

import io.github.ash.fastcrops.FastCropsPlugin;
import io.github.ash.fastcrops.config.FastCropsConfig;
import io.github.ash.fastcrops.config.WorldConfig;
import io.github.ash.fastcrops.tracking.GrowableTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class FastCropsCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN_PERMISSION = "fastcrops.admin";

    private final FastCropsPlugin plugin;

    public FastCropsCommand(FastCropsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "status" -> handleStatus(sender);
            case "reload" -> {
                if (!hasAdmin(sender)) {
                    return true;
                }
                handleReload(sender);
            }
            case "set" -> {
                if (!hasAdmin(sender)) {
                    return true;
                }
                handleSet(sender, args, label);
            }
            case "toggle" -> {
                if (!hasAdmin(sender)) {
                    return true;
                }
                handleToggle(sender, args, label);
            }
            default -> sendUsage(sender, label);
        }

        return true;
    }

    private boolean hasAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage("You need '" + ADMIN_PERMISSION + "' for this subcommand.");
        return false;
    }

    private void handleStatus(CommandSender sender) {
        FastCropsConfig config = plugin.getFastCropsConfig();
        GrowableTracker tracker = plugin.getGrowableTracker();

        sender.sendMessage("FastCrops status:");
        sender.sendMessage("- enabled: " + config.isEnabled());
        sender.sendMessage("- vanillaRandomTickSpeed: " + config.getVanillaRandomTickSpeed());
        sender.sendMessage("- defaultTargetTickSpeed: " + config.getDefaultTargetTickSpeed());
        sender.sendMessage("- intervalTicks: " + config.getIntervalTicks());
        sender.sendMessage("- maxBlocksPerTickPerWorld: " + config.getMaxBlocksPerTickPerWorld());
        sender.sendMessage("- include: crops=" + config.isIncludeCrops()
                + ", saplings=" + config.isIncludeSaplings()
                + ", bamboo=" + config.isIncludeBamboo()
                + ", sugarCane=" + config.isIncludeSugarCane()
                + ", cactus=" + config.isIncludeCactus());

        Map<String, Integer> trackedCounts = tracker.getTrackedCountsByWorldName();
        sender.sendMessage("Per-world:");
        for (World world : Bukkit.getWorlds()) {
            WorldConfig worldConfig = config.getWorldConfig(world.getName());
            sender.sendMessage("- " + world.getName()
                    + ": enabled=" + worldConfig.enabled()
                    + ", targetTickSpeed=" + worldConfig.targetTickSpeed()
                    + ", maxBlocksPerTick=" + worldConfig.maxBlocksPerTick()
                    + ", tracked=" + trackedCounts.getOrDefault(world.getName(), 0));
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPluginState();
        sender.sendMessage("FastCrops reloaded.");
    }

    private void handleSet(CommandSender sender, String[] args, String label) {
        if (args.length != 4) {
            sender.sendMessage("Usage: /" + label + " set <world> targetTickSpeed <number>");
            return;
        }

        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("World not found or not loaded: " + worldName);
            return;
        }

        if (!"targetTickSpeed".equalsIgnoreCase(args[2])) {
            sender.sendMessage("Only 'targetTickSpeed' can be set via this command.");
            return;
        }

        double targetTickSpeed;
        try {
            targetTickSpeed = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Invalid number: " + args[3]);
            return;
        }

        if (targetTickSpeed < 0.0D) {
            sender.sendMessage("targetTickSpeed must be >= 0.");
            return;
        }

        plugin.getFastCropsConfig().setWorldTargetTickSpeed(world.getName(), targetTickSpeed);
        plugin.reloadPluginState();
        sender.sendMessage("Updated targetTickSpeed for world '" + world.getName() + "' to " + targetTickSpeed + ".");
    }

    private void handleToggle(CommandSender sender, String[] args, String label) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " toggle <world>");
            return;
        }

        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage("World not found or not loaded: " + worldName);
            return;
        }

        boolean next = plugin.getFastCropsConfig().toggleWorldEnabled(world.getName());
        plugin.reloadPluginState();

        if (!next) {
            plugin.getGrowableTracker().clearWorld(world);
        }

        sender.sendMessage("World '" + world.getName() + "' enabled=" + next + ".");
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("FastCrops commands:");
        sender.sendMessage("- /" + label + " status");
        sender.sendMessage("- /" + label + " reload");
        sender.sendMessage("- /" + label + " set <world> targetTickSpeed <number>");
        sender.sendMessage("- /" + label + " toggle <world>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(args[0], List.of("status", "reload", "set", "toggle"));
        }

        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 2 && ("set".equalsIgnoreCase(args[0]) || "toggle".equalsIgnoreCase(args[0]))) {
            List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
            return complete(args[1], worlds);
        }

        if (args.length == 3 && "set".equalsIgnoreCase(args[0])) {
            return complete(args[2], List.of("targetTickSpeed"));
        }

        if (args.length == 4 && "set".equalsIgnoreCase(args[0])) {
            return complete(args[3], List.of("100", "75", "50"));
        }

        return Collections.emptyList();
    }

    private List<String> complete(String token, List<String> candidates) {
        if (token == null || token.isEmpty()) {
            return new ArrayList<>(candidates);
        }

        String lowerToken = token.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lowerToken))
                .sorted()
                .collect(Collectors.toList());
    }
}
