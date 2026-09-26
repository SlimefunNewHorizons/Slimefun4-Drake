package com.github.drakescraft_labs.slimefun4.implementation.listeners;

import java.util.Set;
import javax.annotation.Nonnull;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.implementation.items.cargo.CargoConnectorNode;
import com.github.drakescraft_labs.slimefun4.implementation.items.cargo.CargoNode;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;

/**
 * This {@link Listener} is responsible for validating Cargo Node placement
 * and enforcing chunk limits to prevent server thread saturation.
 * 
 * @author TheBusyBiscuit
 * @author DrakesCraft-Labs
 *
 */
public class CargoNodeListener implements Listener {

    public CargoNodeListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCargoNodePlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        if (sfItem instanceof CargoNode || sfItem instanceof CargoConnectorNode) {
            Block b = e.getBlock();

            // Placement validation: Must be placed against the side of a block horizontally
            if (sfItem instanceof CargoNode) {
                if (b.getY() != e.getBlockAgainst().getY() || !e.getBlockReplacedState().getType().isAir()) {
                    Slimefun.getLocalization().sendMessage(e.getPlayer(), "machines.CARGO_NODES.must-be-placed", true);
                    e.setCancelled(true);
                    return;
                }
            }

            // Chunk limiter: Enforce per-chunk maximum cargo nodes to protect tick thread
            int maxPerChunk = Slimefun.getCfg().contains("networks.cargo-max-nodes-per-chunk")
                    ? Slimefun.getCfg().getInt("networks.cargo-max-nodes-per-chunk")
                    : 32;
            if (maxPerChunk > 0) {
                int count = countCargoNodesInChunk(b.getChunk());
                if (count >= maxPerChunk) {
                    e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&c[Cargo] Has alcanzado el límite máximo de nodos de Cargo en este chunk ("
                            + count + "/" + maxPerChunk + ")."));
                    e.setCancelled(true);
                }
            }
        }
    }

    public static int countCargoNodesInChunk(@Nonnull Chunk chunk) {
        Set<Location> locations = BlockStorage.getLocations(chunk);
        if (locations == null || locations.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Location loc : locations) {
            String id = BlockStorage.checkID(loc);
            if (id != null) {
                if (id.startsWith("CARGO_NODE") || id.startsWith("CARGO_CONNECTOR_NODE")) {
                    count++;
                    continue;
                }
                SlimefunItem item = SlimefunItem.getById(id);
                if (item instanceof CargoNode || item instanceof CargoConnectorNode) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean isCargoNode(@Nonnull ItemStack item) {
        return SlimefunItem.getByItem(item) instanceof CargoNode;
    }
}
