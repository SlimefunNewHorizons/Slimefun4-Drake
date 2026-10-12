package com.github.drakescraft_labs.slimefun4.core.commands.subcommands;

import javax.annotation.Nonnull;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.core.commands.SlimefunCommand;
import com.github.drakescraft_labs.slimefun4.core.commands.SubCommand;
import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;
import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;
import com.github.drakescraft_labs.slimefun4.legacy.api.inventory.BlockMenu;

/** Repairs the active runtime state of persisted Slimefun blocks in the sender's current chunk. */
class RepairCommand extends SubCommand {

    RepairCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "repair", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return;
        }
        if (!player.hasPermission("slimefun.command.repair") && !player.isOp()) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }
        if (args.length != 2 || !args[1].equalsIgnoreCase("chunk")) {
            sender.sendMessage("Usage: /sf repair chunk");
            return;
        }

        RepairSummary summary = repairChunk(player.getLocation().getChunk());
        sender.sendMessage("§aSlimefun chunk repair: " + summary.validEntries + " valid, "
                + summary.tickersEnabled + " ticker(s) re-enabled, " + summary.inventoriesMarked
                + " inventories marked for saving.");
        if (summary.invalidEntries > 0 || summary.staleBlocks > 0) {
            sender.sendMessage("§eDiagnosis (no changes made): " + summary.invalidEntries + " unknown ID(s), "
                    + summary.staleBlocks + " record(s) with a missing block.");
        }
    }

    /** Re-enables safe runtime services only; unknown or stale records remain untouched for recovery. */
    private RepairSummary repairChunk(@Nonnull Chunk chunk) {
        RepairSummary summary = new RepairSummary();
        for (Location location : BlockStorage.getLocations(chunk)) {
            if (location.getBlock().getType().isAir()) {
                summary.staleBlocks++;
                continue;
            }

            SlimefunItem item = BlockStorage.check(location);
            if (item == null) {
                summary.invalidEntries++;
                continue;
            }

            summary.validEntries++;
            if (item.isTicking() && !item.isDisabledIn(chunk.getWorld())) {
                Slimefun.getTickerTask().enableTicker(location);
                summary.tickersEnabled++;
            }

            BlockMenu menu = BlockStorage.getInventory(location);
            if (menu != null) {
                menu.markDirty();
                summary.inventoriesMarked++;
            }
        }
        return summary;
    }

    private static final class RepairSummary {
        private int validEntries;
        private int tickersEnabled;
        private int inventoriesMarked;
        private int invalidEntries;
        private int staleBlocks;
    }
}
