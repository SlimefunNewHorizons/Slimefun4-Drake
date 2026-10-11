package com.github.drakescraft_labs.slimefun4.implementation.listeners;

import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.github.drakescraft_labs.slimefun4.implementation.Slimefun;

import com.github.drakescraft_labs.slimefun4.legacy.api.BlockStorage;

public class WorldListener implements Listener {

    public WorldListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        Slimefun.getWorldSettingsService().load(e.getWorld());
        BlockStorage.getOrCreate(e.getWorld());
    }

    // MONITOR + ignoreCancelled: if another plugin cancels the unload, the world stays loaded and so must its data
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent e) {
        BlockStorage storage = BlockStorage.getStorage(e.getWorld());

        if (storage != null) {
            // Block files are written in the background so the main thread never waits on disk I/O here
            storage.saveAndRemoveAsync();
        } else {
            Slimefun.logger().log(Level.SEVERE, "Could not save Slimefun Blocks for World \"{0}\"", e.getWorld().getName());
        }
    }

}
