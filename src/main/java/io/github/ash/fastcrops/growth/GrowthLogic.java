package io.github.ash.fastcrops.growth;

import io.github.ash.fastcrops.FastCropsPlugin;
import io.github.ash.fastcrops.tracking.GrowableTracker;
import io.github.ash.fastcrops.tracking.TrackedBlockType;
import java.util.EnumSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.event.block.BlockGrowEvent;

public final class GrowthLogic {
    private static final Set<Material> FARMLAND_CROPS = EnumSet.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.MELON_STEM,
            Material.PUMPKIN_STEM,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP
    );

    private static final Set<Material> FRUIT_GROUNDS = EnumSet.of(
            Material.FARMLAND,
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.ROOTED_DIRT,
            Material.MOSS_BLOCK
    );

    private static final Set<Material> SAPLING_SOILS = EnumSet.of(
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.MOSS_BLOCK,
            Material.MUD,
            Material.MYCELIUM,
            Material.FARMLAND
    );

    private static final Set<Material> SUGAR_CANE_BASE = EnumSet.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.SAND,
            Material.RED_SAND,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.MUD,
            Material.MOSS_BLOCK
    );

    private static final Set<BlockFace> HORIZONTAL_FACES = Set.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.WEST,
            BlockFace.EAST
    );

    private final FastCropsPlugin plugin;
    private final GrowableTracker tracker;

    public GrowthLogic(FastCropsPlugin plugin, GrowableTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    public void attemptGrowth(Block block, RandomGenerator random) {
        Material material = block.getType();
        TrackedBlockType type = tracker.classifyMaterial(material);
        if (type == TrackedBlockType.NONE) {
            return;
        }

        switch (type) {
            case CROP -> attemptCropGrowth(block, material, random);
            case SAPLING -> attemptSaplingGrowth(block, random);
            case BAMBOO -> attemptBambooGrowth(block, random);
            case SUGAR_CANE -> attemptSugarCaneGrowth(block, random);
            case CACTUS -> attemptCactusGrowth(block, random);
            default -> {
            }
        }
    }

    private void attemptCropGrowth(Block block, Material material, RandomGenerator random) {
        if (material == Material.ATTACHED_MELON_STEM || material == Material.ATTACHED_PUMPKIN_STEM) {
            return;
        }

        if (material == Material.MELON_STEM || material == Material.PUMPKIN_STEM) {
            attemptStemGrowth(block, material, random);
            return;
        }

        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return;
        }

        if (!canGrowAgeableCrop(block, material, blockData)) {
            return;
        }

        if (ageable.getAge() >= ageable.getMaximumAge()) {
            return;
        }

        if (random.nextDouble() > growthChanceFor(material)) {
            return;
        }

        Ageable next = (Ageable) ageable.clone();
        next.setAge(Math.min(next.getMaximumAge(), ageable.getAge() + 1));
        applyBlockGrowth(block, next);
    }

    private boolean canGrowAgeableCrop(Block block, Material material, BlockData blockData) {
        if (material == Material.NETHER_WART) {
            return block.getRelative(BlockFace.DOWN).getType() == Material.SOUL_SAND;
        }

        if (material == Material.COCOA && blockData instanceof Cocoa cocoa) {
            Block attachedTo = block.getRelative(cocoa.getFacing().getOppositeFace());
            return attachedTo.getType() == Material.JUNGLE_LOG;
        }

        if (material == Material.SWEET_BERRY_BUSH) {
            return block.getLightLevel() >= 9;
        }

        if (FARMLAND_CROPS.contains(material)) {
            return block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND && block.getLightLevel() >= 9;
        }

        if (Tag.CROPS.isTagged(material)) {
            if (block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND) {
                return block.getLightLevel() >= 9;
            }
            return true;
        }

        return true;
    }

    private void attemptStemGrowth(Block block, Material stemMaterial, RandomGenerator random) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Ageable ageable)) {
            return;
        }

        if (!canGrowAgeableCrop(block, stemMaterial, blockData)) {
            return;
        }

        if (ageable.getAge() < ageable.getMaximumAge()) {
            if (random.nextDouble() <= 0.35D) {
                Ageable next = (Ageable) ageable.clone();
                next.setAge(ageable.getAge() + 1);
                applyBlockGrowth(block, next);
            }
            return;
        }

        if (random.nextDouble() > 0.2D) {
            return;
        }

        tryGrowStemFruit(block, stemMaterial, random);
    }

    private void tryGrowStemFruit(Block stem, Material stemMaterial, RandomGenerator random) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST};
        int start = random.nextInt(faces.length);

        Material fruitMaterial = stemMaterial == Material.MELON_STEM ? Material.MELON : Material.PUMPKIN;
        Material attachedMaterial = stemMaterial == Material.MELON_STEM ? Material.ATTACHED_MELON_STEM : Material.ATTACHED_PUMPKIN_STEM;

        for (int i = 0; i < faces.length; i++) {
            BlockFace direction = faces[(start + i) % faces.length];
            Block fruitBlock = stem.getRelative(direction);

            if (!fruitBlock.getType().isAir()) {
                continue;
            }

            Block below = fruitBlock.getRelative(BlockFace.DOWN);
            if (!FRUIT_GROUNDS.contains(below.getType())) {
                continue;
            }

            if (!applyBlockGrowthType(fruitBlock, fruitMaterial)) {
                continue;
            }

            BlockData attachedStemData = Bukkit.createBlockData(attachedMaterial);
            if (attachedStemData instanceof Directional directional) {
                directional.setFacing(direction);
            }
            applyBlockGrowth(stem, attachedStemData);
            return;
        }
    }

    private void attemptSaplingGrowth(Block block, RandomGenerator random) {
        if (!isValidSaplingEnvironment(block)) {
            return;
        }

        BlockData blockData = block.getBlockData();
        if (blockData instanceof Sapling sapling) {
            if (sapling.getStage() < sapling.getMaximumStage()) {
                if (random.nextDouble() <= 0.14D) {
                    Sapling next = (Sapling) sapling.clone();
                    next.setStage(sapling.getStage() + 1);
                    applyBlockGrowth(block, next);
                }
                return;
            }

            if (random.nextDouble() <= 0.14D) {
                block.applyBoneMeal(BlockFace.UP);
            }
            return;
        }

        if (blockData instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            if (random.nextDouble() <= 0.14D) {
                Ageable next = (Ageable) ageable.clone();
                next.setAge(ageable.getAge() + 1);
                applyBlockGrowth(block, next);
            }
            return;
        }

        if (random.nextDouble() <= 0.14D) {
            block.applyBoneMeal(BlockFace.UP);
        }
    }

    private boolean isValidSaplingEnvironment(Block block) {
        Material below = block.getRelative(BlockFace.DOWN).getType();
        if (!SAPLING_SOILS.contains(below)) {
            return false;
        }
        return block.getLightLevel() >= 9;
    }

    private void attemptBambooGrowth(Block block, RandomGenerator random) {
        if (block.getRelative(BlockFace.UP).getType() == Material.BAMBOO) {
            return;
        }

        Block target = block.getRelative(BlockFace.UP);
        if (!target.getType().isAir()) {
            return;
        }

        if (random.nextDouble() > 0.24D) {
            return;
        }

        int height = 1;
        Block cursor = block.getRelative(BlockFace.DOWN);
        while (cursor.getType() == Material.BAMBOO && height < 16) {
            height++;
            cursor = cursor.getRelative(BlockFace.DOWN);
        }

        if (height >= 16) {
            return;
        }

        applyBlockGrowthType(target, Material.BAMBOO);
    }

    private void attemptSugarCaneGrowth(Block block, RandomGenerator random) {
        if (block.getRelative(BlockFace.UP).getType() == Material.SUGAR_CANE) {
            return;
        }

        Block target = block.getRelative(BlockFace.UP);
        if (!target.getType().isAir()) {
            return;
        }

        int height = 1;
        Block cursor = block.getRelative(BlockFace.DOWN);
        while (cursor.getType() == Material.SUGAR_CANE && height < 3) {
            height++;
            cursor = cursor.getRelative(BlockFace.DOWN);
        }

        if (height >= 3 || !isValidSugarCaneBase(cursor)) {
            return;
        }

        if (random.nextDouble() <= 0.2D) {
            applyBlockGrowthType(target, Material.SUGAR_CANE);
        }
    }

    private boolean isValidSugarCaneBase(Block bottom) {
        Material support = bottom.getRelative(BlockFace.DOWN).getType();
        if (!SUGAR_CANE_BASE.contains(support)) {
            return false;
        }

        for (BlockFace face : HORIZONTAL_FACES) {
            Material adjacent = bottom.getRelative(face).getType();
            if (adjacent == Material.WATER || adjacent == Material.FROSTED_ICE) {
                return true;
            }
        }

        return false;
    }

    private void attemptCactusGrowth(Block block, RandomGenerator random) {
        if (block.getRelative(BlockFace.UP).getType() == Material.CACTUS) {
            return;
        }

        Block target = block.getRelative(BlockFace.UP);
        if (!target.getType().isAir()) {
            return;
        }

        int height = 1;
        Block cursor = block.getRelative(BlockFace.DOWN);
        while (cursor.getType() == Material.CACTUS && height < 3) {
            height++;
            cursor = cursor.getRelative(BlockFace.DOWN);
        }

        if (height >= 3 || !canPlaceCactus(target)) {
            return;
        }

        if (random.nextDouble() <= 0.2D) {
            applyBlockGrowthType(target, Material.CACTUS);
        }
    }

    private boolean canPlaceCactus(Block target) {
        Material below = target.getRelative(BlockFace.DOWN).getType();
        if (!(below == Material.CACTUS || below == Material.SAND || below == Material.RED_SAND)) {
            return false;
        }

        for (BlockFace face : HORIZONTAL_FACES) {
            if (!target.getRelative(face).getType().isAir()) {
                return false;
            }
        }

        return true;
    }

    private double growthChanceFor(Material material) {
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS -> 0.4D;
            case COCOA -> 0.25D;
            case NETHER_WART, SWEET_BERRY_BUSH -> 0.3D;
            case TORCHFLOWER_CROP, PITCHER_CROP -> 0.3D;
            default -> 0.35D;
        };
    }

    private boolean applyBlockGrowthType(Block block, Material targetType) {
        BlockState newState = block.getState();
        newState.setType(targetType);

        BlockGrowEvent event = new BlockGrowEvent(block, newState);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        return event.getNewState().update(true, false);
    }

    private boolean applyBlockGrowth(Block block, BlockData targetData) {
        BlockState newState = block.getState();
        if (targetData.getMaterial() != newState.getType()) {
            newState.setType(targetData.getMaterial());
        }
        newState.setBlockData(targetData);

        BlockGrowEvent event = new BlockGrowEvent(block, newState);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        return event.getNewState().update(true, false);
    }
}
