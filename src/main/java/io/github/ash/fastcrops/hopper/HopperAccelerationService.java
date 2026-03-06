package io.github.ash.fastcrops.hopper;

import io.github.ash.fastcrops.config.FastCropsConfig;
import io.github.ash.fastcrops.config.WorldConfig;
import io.github.ash.fastcrops.growth.GrowthMath;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HopperAccelerationService implements Listener {
    private final FastCropsConfig config;

    public HopperAccelerationService(FastCropsConfig config) {
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!isHopperAccelerationEnabled()) {
            return;
        }

        accelerateInventory(event.getInitiator());
        accelerateInventory(event.getSource());
        accelerateInventory(event.getDestination());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (!isHopperAccelerationEnabled()) {
            return;
        }

        accelerateInventory(event.getInventory());
    }

    private boolean isHopperAccelerationEnabled() {
        return config.isEnabled() && config.isIncludeHoppers();
    }

    private void accelerateInventory(Inventory inventory) {
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof Hopper hopper)) {
            return;
        }

        WorldConfig worldConfig = config.getWorldConfig(hopper.getWorld().getName());
        if (!worldConfig.enabled()) {
            return;
        }

        int targetCooldown = GrowthMath.hopperTransferCooldownTicks(
                worldConfig.targetTickSpeed(),
                config.getVanillaRandomTickSpeed()
        );

        if (hopper.getTransferCooldown() <= targetCooldown) {
            return;
        }

        hopper.setTransferCooldown(targetCooldown);
        hopper.update(true, false);
    }
}
